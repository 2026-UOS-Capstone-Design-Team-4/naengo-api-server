# 카카오 OAuth 운영 키 설정 + 브라우저 e2e 런북

> 대상: 배포/운영 담당자 (`user-domain-todo.md §4-1·§4-6` 의 실행 가이드)
> 목적: 카카오 개발자 콘솔 등록 → env 주입 → **로컬 브라우저 e2e 1회** → 운영 검증
> 코드 변경 불요. 본 문서는 "무엇을 누르고 무엇을 넣는가" 순서다.
> 코드 근거: `KakaoTokenClient`, `KakaoOAuthClient`, `DevOAuthController`(`@Profile("local")`), `application(-local/-prod).yml`.

---

## 0. 두 가지 흐름 (먼저 이해)

| 흐름 | 누가 씀 | 경로 | 카카오 키 |
|---|---|---|---|
| **A. 로컬 브라우저 e2e (검증용)** | 개발자 (사람) | `GET /oauth/kakao/authorize` → 카카오 로그인 → `GET /oauth/kakao/test-callback?code=...` | `KAKAO_REST_API_KEY` 로 **code→token 교환** (`KakaoTokenClient`) |
| **B. 운영 실제 흐름** | front/모바일 (카카오 SDK) | 클라이언트가 카카오 SDK 로 access token 획득 → `POST /api/v1/auth/social/kakao` `{"access_token":"..."}` | 서버는 **token 교환 안 함**. `KakaoOAuthClient` 가 `kapi.kakao.com/v2/user/me` 로 토큰 검증만 |

- **A** 는 `@Profile("local")` 전용 (운영에 endpoint 없음). "브라우저 e2e 1회 검증"이 바로 이 A.
- **B** 가 실서비스 경로. 서버 쪽은 REST 키로 토큰 교환을 하지 않고, 클라이언트가 준 access token 을 카카오 API 로 검증만 함.
- ⚠️ 단, **운영 부팅 시 `KAKAO_REST_API_KEY`·`KAKAO_REDIRECT_URI` env 가 없으면 서버가 기동 실패** (`application-prod.yml` 이 default 없이 placeholder 로 매핑 → `KakaoTokenClient` `@Value` 미해결). 그래서 운영에도 두 env 는 **반드시 주입**해야 한다 (값이 실제로 토큰 교환에 안 쓰여도 기동 전제).

---

## 1. 카카오 개발자 콘솔 설정 (https://developers.kakao.com)

1. **애플리케이션 생성**: 내 애플리케이션 → 애플리케이션 추가하기 (앱 이름/사업자명 입력).
2. **REST API 키 확보**: 앱 설정 → 앱 키 → **REST API 키** 복사. → 이 값이 `KAKAO_REST_API_KEY`.
   - (모바일/JS SDK 는 클라이언트가 Native/JavaScript 키를 따로 씀 — 서버 env 와 무관, 클라 담당.)
3. **카카오 로그인 활성화**: 제품 설정 → 카카오 로그인 → **활성화 ON**.
4. **Client Secret = 사용 안 함**: 카카오 로그인 → 보안 → Client Secret → **"사용 안 함"** 으로 둔다.
   - 근거: `KakaoTokenClient.exchangeCodeForToken` 의 폼 바디는 `grant_type/client_id/redirect_uri/code` 만 보냄 — **`client_secret` 미포함**. Client Secret 을 "사용함"으로 켜면 토큰 교환이 실패한다.
5. **Redirect URI 등록**: 카카오 로그인 → Redirect URI → 등록.
   - 로컬 e2e: `http://localhost:8080/oauth/kakao/test-callback` (= `KAKAO_REDIRECT_URI` 기본값과 **정확히 일치**해야 함. scheme/host/port/path 한 글자도 다르면 KOE006).
   - 운영(흐름 B 만 쓰면 dev 콜백 불필요)에서도 부팅 전제로 `KAKAO_REDIRECT_URI` 는 채워야 하므로, 운영용으로도 형식상 1개 등록(예: `https://<운영도메인>/oauth/kakao/test-callback`)하거나 로컬과 동일 값을 그대로 둔다(운영에선 호출 안 함).
