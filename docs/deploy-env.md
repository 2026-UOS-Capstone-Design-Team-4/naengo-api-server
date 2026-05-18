# 운영(prod) 환경변수 주입 체크리스트

> 대상: 배포/인프라 담당. `user-domain-todo.md §4-1`(운영 카카오 env) + `api-server-tasks.md §8-2`(secret 외부화) 실행 가이드.
> 전제: 코드/설정 변경 없음. `application-prod.yml` 은 placeholder 만 보유 — 값은 **배포 시 env 주입**.
> 템플릿: 루트 [`.env.example`](../.env.example) (커밋됨, secret 없음).

---

## 1. prod 프로파일 활성화

```
SPRING_PROFILES_ACTIVE=prod
```
(미설정 시 `local` 기본 — 운영 배포 시 반드시 prod)

## 2. 필수 env (미주입 시 **부팅 실패** = placeholder 미해결)

| env | 출처/형식 | 비고 |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://<rds-host>:5432/naengo` | AWS RDS(공유 DB). AI 팀과 동일 인스턴스 |
| `DB_USERNAME` / `DB_PASSWORD` | RDS 자격증명 | Secret Manager 권장 |
| `JWT_SECRET` | `openssl rand -base64 48` (≥32바이트) | **AI 서버와 동일 값 공유** (Phase 0-1 / D-2). 회전 시 양 서버 동시 배포 |
| `KAKAO_REST_API_KEY` | 카카오 콘솔 REST API 키 | `KakaoTokenClient` `@Value` 가 부팅 시 요구 → 없으면 기동 실패. 흐름 B(클라이언트 SDK) 자체는 미사용이나 부팅 전제 |
| `KAKAO_REDIRECT_URI` | 콘솔 등록값과 완전 일치 | 운영에서 dev 콜백 미사용이어도 부팅 전제로 필요(형식상 1개 등록) |
| `CORS_ALLOWED_ORIGINS` | `https://front…,https://admin…` 콤마 구분 | 미주입 시 부팅 실패. localhost 기본값이 prod 로 새지 않게 강제 |

## 3. 선택 env (default 존재 — 미주입 시 기본 동작)

| env | default | 영향 |
|---|---|---|
| `AUTH_COOKIE_SAME_SITE` | `Lax` | 크로스도메인 front 배포면 `None` (HTTPS 필수, prod 는 `secure=true` 고정) |
| `AUTH_COOKIE_DOMAIN` | 비움(호스트 한정) | 멀티 서브도메인이면 `.example.com` |
| `AWS_REGION` | `ap-northeast-2` | |
| `AWS_S3_BUCKET` / `AWS_S3_PUBLIC_URL_PREFIX` | 비움 | 비우면 업로드 endpoint 503(의도). S3 준비 후 주입 |

## 4. 부팅 검증 절차 (배포 후 1회)

1. 기동 로그: `Started ApiServerApplication` + `Flyway ... Successfully applied N migrations` (V1~V3) 확인.
   - `Could not resolve placeholder 'XXX'` → §2 필수 env 누락. 주입 후 재기동.
   - `password authentication failed` → DB 자격증명/RDS 보안그룹 확인.
2. 헬스: `curl https://<운영도메인>/health` → `{"status":"UP"}`.
3. 인증 가드: `curl -i https://<운영도메인>/api/v1/users/me` → `401` + `{"error":{"code":"UNAUTHENTICATED"}}`.
4. 자체 회원가입 스모크: `POST /api/v1/auth/signup` → `201` + `Set-Cookie: NAENGO_AT=...; Secure; HttpOnly` + body `access_token`(snake_case).
   - 쿠키에 `Secure` 있는데 HTTP 로 호출하면 미적용처럼 보임 → **HTTPS 로 검증**.
5. 카카오 흐름 B: 클라이언트(SDK) 통합 후 `POST /api/v1/auth/social/kakao {"access_token":"<카카오 토큰>"}` → 200. (절차: [`kakao-oauth-runbook.md §4`](kakao-oauth-runbook.md))

## 5. 주입 방법 (택1)

- **컨테이너/ECS**: 태스크 정의 환경변수 + Secret Manager (DB/JWT/KAKAO).
- **systemd/EC2**: `EnvironmentFile=` 또는 `Environment=`.
- **수동 점검용**: `.env.example` 복사 → 값 채움 → `set -a; source .env; set +a` (LF 필수).

> secret 은 코드/이미지/로그에 남기지 말 것. `application-prod.yml` 은 placeholder 만 — 커밋 OK, 값은 런타임 주입.

## 6. 완료 체크 (→ `user-domain-todo.md §4-1`)

- [ ] prod env(§2 필수 6종) 주입, `SPRING_PROFILES_ACTIVE=prod` 기동 성공
- [ ] `/health` 200, 인증 가드 401(`UNAUTHENTICATED`), signup 201+Secure 쿠키
- [ ] `JWT_SECRET` AI 서버와 동일 값 공유 합의·반영 (D-2)
- [ ] 카카오 흐름 B 200 (클라이언트 SDK 통합 후)
