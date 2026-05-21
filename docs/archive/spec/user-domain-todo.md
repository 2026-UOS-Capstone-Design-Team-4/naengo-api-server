# User 도메인 TODO — 로그인 + 카카오 소셜 (2026-05-13)

> 본 문서는 **TODO 리스트**다. 명세서가 아니다. 각 항목은 PR 단위로 쪼개 발행한다.
> 작성 시점에 user 도메인 코드는 1차 구현되어 있다(`AuthService`, `SocialAuthService`, `KakaoOAuthClient` 등). 본 TODO 는 **점검·결손·정합** 위주다.

---

## 0. 범위 / 비범위

### 범위
- 자체 회원가입 / 로그인 / 로그아웃
- **카카오 소셜 로그인** (1개만)
- 본인 정보 조회·수정 (마이페이지) / 비밀번호 변경 / 회원 탈퇴 (익명화)
- 프로필(`user_profiles`) 조회·수정
- 인증 기반 인프라 (JWT, 쿠키, EntryPoint, 401/403 정합)

### 비범위 (의도적 제외)
- **구글 소셜 로그인** — 2026-05-13 결정으로 완전 제거 (§1 참조).
- AI 서버 ↔ API 서버 service-to-service 인증 (별도 합의).
- 이메일 인증 / 비밀번호 재설정 메일 발송 (MVP 외).

---

## 1. 구글 관련 코드/문서 전면 제거 (P0) — **완료 (2026-05-13)**

> 사용자 결정 (2026-05-13): "구글은 미반영. 구글 관련은 모두 삭제."

대상 파일 / 라인:

- [x] `src/main/java/com/naengo/api_server/global/auth/oauth/GoogleOAuthClient.java` — **파일 삭제** (git rm)
- [x] `src/main/java/com/naengo/api_server/domain/user/entity/AuthProvider.java` — `GOOGLE` enum 값 삭제
- [x] `src/main/java/com/naengo/api_server/domain/user/service/SocialAuthService.java` — `googleOAuthClient` 주입 + `googleLogin(...)` 메서드 + nickname 주석 정리
- [x] `src/main/java/com/naengo/api_server/domain/user/controller/AuthController.java` — `POST /api/auth/social/google` endpoint 삭제
- [x] `src/main/java/com/naengo/api_server/domain/user/entity/User.java` — `LOCAL / KAKAO / GOOGLE` 주석 정리
- [x] `src/main/resources/application.yml` — `oauth.google.*` 블록 삭제
- [x] `src/main/resources/application-prod.yml` — 구글 미실현 주석 라인 삭제
- [x] `src/main/resources/db/migration/V2__add_social_login_fields.sql` — 코멘트에서 GOOGLE 삭제
- [x] `docs/api-server-tasks.md` — Phase 0 OAuth 항목 정리 (3곳)
- [x] `docs/spec/auth-cookie.md`, `docs/spec/user-me-get.md`, `docs/spec/user-password-change.md` — GOOGLE/google 키워드 정리
- [x] `docs/spec-template.md` — `Kakao/Google` → `Kakao`
- [x] `README.md` (프로젝트 루트) — 구글 미실현 안내 삭제 + 카카오 단독 명시
- [x] `docs/README.md` — archive 안내 (`oauth-google-status.md` 폐기 표기) 유지
- [x] `docs/archive/changes/oauth-google-status.md` — archive 이동 완료 (history 보존)
- [x] 통합 테스트 — Google 테스트 없음 확인 완료

DoD: `git grep -in google` 가 (docs/archive, docs/spec/user-domain-todo.md, docs/README.md 의 archive 안내 라인) 외에는 0 매치. **검증 완료.**

---

## 2. 현 코드 진단 — **완료 (2026-05-17, 결정 기록)**

