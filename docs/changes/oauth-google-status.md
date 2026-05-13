# OAuth Google — 현재 미실현 상태 (코드 placeholder)

## 0. 메타

| 항목 | 값 |
|---|---|
| 변경 이력 ID | `oauth-google-status` |
| 대상 | `domain/user/service/SocialAuthService.googleLogin`, `global/auth/oauth/GoogleOAuthClient` |
| 변경 종류 | 상태 명시 (미구현 marker) |
| 작성자 | API 서버 담당자 |
| 작성일 | 2026-05-08 |

---

## 1. 무엇이 문제인가

코드에는 구글 소셜 로그인 흐름이 들어가 있다:
- `GoogleOAuthClient.getUserInfo(accessToken)` — `https://www.googleapis.com/oauth2/v3/userinfo` 호출
- `SocialAuthService.googleLogin(SocialLoginRequest)` — `OAuthUserInfo` 받아 자체 JWT 발급
- `AuthController.googleLogin` — `POST /api/auth/social/google`

**그러나 사용자(API 서버 담당자) 가 구글 OAuth 토큰 발급 측 작업을 진행한 적이 없으며, 운영 환경에서도 구글 로그인은 검증되지 않았다.**

---

## 2. 영향 / 현 상태

- 코드 자체는 빌드/실행됨 (위 클라이언트가 구글 API 의 응답 schema 를 가정하고 있음)
- 클라이언트가 실제 구글 access token 을 보내면 동작할 가능성은 있으나, 동작 여부 / 에러 케이스 검증 없음
- **운영에서는 사실상 비활성** — 프론트 / 모바일이 구글 OAuth SDK 통합 작업을 하지 않은 상태
- 환경변수 / OAuth 콘솔 설정도 안 되어 있음

---

## 3. 정책

- **운영**: `POST /api/auth/social/google` 는 활성 endpoint 이지만 클라이언트 통합 전까지는 호출 X. 별도의 비활성 가드는 미구현 (라우트는 살아있음)
- **본 PR (Step 8-2)**: README 의 env 표에서 구글 관련 항목 미포함. 추후 활성화 시점에 추가
- **인벤토리**: tasks.md §1 의 OAuth 항목에 "구글 — 미검증" 메모 추가

---

## 4. 향후 활성 조건

이 항목들이 채워지면 활성화 가능:
- 구글 Cloud Console 에서 OAuth 클라이언트 발급 (Web / Mobile)
- 프론트 / 모바일이 구글 OAuth SDK 통합 → access token 획득
- `GoogleOAuthClient.getUserInfo` 의 응답 schema 변경 가능성 점검 (Google 의 사용자 정보 API 가 잦게 변경 X 라 큰 위험은 아님)
- 통합 테스트 1건 추가 (mock 또는 실 통합)

활성 시점에 본 change-log 의 "후속" 으로 갱신.

---

## 5. 후속

- [ ] 구글 OAuth 활성 결정 시점에 본 status 갱신 + 통합 테스트 추가
- [ ] 비활성 가드를 추가할지 결정 — 활성 결정이 멀어지면 endpoint 자체를 503 으로 비활성화 검토
- [ ] AI 서버 / 프론트 팀과 합의: "현재 구글 로그인 미지원" 을 클라이언트 UX 에서 안내

---

## 6. 참고 — 카카오는 동작

카카오 로그인은 dev 환경에서 검증됨 (`DevOAuthController` 가 OAuth 콜백 흐름 직접 처리).
환경변수 `KAKAO_REST_API_KEY`, `KAKAO_REDIRECT_URI` 가 운영에서 필수 (`application-prod.yml`).