6. **동의 항목**: 카카오 로그인 → 동의 항목.
   - **닉네임/프로필**: 선택 동의 무방 (서버는 닉네임을 `kakao_<랜덤8자>` 로 자체 생성하므로 필수 아님).
   - **카카오계정(이메일)**: 선택 동의 권장. 미동의/미수집이어도 서버는 placeholder `kakao_<id>@social.naengo.com` 로 가입 처리 (`KakaoOAuthClient.resolveEmail`). 즉 이메일 없어도 가입은 됨.
7. **플랫폼 등록**: 앱 설정 → 플랫폼.
   - 로컬 브라우저 e2e: Web 플랫폼에 `http://localhost:8080` 등록.
   - 모바일(흐름 B): Android/iOS 플랫폼에 패키지명/번들ID/키해시 등록 (모바일 담당).

---

## 2. 환경변수 주입

| env | local 기본값 | 운영(prod) | 비고 |
|---|---|---|---|
| `KAKAO_REST_API_KEY` | 빈 값 (실테스트 시 **반드시 주입**) | **필수** (없으면 부팅 실패) | 콘솔 REST API 키 |
| `KAKAO_REDIRECT_URI` | `http://localhost:8080/oauth/kakao/test-callback` | **필수** (없으면 부팅 실패) | 콘솔 등록값과 **완전 일치** |
| `JWT_SECRET` | 로컬 기본값 있음 | **필수** (32자+) | 발급 JWT 서명 |
| `DB_URL/USERNAME/PASSWORD` | 로컬 기본값 있음 | **필수** | 공유 DB |
| `CORS_ALLOWED_ORIGINS` | localhost 기본 | **필수** | front 도메인 |
| `AUTH_COOKIE_SECURE` | false | prod yml 에서 `true` 고정 | **운영은 HTTPS 필수** (Secure 쿠키) |

로컬 e2e 실행 예 (PowerShell):
```
$env:KAKAO_REST_API_KEY="<콘솔 REST API 키>"
# KAKAO_REDIRECT_URI 는 기본값 사용 시 생략 가능
./gradlew bootRun --args='--spring.profiles.active=local'
```

---

## 3. 로컬 브라우저 e2e 절차 (§4-6 핵심)

전제: 로컬 DB(docker-compose) 기동 + 위 env 로 `bootRun` (local 프로파일).

1. 브라우저에서 **`http://localhost:8080/oauth/kakao/authorize`** 접속.
   - 서버가 `https://kauth.kakao.com/oauth/authorize?client_id=<REST키>&redirect_uri=<redirect>&response_type=code` 로 리다이렉트.
2. 카카오 로그인 + 동의 진행.
3. 카카오가 `http://localhost:8080/oauth/kakao/test-callback?code=...` 로 콜백 → 서버가:
   - code → access token 교환 (`KakaoTokenClient`)
   - access token → 사용자정보 (`KakaoOAuthClient`, `kapi.kakao.com/v2/user/me`)
   - 신규면 `users` + `user_profiles`(빈) 생성, 자체 JWT 발급
   - **응답 JSON (snake_case)**:
     ```json
     { "user_id": 1, "nickname": "kakao_a1b2c3d4", "role": "USER", "access_token": "eyJ..." }
     ```
     + `Set-Cookie: NAENGO_AT=...`
4. **검증 체크리스트**:
   - [ ] 응답 200 + `access_token` 존재
   - [ ] DB: `SELECT user_id, provider, provider_id, email FROM users ORDER BY user_id DESC LIMIT 1;` → `provider='KAKAO'`, `provider_id` 채워짐, email 은 실제 또는 `kakao_<id>@social.naengo.com`
   - [ ] DB: `SELECT count(*) FROM user_profiles WHERE user_id=<위 user_id>;` → **1** (가입 시 자동 생성)
   - [ ] 발급 토큰으로 보호 endpoint:
     ```
     curl -H "Authorization: Bearer <access_token>" http://localhost:8080/api/v1/users/me
     ```
     → 200 + `{"user_id":...,"provider":"KAKAO",...}` (snake_case)
   - [ ] 같은 카카오 계정으로 2회차 `/oauth/kakao/authorize` → **동일 user_id** (재가입 안 됨)
   - [ ] (선택) 차단 시: `UPDATE users SET is_blocked=true WHERE user_id=<id>;` 후 재로그인 → 403 `USER_BLOCKED`

---

## 4. 운영(흐름 B) 검증

서버 단독으로는 카카오 access token 을 만들 수 없으므로, **클라이언트(front/모바일)가 발급한 실제 카카오 access token** 으로 검증한다.