- [x] **2-1. endpoint 매트릭스 vs api-3.json** — [`api3-alignment-and-integration.md §1.1`](api3-alignment-and-integration.md) 에 현행 35개 엔드포인트 전체 표로 캡처. user 도메인 갭은 PR-1·2·7 으로 모두 해소.
- [x] **2-2. `loadActiveUser` 정책** — **결정: 현행 유지.** `deletedAt != null` → `ALREADY_WITHDRAWN`(409). 단 탈퇴 시 `User.anonymize()` 가 `isActive=false`+`isBlocked=true` 로 만들므로, 살아있는 토큰 재사용은 `JwtAuthenticationFilter`/`CustomUserDetailsService` 단계에서 먼저 차단되어 **실사용상 401** 로 응답됨 (`AuthCookieIntegrationTest.withdrawExpiresCookieAndBlocksToken` 가 401 검증). 즉 사용자 체감은 401, 내부 가드는 409 — 추가 변경 불요.
- [x] **2-3. 이메일 충돌 — 결정: 옵션 (a) 채택.** 동일 이메일이 LOCAL 로 이미 있으면 카카오 로그인은 `EMAIL_PROVIDER_CONFLICT`(409). 사용자는 기존 LOCAL 로그인 사용. 자동 계정통합(옵션 b)은 MVP 범위 외. `SocialAuthIntegrationTest.emailConflictWithLocalAccount` 검증.
- [x] **2-4. 카카오 placeholder 이메일 — 결정: 현행 유지.** 이메일 미동의 시 `kakao_{providerId}@social.naengo.com`. 진짜 식별자는 `(provider, provider_id)` UNIQUE(V2). `email` UNIQUE 는 placeholder 가 providerId 로 유일하므로 충돌 없음. `SocialAuthIntegrationTest.placeholderEmailSignsUp` 검증.
- [x] **2-5. JWT/refresh — 결정: MVP 는 access token 단일, refresh 없음.** `jwt.expiration=86400000`(24h), 쿠키 `max-age=86400`(일치). 만료 시 **재로그인 강제** (refresh token 미도입). 변경 불요. (refresh 도입은 별도 트랙)
- [x] **2-6. `DevOAuthController` prod 가드** — `@Profile("local")` 확인됨. prod 컨텍스트에 빈 미등록. 추가 작업 없음.
- [x] **2-7. `AuthCookieFactory`** — `auth.cookie.*` env 분리 확인. prod `secure=true`, `same-site` 기본 `Lax`(env `AUTH_COOKIE_SAME_SITE` 로 override). **결정: 크로스도메인 front 배포 시 `AUTH_COOKIE_SAME_SITE=None` 로 주입** (코드 변경 없이 env 만). header-우선/쿠키-fallback 은 `AuthCookieIntegrationTest` 4분기로 검증됨.

---

## 3. 도메인 갱신 — api-3.json 정합 (P0~P1)

### 3-1. URL prefix `/api/v1/...` (P0) — **완료 (2026-05-13, PR-1)**
- [x] `AuthController` `@RequestMapping("/api/auth")` → `/api/v1/auth`
- [x] `UserMeController` `@RequestMapping("/api/users/me")` → `/api/v1/users/me`
- [x] `SecurityConfig` 의 매처 (`/api/auth/**`, `/api/users/me/**`) → `/api/v1/...`
- [x] CORS — `/**` 전역 적용이라 path 변경 영향 없음 (확인 완료)
- [x] 통합 테스트 (`AuthCookieIntegrationTest`, `CorsIntegrationTest`, `RecipeFlowIntegrationTest`) 의 path 갱신. 19건 PASS.

> 전 도메인 일괄 적용했으므로 user 외 컨트롤러(Recipe/Like/Scrap/Chat/Admin)도 동시에 `/api/v1` 로 이동됨.

