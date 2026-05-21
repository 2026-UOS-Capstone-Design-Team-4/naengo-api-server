# 인증 · 사용자 API 가이드 (팀 공유용)

> 대상: front / admin / AI 서버 담당자
> 범위: **회원가입 · 로그인 · 카카오 소셜 · 로그아웃 · 마이페이지 · 회원탈퇴 · 프로필**
> 상태: **운영 배포 완료** (2026-05-21, 옵션 A — DBv5 정합. 통합테스트 30/30 PASS)
> 작성: API 서버 담당자 · 진실원본: 본 문서 + [`changes/2026-05-21-option-a-contract-diff.md`](changes/2026-05-21-option-a-contract-diff.md)
>
> **2026-05-21 변경점 요약** (옵션 A 채택):
> - signup/login `email` → **`username`** (필드명 변경, validation 3~255자)
> - `/api/v1/users/me` 응답 `email` → **`username`** + `deleted_at` 제거
> - `/api/v1/[admin/]pending-recipes` → **`/api/v1/[admin/]user-recipes`** (path 변경)
> - 응답 `pending_recipe_id` → **`user_recipe_id`** (PK 키 변경)
> - 내부: `social_accounts` → `user_identities` (응답 외형 무영향)
> - PK 폭 BIGINT → INTEGER (JSON number 그대로, 클라 영향 0)

---

## 0. 기본 사항

| 항목 | 값 |
|---|---|
| Base URL | `{API_SERVER}` (로컬 `http://localhost:8080`) |
| 공통 prefix | `/api/v1` |
| 성공 응답 | **raw JSON** (래퍼 없음) |
| 실패 응답 | `{"error":{"code":"...","message":"...","details":{}}}` |
| 인증 방식 | JWT — **헤더 또는 쿠키 둘 다 지원** (헤더 우선) |
| 소셜 | **카카오만** 지원 (구글 미지원) |
| 키 표기 | **전 요청·응답 JSON 키 snake_case 통일** (2026-05-17 적용) |

> ✅ **모든 요청 본문·응답 JSON 키는 snake_case**. 예: `access_token`, `user_id`,
> `is_active`, `created_at`, `current_password`, `new_password`, `user_input`, `cooking_time`.
> 서버 전역 Jackson `SNAKE_CASE` 전략. 클라이언트는 아래 예시 키를 그대로 사용.

### 인증 토큰 사용법

로그인/회원가입/소셜로그인 성공 시 **두 가지 통로로 동시 발급**:

1. **응답 body** 의 `access_token` — 모바일 / 외부 호출용 → 이후 요청에 `Authorization: Bearer <token>`
2. **`Set-Cookie: NAENGO_AT=<jwt>; HttpOnly; Path=/; Max-Age=86400; SameSite=Lax`** (prod `Secure`) — 브라우저 자동 동봉

→ 클라이언트는 둘 중 편한 방식 선택. 서버는 **헤더 우선, 없으면 쿠키 fallback**.
JWT 만료 **24시간**, refresh token 없음 → 만료 시 **재로그인**.

### 공통 에러 코드

| HTTP | code | 의미 |
|---|---|---|
| 400 | `VALIDATION_FAILED` | 요청 값 검증 실패 (`details.fields[]` 에 필드별 사유) |
| 401 | `UNAUTHENTICATED` | 미인증 (토큰 없음/만료/유효X) |
| 401 | `INVALID_CREDENTIALS` | 이메일/비밀번호 불일치 |
| 403 | `FORBIDDEN` | 권한 없음 |
| 403 | `USER_BLOCKED` | 차단된 사용자 |
| 403 | `SOCIAL_PASSWORD_NOT_ALLOWED` | 소셜 사용자 비밀번호 변경 시도 |
| 404 | `USER_NOT_FOUND` | 사용자 없음 |
| 409 | `EMAIL_ALREADY_EXISTS` | 이미 가입된 이메일 |
| 409 | `NICKNAME_ALREADY_EXISTS` | 이미 사용 중인 닉네임 |
| 409 | `EMAIL_PROVIDER_CONFLICT` | 같은 이메일이 다른 로그인 방식으로 이미 가입됨 |
| 409 | `ALREADY_WITHDRAWN` | 이미 탈퇴한 사용자 |
| 401 | `SOCIAL_AUTH_FAILED` | 카카오 토큰 검증 실패 |

