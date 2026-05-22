# JWT 검증 사양 — AI 서버 전달용 핸드오버

## 0. 메타

| 항목 | 값 |
|---|---|
| 변경 이력 ID | `2026-05-22-jwt-ai-handover` |
| 대상 | AI 서버 (FastAPI) 담당자 |
| 발신 | API 서버 (Spring Boot 4 / jjwt 0.12.3) 담당자 |
| 관련 | `deploy-status.md §C3` (JWT_SECRET 동일값 공유, 옵션 a 채택) |
| 작성일 | 2026-05-22 |
| 상태 | 전달 대기 (secret 별도 채널) |

---

## 1. 왜 공유하는가

API 서버가 발급한 JWT 를 AI 서버가 **동일 secret 으로 HMAC 검증** 해야 함.
사용자 흐름:

```
[Client] ──login──> [API 서버] ──issue JWT──> [Client]
                                                  │
                                                  └──Bearer──> [AI 서버] (chat/recommend 등)
                                                                  │
                                                                  ▼
                                              JWT_SECRET 으로 HS512 검증 → user_id 추출
```

→ 양 서버가 같은 secret 을 보유해야 함. **별도 토큰 발급 X — API 서버가 단일 발급자.**

---

## 2. 알고리즘

| 항목 | 값 |
|---|---|
| **알고리즘** | `HS512` (HMAC-SHA512) |
| **결정 이유** | jjwt 0.12.3 의 `Keys.hmacShaKeyFor(secret.getBytes(UTF_8))` 가 키 길이로 자동 결정. 64 chars = 512 bits → HS512 자동 선택 |
| **만료** | 24 시간 (`exp = iat + 86400 sec`) |
| **refresh token** | 없음. 만료 시 재로그인 |
| **issuer / audience claims** | 미사용 (`iss`/`aud` 모두 비어있음) |

---

## 3. Secret Key

| 항목 | 값 |
|---|---|
| **값** | ⚠️ git 미공개. 별도 채널로 전달 (§7 참조) |
| **인코딩** | **UTF-8 raw bytes** (base64 디코딩 ❌). 받은 문자열을 그대로 `.encode("utf-8")` |
| **길이** | 64 chars / 64 bytes / 512 bits |
| **출처** | API 서버 AWS Secrets Manager `naengo/prod/jwt` |
| **검증** | `len(secret) == 64` 이고 ASCII 영숫자만 포함 |

---

## 4. Token Payload

```json
{
  "sub": "4",
  "role": "USER",
  "iat": 1747804800,
  "exp": 1747891200
}
```

| 클레임 | 타입 | 의미 |
|---|---|---|
| `sub` | String | `user_id` 를 문자열화한 값. **AI 측에서 `int(payload["sub"])` 변환 필요** (실제 user_id 는 Integer — DBv5 SERIAL PK) |
| `role` | String | `"USER"` \| `"ADMIN"` |
| `iat` | int (epoch sec) | 발급 시각 |
| `exp` | int (epoch sec) | 만료 시각 = `iat + 86400` |

> 추가 클레임 미예정. 향후 확장 시 본 문서 후속 PR 로 통지.

---

## 5. Python 검증 예제 (FastAPI + PyJWT)

### 5.1 의존성

```bash
pip install pyjwt
```

### 5.2 검증 dependency

```python
# auth.py
import os
import jwt
from fastapi import HTTPException, Header, status

JWT_SECRET = os.environ["JWT_SECRET"].encode("utf-8")
JWT_ALG = "HS512"

def get_current_user_id(authorization: str = Header(...)) -> int:
    if not authorization.startswith("Bearer "):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Missing Bearer token",
        )
    token = authorization[len("Bearer "):]
    try:
        payload = jwt.decode(
            token,
            JWT_SECRET,
            algorithms=[JWT_ALG],
            options={"require": ["exp", "sub"]},
        )
        return int(payload["sub"])
    except jwt.ExpiredSignatureError:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Token expired",
        )
    except (jwt.InvalidTokenError, KeyError, ValueError):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid token",
        )
```

### 5.3 endpoint 사용 예