### 3-2. 응답 모양 정합 (P0) — **완료 (2026-05-13, PR-2)**
- [x] `ApiResponse<T>` 래퍼 폐기 → 전 도메인 raw DTO 직접 반환. 에러 응답은 `{error:{code,message,details}}` 표준(`ErrorResponse`) 도입. `GlobalExceptionHandler` / `JwtAuthenticationEntryPoint` / `JwtAccessDeniedHandler` 갱신.
- [x] `UserMeResponse`/`AuthResponse`/`UserPreferences*` 필드 snake_case — **2026-05-17 완료**. `spring.jackson.property-naming-strategy=SNAKE_CASE` 전역 적용으로 일괄 정합 (`user_id`, `access_token`, `is_active`, `created_at`, `current_password`, `cooking_skill` 등). 명시적 `@JsonProperty` 가 붙은 DTO(레시피/채팅/프로필)는 그 이름 우선 유지(`id` 등). 통합 테스트 29건 PASS.

### 3-3. 프로필 endpoint 분리 (P1) — **완료 (2026-05-16, PR-7)**
- [x] `GET /api/v1/users/me/profile` → 응답 `{ user_input: string[] }` 만 (`UserProfileResponse`, snake_case)
- [x] `PATCH /api/v1/users/me/profile` → 요청·응답 모두 `{ user_input: string[] }` (전체 교체, 빈 배열 초기화, 필수)
- [x] `cookingSkill` / `preferredCookingTime` / `servingSize` → `GET/PATCH /api/v1/users/me/preferences` 확장 endpoint 로 분리 (기존 `UserPreferencesResponse`/`UserPreferencesUpdateRequest` 재사용, PUT→PATCH)
- [~] AI 분석 11필드는 현재 `/preferences` 응답(`UserPreferencesResponse`)에 read-only 로 포함됨. 별도 `/ai-profile` 분리는 보류 (front 미사용).
- [x] `UserProfile.empty()` INSERT 트랜잭션 동작 통합 테스트(`ProfileChatIntegrationTest`)로 검증.

### 3-4. 닉네임 변경 (P1)
- [ ] `PATCH /api/v1/users/me` 의 요청 바디 `{nickname}` only (api-3.json `UserUpdateRequest` 정합). 현재 `UserUpdateRequest` 가 이미 그 모양인지 확인. ✅ (`UserUpdateRequest.java` 검토 완료. 변경 없음)
- [ ] 닉네임 중복 시 409 응답 — `NICKNAME_ALREADY_EXISTS` (이미 있음). 변경 없음.

---

## 4. 카카오 소셜 로그인 마무리 (P0)

> 코드는 이미 1차 완료 (`SocialAuthService.kakaoLogin`, `KakaoOAuthClient`, `KakaoTokenClient`, `DevOAuthController`). 점검 위주.

- [x] **4-1.** 카카오 콘솔 등록값 — **로컬 검증 완료 (2026-05-18)**. 콘솔(앱/REST키/카카오로그인 ON/Client Secret OFF/Redirect URI/이메일 동의) 설정 후 로컬 e2e 콜백 성공으로 정합 입증. 런북 [`docs/kakao-oauth-runbook.md`](../kakao-oauth-runbook.md). **운영(prod) env 주입·검증만 배포 시점 잔여.**
- [x] **4-2.** 신규 카카오 사용자의 user_profiles 자동 생성 — **2026-05-16 완료**. `SocialAuthService.processLogin` 신규 분기에서 `User` save 후 `UserProfile.empty(userId)` INSERT (같은 트랜잭션).
- [x] **4-3.** placeholder 이메일 가입 검증 — **2026-05-16 완료**. `SocialAuthIntegrationTest.placeholderEmailSignsUp` (`kakao_5005@social.naengo.com` → 정상 가입 + 프로필 1건).
- [x] **4-4.** 카카오 토큰 검증 실패 → `SOCIAL_AUTH_FAILED` (401). PR-2 표준 ErrorResponse 로 출력 (변경 없음, 정합 확인).
- [x] **4-5.** `is_blocked` 사용자 카카오 로그인 → `USER_BLOCKED` (403). `SocialAuthIntegrationTest.blockedKakaoUserRejected` 로 검증.
- [x] **4-6.** `DevOAuthController` dev 흐름 브라우저 e2e — **완료 (2026-05-18, 실수행)**. `/oauth/kakao/authorize` → 카카오 로그인 → 콜백 200 → `user_id=1` `provider=KAKAO` `provider_id=4900722356` 실이메일, `user_profiles` 1건 자동생성, 발급 JWT 로 `/api/v1/users/me` 200(snake_case). 런북 [`§3 체크리스트`](../kakao-oauth-runbook.md) 통과. 재로그인/차단은 `SocialAuthIntegrationTest` 로 커버(실계정 파괴 회피).
- [x] **4-7.** 통합 테스트 — **2026-05-16 완료**. `SocialAuthIntegrationTest` 신설 (`@MockitoBean KakaoOAuthClient`). 5케이스: 신규 가입+프로필, 재로그인 동일 user_id, `is_blocked` 403, 이메일 충돌 409 `EMAIL_PROVIDER_CONFLICT`, placeholder 이메일.
- [ ] **4-8.** front / 모바일 통합 가이드 — naengo-front 가 카카오 SDK 로 access token 받은 뒤 `POST /api/v1/auth/social/kakao` 호출하는 흐름을 README 또는 spec 문서에 명시. (클라이언트 전달 항목 §9-1)