에러 응답 예:
```json
{ "error": { "code": "EMAIL_ALREADY_EXISTS", "message": "이미 사용 중인 이메일입니다." } }
```

---

## 1. 회원가입 — `POST /api/v1/auth/signup`

- 인증: 불요
- 가입 즉시 빈 프로필(`user_profiles`) row 자동 생성 (마이페이지 바로 사용 가능)

**Request**
```json
{ "username": "user@naengo.com", "password": "pw12345A", "nickname": "냉장고요리왕" }
```
| 필드 | 규칙 |
|---|---|
| `username` | 3~255자, 필수. 이메일 형식 강제 안 함(자유 식별자) — 이메일을 그대로 써도 OK |
| `password` | 8~64자, 영문+숫자 각 1자 이상, 필수 |
| `nickname` | 2~20자, 필수 |

**Response `201`** (+ `Set-Cookie: NAENGO_AT`)
```json
{ "user_id": 1, "nickname": "냉장고요리왕", "role": "USER", "access_token": "eyJ..." }
```

**에러**: `400 VALIDATION_FAILED`, `409 EMAIL_ALREADY_EXISTS`, `409 NICKNAME_ALREADY_EXISTS`

---

## 2. 로그인 — `POST /api/v1/auth/login`

- 인증: 불요 · LOCAL(자체가입) 사용자 전용

**Request**
```json
{ "username": "user@naengo.com", "password": "pw12345A" }
```

**Response `200`** (+ `Set-Cookie: NAENGO_AT`)
```json
{ "user_id": 1, "nickname": "냉장고요리왕", "role": "USER", "access_token": "eyJ..." }
```

**에러**: `401 INVALID_CREDENTIALS` (이메일/비번 불일치 — 보안상 구분 안 함, 소셜 전용 계정·탈퇴 계정 포함), `403 USER_BLOCKED`

---

## 3. 카카오 소셜 로그인 — `POST /api/v1/auth/social/kakao`

- 인증: 불요
- 신규 카카오 사용자는 가입 + 빈 프로필 자동 생성, 기존 사용자는 재로그인

### 흐름
```
[클라이언트]
  1. 카카오 SDK / OAuth 로 카카오 access token 획득
  2. POST /api/v1/auth/social/kakao  { "access_token": "<카카오 토큰>" }
[API 서버]
  3. 카카오 API 로 토큰 검증 → provider_user_id (+email) 획득
  4. (provider=KAKAO, provider_user_id) 로 `user_identities` 조회 / 신규 생성
  5. 자체 JWT 발급 (이후 모든 API 는 이 JWT 사용 — 카카오 토큰 불필요)
```

**Request**
```json
{ "access_token": "<카카오에서 받은 access token>" }
```

**Response `200`** (+ `Set-Cookie: NAENGO_AT`)
```json
{ "user_id": 7, "nickname": "kakao_a1b2c3d4", "role": "USER", "access_token": "eyJ..." }
```
- 신규 카카오 사용자 닉네임은 `kakao_<랜덤8자>` 자동 생성 (이후 닉네임 수정 API 로 변경)
- 카카오가 이메일 미동의 시 내부 placeholder(`kakao_<id>@social.naengo.com`)를 **`users.username`** 에 저장 — 식별자는 `(provider, provider_user_id)` 로 `user_identities` 테이블에. `users.username` 의 placeholder 와 별도로 `user_identities.email` 컬럼에 카카오가 준 실 이메일(또는 placeholder) 도 보관.

**에러**: `401 SOCIAL_AUTH_FAILED` (카카오 토큰 무효), `403 USER_BLOCKED`,
`409 EMAIL_PROVIDER_CONFLICT` (같은 username 이 LOCAL 로 이미 가입 → 기존 방식으로 로그인 안내)

---

## 4. 로그아웃 — `POST /api/v1/auth/logout`

- 인증: 불요 (멱등 — 토큰/쿠키 없어도 200)
- 쿠키 만료(`Max-Age=0`)만 수행. stateless JWT 라 토큰 자체는 만료시각까지 유효 (블랙리스트 미적용)

**Response `204`** (+ `Set-Cookie: NAENGO_AT=; Max-Age=0`)

> 클라이언트는 로그아웃 시 저장한 `access_token` 도 폐기할 것.

---