```
curl -X POST https://<운영도메인>/api/v1/auth/social/kakao \
  -H "Content-Type: application/json" \
  -d '{"access_token":"<클라이언트가 카카오 SDK 로 받은 토큰>"}'
```
- 기대: 200 + `{"user_id","nickname","role","access_token"}` + `Set-Cookie: NAENGO_AT=...; Secure; ...`
- 실패 매핑: 토큰 무효 → 401 `SOCIAL_AUTH_FAILED`, 차단 사용자 → 403 `USER_BLOCKED`, 같은 이메일이 LOCAL 로 선가입 → 409 `EMAIL_PROVIDER_CONFLICT`.
- 운영 쿠키는 `Secure` → **HTTPS 환경에서만** 쿠키 정상 동작. (HTTP 로 테스트하면 쿠키 누락처럼 보임 — body 의 `access_token` 으로 확인)

---

## 5. 트러블슈팅

| 증상 | 원인 | 조치 |
|---|---|---|
| `KOE006` redirect mismatch | 콘솔 Redirect URI ≠ `KAKAO_REDIRECT_URI` | 두 값을 **완전 일치**(scheme/host/port/path). localhost vs 127.0.0.1, 끝 슬래시 주의 |
| `KOE101` / invalid client | REST API 키 오타, 앱 키 종류 혼동 | **REST API 키** 사용 확인 (Native/JS 아님) |
| 토큰 교환 실패(`SOCIAL_AUTH_FAILED`, 콜백 단계) | Client Secret "사용함" 인데 서버는 미전송 | 콘솔 Client Secret **"사용 안 함"** |
| 카카오 로그인 화면 안 뜸 | 카카오 로그인 비활성 | 제품 설정 → 카카오 로그인 활성화 ON |
| email 이 `kakao_<id>@social.naengo.com` | 이메일 동의 항목 미수집/미동의 | 정상 동작 (placeholder). 실 이메일 필요 시 동의 항목 설정 |
| 운영 서버 부팅 실패 (`Could not resolve placeholder 'KAKAO_...'`) | prod env 미주입 | `KAKAO_REST_API_KEY`·`KAKAO_REDIRECT_URI` 주입 후 재기동 |
| 쿠키가 안 붙음(운영) | `AUTH_COOKIE_SECURE=true` + HTTP 접속 | HTTPS 로 접속. 임시 디버깅은 body `access_token` 사용 |
| 응답 키가 `accessToken` 인 줄 알았는데 `access_token` | 2026-05-17 전역 snake_case 적용 | 클라이언트는 **snake_case** 파싱 (`access_token`,`user_id`) |

---

## 6. 완료 기준 (→ `user-domain-todo.md §4-1·§4-6` 체크)

- [x] 콘솔: 앱/REST API 키/카카오 로그인 ON/Client Secret OFF/Redirect URI/이메일 동의 — **로컬 검증 완료 (2026-05-18, 콜백 성공으로 입증)**
- [x] env(로컬): `.env` 에 `KAKAO_REST_API_KEY`/`KAKAO_REDIRECT_URI`/`JWT_SECRET`(openssl base64 48) LF 주입, `bootRun` 기동 성공 — / [ ] **운영 env 주입·부팅은 배포 시점 잔여**
- [x] 로컬 브라우저 e2e 1회 — **완료 (2026-05-18)**: 콜백 200 → `user_id=1` `provider=KAKAO` `provider_id=4900722356` 실이메일, `user_profiles` 1건 자동생성, JWT 로 `/api/v1/users/me` 200(snake_case). §3 체크리스트 통과.
- [ ] 운영 흐름 B: 클라이언트 실토큰으로 `POST /api/v1/auth/social/kakao` 200 — **배포·클라이언트 통합 후 잔여**
- [x] 결과를 `user-domain-todo.md §4-1·§4-6` 반영 완료

> **참고(2026-05-18 로컬 트러블슈팅)**: `docker compose up -d` 가 옛 볼륨 재사용 → `password authentication failed for user "naengo"` → `docker compose down -v && up -d`(볼륨 리셋, 로컬 데이터만 삭제, Flyway 재생성)로 해결. §5 표와 동일 케이스.

> 본 런북은 운영/배포 담당이 수행. 코드/서버 로직 변경 없음. 클라이언트(카카오 SDK→토큰) 통합은 front/모바일 담당(§9-1).