---

## 5. 회원가입 / 로그인 (자체) 점검 (P0)

> 1차 구현됨 (`AuthService.signUp`, `login`). 점검 + 누락 보강.

- [x] **5-1.** 회원가입 시 `user_profiles` 자동 INSERT — **2026-05-16 완료**. `AuthService.signUp` 이 `userRepository.save` 후 `userProfileRepository.save(UserProfile.empty(userId))` (같은 `@Transactional`). `AuthCookieIntegrationTest.signupReturnsBothCookieAndToken` 가 `user_profiles` row 1건 검증.
- [x] **5-2.** 이메일/비밀번호 검증 — 점검 완료. `SignUpRequest`: `@Email`, password `@Size(8,64)`+`@Pattern`(영문+숫자), nickname `@Size(2,20)`. `LoginRequest`: `@Email`+`@NotBlank`. **이미 충족** — 변경 없음.
- [x] **5-3.** 비밀번호 정책 통일 — 점검 완료. `SignUpRequest` 와 `PasswordChangeRequest` 의 `@Size(8,64)` + 동일 `@Pattern` regex 일치. nickname 정책도 `SignUpRequest`/`UserUpdateRequest` 모두 `@Size(2,20)`. **일관됨** — 변경 없음.
- [x] **5-4.** 로그인 응답 `AuthResponse{userId,nickname,role,accessToken}` — 자체 결정 유지 (api-3.json 미정의). 변경 없음.
- [x] **5-5.** 차단된 LOCAL 사용자 로그인 → `USER_BLOCKED` (403) — 코드상 정상 (`AuthService.login` `user.isBlocked()` 가드). 변경 없음.
- [x] **5-6.** 탈퇴된 사용자 로그인 → `findByEmail` nullified 미스 → `INVALID_CREDENTIALS` (401). 의도된 동작. 변경 없음.
- [x] **5-7.** 로그아웃 — 쿠키 만료만 (`AuthController.logout`, 멱등, 인증 불요). stateless JWT 블랙리스트 미적용 정책 명시. `AuthCookieIntegrationTest.logoutExpiresCookie` 검증.

---

## 6. 비밀번호 변경 / 탈퇴 점검 (P1) — **완료 (2026-05-17, 점검·결정)**