## 5. 내 정보 조회 — `GET /api/v1/users/me`

- 인증: 필요

**Response `200`**
```json
{
  "user_id": 1, "username": "user@naengo.com", "nickname": "냉장고요리왕",
  "role": "USER", "provider": "LOCAL", "is_active": true,
  "created_at": "2026-04-01T09:00:00+09:00"
}
```
- `provider`: `LOCAL` | `KAKAO` (현 서비스 구현 한도. DB CHECK 는 `KAKAO/GOOGLE/NAVER/APPLE` 도 허용해 둠 — 추후 확장)
- 카카오 이메일 미동의 사용자는 `username` 이 placeholder (`kakao_<id>@social.naengo.com`) 일 수 있음
- `deleted_at` 필드 없음 — 탈퇴 사용자는 `is_active=false` 로 판정

**에러**: `401 UNAUTHENTICATED`, `404 USER_NOT_FOUND`

---

## 6. 닉네임 수정 — `PATCH /api/v1/users/me`

- 인증: 필요

**Request**
```json
{ "nickname": "새닉네임" }
```
| 필드 | 규칙 |
|---|---|
| `nickname` | 2~20자, 필수 |

**Response `200`** — 수정된 `UserMeResponse` (§5 와 동일 구조)

**에러**: `400 VALIDATION_FAILED`, `409 NICKNAME_ALREADY_EXISTS`

---

## 7. 비밀번호 변경 — `POST /api/v1/users/me/password`

- 인증: 필요 · **자체 가입자 전용** (`password_hash` 가 있는 사용자만. 소셜 전용 가입자는 403)

**Request**
```json
{ "current_password": "oldPw123", "new_password": "newPw456A" }
```
| 필드 | 규칙 |
|---|---|
| `current_password` | 필수 |
| `new_password` | 8~64자, 영문+숫자 각 1자 이상, 필수 |

**Response `204`**

**에러**: `400 VALIDATION_FAILED`, `401 INVALID_CREDENTIALS` (현재 비번 불일치),
`403 SOCIAL_PASSWORD_NOT_ALLOWED` (카카오 사용자)

---

## 8. 회원 탈퇴 — `DELETE /api/v1/users/me`

- 인증: 필요
- **익명화 방식**: `users` row 보존 + PII nullify(`username`/`password_hash`) + 닉네임 `탈퇴한 사용자_<id>` + `is_active=false`, `is_blocked=true`. (옵션 A 후 `deleted_at` 컬럼 없음 — `is_active=false` 가 탈퇴 표식 단일화)
- 부속 데이터(좋아요/스크랩/제출레시피/프로필/`user_identities` 소셜 link) 삭제 + 채팅방 soft delete(`is_active=false`). 작성 레시피는 보존(응답 시 닉네임 `탈퇴한 사용자` 치환)

**Response `204`** (+ `Set-Cookie: NAENGO_AT=; Max-Age=0`)

탈퇴 후 같은 토큰 재사용 → 차단되어 **401** 응답.

> 채팅: 탈퇴 시 본인 `chat_rooms` 는 **soft delete(`is_active=false`)** 처리 → 이후 채팅 목록/조회에서 제외.
> 메시지 본문 hard delete / PII 스크럽은 AI 서버(메시지 primary writer) 합의 후 승격 예정.

---

## 9. 내 프로필(취향 입력) — `GET / PATCH /api/v1/users/me/profile`

사용자가 직접 입력한 취향/알레르기 문장 배열(`user_input`). **snake_case**.

### `GET /api/v1/users/me/profile` (인증 필요)
**Response `200`**
```json
{ "user_input": ["새우 알레르기가 있어요", "매운 한식을 좋아해요"] }
```
프로필이 비어있으면 `{ "user_input": [] }`.

### `PATCH /api/v1/users/me/profile` (인증 필요)
**전체 교체** — 보낸 배열로 통째 치환 (빈 배열 → 초기화)

**Request**
```json
{ "user_input": ["새우 알레르기가 있어요", "간단한 요리 위주로"] }
```
| 필드 | 규칙 |
|---|---|
| `user_input` | 문자열 배열, **필수**, 최대 50개, 각 항목 1~500자 |

**Response `200`** — 교체된 `{ "user_input": [...] }`

**에러**: `400 VALIDATION_FAILED` (user_input 누락/규격 위반)

---