```python
# main.py
from fastapi import FastAPI, Depends
from auth import get_current_user_id

app = FastAPI()

@app.post("/api/v1/chat")
def chat(user_input: str, user_id: int = Depends(get_current_user_id)):
    # user_id 는 검증 통과한 사용자의 INTEGER PK
    return {"user_id": user_id, "echo": user_input}
```

### 5.4 role 기반 분기 (필요 시)

```python
def require_admin(authorization: str = Header(...)) -> int:
    user_id = get_current_user_id(authorization)
    # role 도 같이 추출하려면 get_current_user_id 변형:
    payload = jwt.decode(authorization[7:], JWT_SECRET, algorithms=[JWT_ALG])
    if payload.get("role") != "ADMIN":
        raise HTTPException(status_code=403, detail="Admin only")
    return user_id
```

---

## 6. Cross-team smoke test 절차

배포 후 양 서버가 호환되는지 1회 확인:

1. AI 서버 환경변수 `JWT_SECRET` 에 §3 값 주입 (k8s secret / docker env / .env — 운영 채널에 맞춰)
2. AI 서비스 재시작
3. API 서버에서 테스트 사용자 로그인:
   ```bash
   curl -X POST https://<API_BASE>/api/v1/auth/login \
        -H "Content-Type: application/json" \
        -d '{"username":"<id>","password":"<pw>"}'
   ```
   응답의 `access_token` 복사
4. AI endpoint 호출:
   ```bash
   curl -X POST https://<AI_BASE>/api/v1/chat \
        -H "Authorization: Bearer <위 access_token>" \
        -H "Content-Type: application/json" \
        -d '{"user_input":"test"}'
   ```
5. **기대**: 200 + AI 응답. user_id 가 로그에 찍히면 검증 OK.

   **실패 시 점검:**
   - `Invalid token` → secret 불일치 가능성 (encoding 확인: utf-8 raw bytes)
   - `Token expired` → 토큰 발급 24h 경과
   - 검증은 통과하지만 user_id 못 읽음 → `int(payload["sub"])` 누락

---

## 7. Secret 전달 절차 + 보안

### 7.1 전달 채널 (택 1)

| 채널 | 평가 |
|---|---|
| 1Password / Bitwarden 공유 vault | ✅ 권장 |
| Signal / Telegram secret chat | ✅ E2E 암호화 |
| 직접 만나서 화면으로 보여주기 + 받는 쪽 즉시 입력 | ✅ |
| Slack / Discord / KakaoTalk 일반 메시지 | ❌ 히스토리 영구 + 검색됨 |
| Email | ❌ |
| GitHub Issue / PR / commit 메시지 | ❌ |

### 7.2 유출 의심 시 rotation 절차

1. 새 secret 생성 (`openssl rand -base64 48`, 결과를 적당히 잘라 64 chars)
2. **양 서버 동시 갱신** (시간차 두면 그 사이 토큰 호환 깨짐):
   - API 서버: `aws secretsmanager update-secret --secret-id naengo/prod/jwt --secret-string '{"JWT_SECRET":"<new>"}'` + `aws ecs update-service --force-new-deployment ...`
   - AI 서버: 환경변수 갱신 + 재시작
3. 갱신 직후 모든 활성 토큰 무효화 → 사용자 재로그인 필요. 사전 공지 권장.

### 7.3 Rotation 권고 주기

| 상황 | 주기 |
|---|---|
| 일상 운영 | 분기 1회 (3개월) |
| 인원 이탈 (secret 접근권자 변경) | 즉시 |
| 의심 / 누설 가능성 | 즉시 |

---

## 8. 참조

- API 서버 코드: `src/main/java/com/naengo/api_server/global/auth/JwtTokenProvider.java`
- jjwt 라이브러리: `io.jsonwebtoken:jjwt-api:0.12.3` (HS-시리즈 자동 알고리즘 선택 동작 근거)
- 발급 endpoint: `POST /api/v1/auth/login`, `POST /api/v1/auth/signup`, `POST /api/v1/auth/social/kakao`
- Secrets Manager ARN: `arn:aws:secretsmanager:ap-northeast-2:518056141724:secret:naengo/prod/jwt-9m02fH`