- [x] **6-1.** `changePassword` → 204 No Content. api-3.json 미정의 영역, 자체 결정 유지. `user-password-change.md` 규칙(LOCAL 한정, 현재비번 검증) 코드 정합 확인. 변경 없음.
- [x] **6-2.** `withdraw` 의 chat 자원 처리 — **부분 완료 (2026-05-17, 옵션 1 폴백 적용).**
  - **적용**: `withdraw` 트랜잭션에서 `ChatRoomRepository.deactivateAllByUserId` → 본인 `chat_rooms` 전부 `is_active=false`. 우리 권한 내(PR-7/D-6, soft delete) 무충돌 → AI 합의 없이 배포 가능. 즉시 우리 API 비노출 + 목록 제외. `ProfileChatIntegrationTest.withdrawDeactivatesChatRooms` 검증(방 2개 → is_active=false, 행·메시지 보존).
  - **잔여 (AI 합의 후 승격)**: `chat_messages` 본문 PII 스크럽 / `chat_rooms` hard delete (FK CASCADE 로 messages 동시 삭제). AI 서버가 메시지 primary writer 라 단독 hard delete 불가. **합의 안건서 작성 완료**: [`docs/changes/chat-withdrawal-ai-agreement.md`](../changes/chat-withdrawal-ai-agreement.md) (옵션 A 권고 + 즉시적용 코드/테스트 스펙 + AI 팀 회신 질문). 합의 시 §4 스펙대로 1메서드 교체로 승격.
- [x] **6-3.** `withdraw` → 204 + 쿠키 만료. `AuthCookieIntegrationTest.withdrawExpiresCookieAndBlocksToken` 검증.
- [x] **6-4.** `withdraw` 후 동일 토큰 재사용 → 실사용상 **401** (`anonymize()` 의 `isActive=false`+`isBlocked=true` 로 필터단 차단). 통합 테스트로 401 확정. §2-2 결정과 동일.

---

## 7. 인프라 — JWT / 쿠키 / 401·403 (P1) — **완료 (2026-05-17, 점검·결정)**

- [x] **7-1.** JWT 만료 — **결정: 24h** (`jwt.expiration=86400000`). 1주일 대신 24h 채택(MVP, refresh 없음). 쿠키 `max-age=86400` 와 일치 확인.
- [x] **7-2.** secret 회전 — prod `jwt.secret=${JWT_SECRET}` (default 없음 → 미주입 시 부팅 실패로 보호). AI 서버 공유/회전은 별도 트랙 (api3-alignment D-2, §9-4). 절차 메모만, 코드 변경 없음.
- [x] **7-3.** 쿠키 secure/sameSite — prod `secure=true`, `same-site` 기본 `Lax` (+ `AUTH_COOKIE_SAME_SITE` env override). 크로스도메인 front 배포 시 `None` 주입 (§2-7 결정).
- [x] **7-4.** header-우선 / 쿠키-fallback — `AuthCookieIntegrationTest` 4분기(헤더만/쿠키만/둘다/없음) PASS 로 검증됨.
- [x] **7-5.** 401/403 응답 — **PR-2 완료**. `JwtAuthenticationEntryPoint`(401 `UNAUTHENTICATED`) / `JwtAccessDeniedHandler`(403 `FORBIDDEN`) 모두 표준 `ErrorResponse` 출력.

---

## 8. 마이그레이션 / 시드 (P2) — **완료 (2026-05-17)**

- [x] **8-1.** `V2__add_social_login_fields.sql` 주석 GOOGLE 정리 — PR-0 에서 완료.
- [x] **8-2.** 운영 시드 — **결정: api-server 별도 시드 두지 않음.** 관리자 승격은 운영 시 DB 직접/AI 서버 시드(`naengo-ai/db/seeds/admin_user.sql`) 책임. api-server 스키마와 충돌 없음(역할은 `users.role`).
- [x] **8-3.** dev 시드 — **결정: 불필요.** 통합 테스트가 signup/카카오 mock 으로 데이터 생성 (`SocialAuthIntegrationTest` 등). 별도 dev 시드 미도입.

---

## 9. 클라이언트 / 외부 합의 (P2)

- [ ] **9-1.** naengo-front 에 카카오 SDK 통합 흐름 문서화 요청 (B-4 in api3-alignment).
- [ ] **9-2.** naengo-front 에 JWT 저장 / Authorization 헤더 첨부 / 401 처리 요청 (B-5).
- [ ] **9-3.** naengo-admin 에 관리자 로그인 도입 시점 협의 (현재 anonymous → Admin role 필요).
- [ ] **9-4.** AI 서버에 JWT 공유 합의 (Phase 0-1) — 본 TODO 외 별도 트랙.