## 10. (확장) 선호도 — `GET / PATCH /api/v1/users/me/preferences`

> api-3.json 표준 외 **우리 확장**. AI 분석 결과(read-only) + 직접입력 부가필드 포함.
> front 가 `user_input` 만 필요하면 §9 만 쓰면 됨.

### `GET /api/v1/users/me/preferences` (인증 필요)
**Response `200`** (snake_case — `UserPreferencesResponse`)
```json
{
  "user_input": ["..."],
  "cooking_skill": "normal", "preferred_cooking_time": 30, "serving_size": 2.0,
  "allergies": [], "dietary_restrictions": [], "preferred_ingredients": [],
  "disliked_ingredients": [], "preferred_categories": [],
  "frequently_used_ingredients": [], "taste_keywords": [], "recent_recipe_ids": [],
  "ai_analyzed_at": null, "updated_at": "2026-05-01T10:00:00+09:00"
}
```
AI 분석 7필드 + `ai_analyzed_at` 는 **read-only** (AI 서버가 채움).

### `PATCH /api/v1/users/me/preferences` (인증 필요)
직접 입력 영역만 **부분 갱신** (보낸 필드만 변경, null/미포함은 보존)
```json
{ "user_input": ["..."], "cooking_skill": "easy", "preferred_cooking_time": 20, "serving_size": 1.5 }
```
| 필드 | 규칙 |
|---|---|
| `user_input` | 배열, 선택, 최대 50개 |
| `cooking_skill` | `easy`\|`normal`\|`hard`, 선택 |
| `preferred_cooking_time` | 양수(분), 선택 |
| `serving_size` | 0.1~99.9, 선택 |

**Response `200`** — 갱신된 `UserPreferencesResponse`

---

## 11. 클라이언트 통합 가이드

### 11.1 흐름 B — 카카오 소셜 로그인 (front/모바일) ★ 핵심

> 서버 측은 구현·테스트 완료(통합 30건 + 로컬 e2e 26/26 + 카카오 브라우저 e2e). **남은 건 클라이언트 SDK 연동.**
> 서버는 카카오 access token 을 발급/교환하지 않는다 — **클라이언트가 카카오 SDK 로 받은 access token 을 그대로 전달**하면 서버가 검증·계정생성·자체 JWT 발급.

```
[클라이언트]
 1. 카카오 SDK 초기화 (앱: Native 앱 키 / 웹: JavaScript 키 — 콘솔에서 발급, 서버 REST 키와 별개)
 2. 카카오 로그인 → 카카오 access token 획득 (SDK가 처리)
 3. POST {API_SERVER}/api/v1/auth/social/kakao
    Content-Type: application/json
    { "access_token": "<카카오 SDK가 준 access token>" }      ← snake_case 필수
 4. 200 응답 저장:
    { "user_id": 7, "nickname": "kakao_xxxxxxxx", "role": "USER", "access_token": "eyJ..." }
    + Set-Cookie: NAENGO_AT=...   (브라우저는 쿠키 자동, 모바일은 body access_token 사용)
 5. 이후 모든 인증 요청: Authorization: Bearer <응답의 access_token>
```

- **요청 키는 `access_token`** (camelCase `accessToken` 아님 — 2026-05-17 전역 snake_case).
- 신규/기존 자동 분기: 같은 카카오 계정 재호출 시 동일 `user_id` (재가입 X). 닉네임은 서버가 `kakao_<8자>` 자동 생성 → 이후 `PATCH /api/v1/users/me {"nickname":"..."}` 로 변경.
- 에러 처리:
  - `401 SOCIAL_AUTH_FAILED` — 카카오 토큰 무효/만료 → 카카오 재로그인
  - `403 USER_BLOCKED` — 차단 계정 → 안내 후 차단
  - `409 EMAIL_PROVIDER_CONFLICT` — 같은 이메일이 자체(LOCAL) 가입됨 → "기존 이메일/비번 로그인 사용" 안내
- 콘솔 사전 설정(앱/플랫폼/동의): [`kakao-oauth-runbook.md §1`](kakao-oauth-runbook.md).

### 11.2 naengo-front (Flutter)
- 인증/사용자/프로필 호출은 **API 서버**(`{API_SERVER}/api/v1/...`). (SSE 채팅만 AI 서버)
- 카카오: §11.1 흐름. 응답 `access_token` 저장 → 모든 요청 `Authorization: Bearer <access_token>`
- 401 수신 시 토큰 폐기 + 로그인 화면. 로그아웃은 `POST /api/v1/auth/logout` + 로컬 토큰 폐기(서버는 stateless라 토큰 자체는 만료까지 유효).
- 모든 응답 키 **snake_case** 파싱 (`user_id`, `is_active`, `created_at`, `user_input` …).

### 11.3 naengo-admin (React)
- `VITE_API_URL` → API 서버. axios 기본 base `{...}/api/v1`
- 관리자 화면은 ADMIN role 계정으로 로그인 후 JWT 첨부 (axios interceptor)

### 11.4 naengo-ai (FastAPI)
- API 서버가 발급한 JWT 를 **같은 secret(`JWT_SECRET`)** 으로 검증 → `sub`(=user_id) 추출. (Phase 0-1 / D-2 — secret 공유 합의 필요. `deploy-env.md §2`)

---

## 12. 구현/검증 현황

| 항목 | 상태 |
|---|---|
| 회원가입 / 로그인 / 카카오 / 로그아웃 | ✅ 구현·테스트 |
| 마이페이지 조회/수정, 비밀번호 변경, 탈퇴(익명화) | ✅ 구현·테스트 |
| 프로필(user_input) / 선호도(확장) | ✅ 구현·테스트 |
| 가입·소셜가입 시 `user_profiles` 자동 생성 | ✅ |
| 전 요청·응답 JSON 키 snake_case 통일 | ✅ 2026-05-17 (전역 Jackson `SNAKE_CASE`) |
| 탈퇴 시 채팅방 soft delete | ✅ 2026-05-17 (`chat_rooms.is_active=false`, 무충돌) |
| 통합 테스트 (Testcontainers, clean V1=DBv5) | ✅ 2026-05-22 — `SocialAuthIntegrationTest`(5) + `AuthCookieIntegrationTest`(8) + `RecipeFlowIntegrationTest`(6) + `CorsIntegrationTest`(4) + `ProfileChatIntegrationTest`(3) + `RequestIdIntegrationTest`(4) **= 30/30 PASS** |
| 로컬 e2e (bootRun + docker-compose Postgres + curl 시퀀스) — **옵션 A 후 재검증** | ✅ 2026-05-22 — `scripts/e2e-smoke-prod.sh` **33/33 PASS** (signup/login/duplicate/validation/Bearer+Cookie 인증/users/me 필드(`username`·`provider`·`is_active`+`deleted_at` 부재 검증)/닉네임 변경+충돌/비번 변경+오답/프로필 GET·PATCH/선호도 GET·PATCH/카카오 invalid token/로그아웃 멱등/탈퇴+같은 토큰·username 차단) |
| 로컬 e2e (이전, 옵션 A 이전 코드) | ✅ 2026-05-18 — 카카오 가입(브라우저) + 회원가입/로그인/로그아웃/닉네임·비번 변경/프로필/차단/탈퇴(chat soft-delete) **26/26 PASS** (참고용 기록) |
| 로컬 카카오 브라우저 e2e | ✅ 2026-05-18 ([`kakao-oauth-runbook §3`](kakao-oauth-runbook.md)) |
| **운영 ALB e2e (옵션 A 후 첫 시도)** | ⚠️ 2026-05-22 **PENDING** — 9/33 (서버 코드는 무결. 운영 RDS 의 `public.users` 테이블이 사라진 상태 — `relation "users" does not exist` 500 발화. 어제 `user_id=4` 발급 후 ~10시간 사이 DB 변경 발생. DB 팀원과 원인 확인 후 schema 복구 → 재실행 예정. 자세한 진단: [`changes/2026-05-22-prod-users-table-missing.md`](changes/2026-05-22-prod-users-table-missing.md)) |
| 운영 카카오 키 / 흐름 B | ⬜ 도메인 + HTTPS 부착 후 (`KAKAO_REST_API_KEY` 운영값은 Secrets Manager 에 적재 완료. `KAKAO_REDIRECT_URI` 는 도메인 결정 후 update + 카카오 콘솔 등록) |
| 탈퇴 시 chat 메시지 hard delete/PII 스크럽 | ⬜ AI 서버 합의 후 승격 |

상세 결정 이력: [`spec/user-domain-todo.md`](spec/user-domain-todo.md)