---

## 10. 권고 PR 순서

1. **PR-A1 (P0)**: §1 구글 전면 제거. 빌드 PASS 검증.
2. **PR-A2 (P0)**: §3-1 `/api/v1/` prefix 일괄 + SecurityConfig + 통합 테스트 path 갱신. **클라이언트 영향 점진 도입**: 일정 기간 두 prefix 공존 옵션 검토.
3. **PR-A3 (P0)**: §4-2, §5-1 — 신규 가입(자체 + 카카오) 시 `user_profiles.empty()` 자동 생성. 통합 테스트 보강.
4. **PR-A4 (P0)**: §3-3 프로필 endpoint 응답 모양 정합 (`user_input` 만).
5. **PR-A5 (P1)**: §3-2 `ApiResponse<T>` 폐기 + 표준 에러 응답 — **전 도메인 영향**, 본 TODO 와 별도 트랙으로 발행하되 의존성 명시.
6. **PR-A6 (P1)**: §4-7 카카오 통합 테스트 신설.
7. **PR-A7 (P2)**: §6-2 chat 자원 탈퇴 처리 — AI 서버 합의 후.

각 PR 별로 `docs/changes/SPEC-...-CL01.md` 또는 본 TODO 의 체크 처리.

---

## 11. DoD (전체 완료 조건)

- [x] `git grep -in google` 결과 0 매치 (docs/archive·본 TODO 제외) — PR-0
- [x] `./gradlew build` / `test` PASS — **통합 테스트 29건** (Auth 8 + Cors 4 + ProfileChat 2 + Recipe 6 + RequestId 4 + **SocialAuth 5**)
- [x] 카카오 신설 통합 테스트 ≥5건 — `SocialAuthIntegrationTest` 5건
- [x] 회원가입/카카오 가입 시 `user_profiles` 자동 생성 (§5-1/§4-2) + 테스트
- [ ] front / admin 의 `/api/v1/auth/*` 실호출 e2e (클라이언트 측 작업 — §9)
- [x] 카카오 dev 흐름 브라우저 1회 — **2026-05-18 완료** (§4-6, [`kakao-oauth-runbook §3`](../kakao-oauth-runbook.md))
- [x] 로컬 실서버 e2e — **2026-05-18 26/26 PASS** (카카오 가입 + 자체 회원가입/로그인/로그아웃/닉네임·비번/프로필/차단/탈퇴 chat soft-delete, 실행 인스턴스 + `.env` 실설정)
- [x] [`api-server-tasks.md`](../api-server-tasks.md) §1.0 인벤토리에 user 도메인 상태 반영

> **2026-05-16~17 진행 — 코드/결정 항목 전부 종결.**
> §1(구글 제거), §2(진단·결정 기록), §3-1~3-3(prefix/ErrorResponse/프로필), §4-2·4-3·4-5·4-7(카카오 점검+테스트), §5-1~5-7(회원가입/로그인 점검+프로필 자동생성), §6(비번/탈퇴 + chat soft delete 적용), §7(JWT/쿠키 점검·결정), §8(시드 결정) 완료.
> **남은 것은 코드 작업이 아닌 외부/운영·합의 의존 항목뿐**: §4-1 운영(prod) 카카오 env 주입 + 흐름 B(클라이언트 SDK) 배포 검증, §6-2 잔여(chat 메시지 hard delete/PII 스크럽 — AI 합의 후 승격), §9(front/admin/AI 전달·합의).
> 즉 **User-table 기반 인증 API(회원가입·로그인·카카오 소셜·로그아웃·마이페이지·탈퇴·프로필)는 구현·통합테스트·로컬 실서버 e2e 모두 완료**. 통합 테스트 **30건 PASS** + 로컬 실서버 e2e **26/26 PASS**(2026-05-18, 카카오 브라우저 포함).
