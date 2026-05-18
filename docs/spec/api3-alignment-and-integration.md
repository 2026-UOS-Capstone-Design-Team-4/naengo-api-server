# SPEC-20260513-01: api-3.json 정합 & 통합 작업 정리

## 0. 메타

| 항목 | 값 |
|---|---|
| 명세서 ID | `SPEC-20260513-01` |
| 도메인 | (외부 contract — AI 서버 OpenAPI 0.1.0 vs 현 API 서버) |
| 기능명 | api-3.json 기준 정합화 + DB 스키마 결합 + front/admin/ai 통합 작업 정리 |
| 작성자 | API 서버 담당자 |
| 작성일 | 2026-05-13 |
| 우선순위 | P0 (다음 통합 PR 의 입력) |
| 원본 | [`docs/api-3.json`](../api-3.json) (AI 서버 OpenAPI 3.1.0 dump), [`docs/spec/ai-server-contract.md`](ai-server-contract.md) (api-1.json 시점 갭분석) |
| 비교 대상 코드 | `naengo-api-server` 현재 main, `naengo-ai/app/models/*.py`, `naengo-ai/db/schema.sql`, `naengo-front/lib/services/*.dart`, `naengo-admin/src/api/*.ts` |

> 본 문서는 명세 자체가 아니라 **"api-3.json 기준으로 무엇을 바꿔야 하는지"** 를 한 줄로 캡처한 변경 카탈로그다. 각 변경 항목은 별도 SPEC 발행 또는 기존 SPEC change-log 로 연결한다.

---

## 1. 요약 (TL;DR)

> **2026-05-16 현재 — PR-1 ~ PR-7 완료**. 아래 표의 "api-3.json 요구"가 현행 구현 상태다.
> 통합 테스트 30건 PASS (+카카오 소셜 5, +탈퇴 chat soft delete). 진행 로그는 §6, 현행 엔드포인트 전체 목록은 §1.1.

| 영역 | api-3.json 요구 (= 현행) | 상태 |
|---|---|---|
| URL 프리픽스 | `/api/v1/...` (전 컨트롤러 + SecurityConfig) | ✅ PR-1 |
| 응답 규약 | 성공 = raw JSON, 실패 = `{"error":{code,message,details}}` | ✅ PR-2 |
| 레시피 제출 | `POST /api/v1/pending-recipes` 201 → `PendingRecipeResponse` | ✅ PR-4 |
| 내 제출 목록 | `GET /api/v1/pending-recipes` (단순 배열) | ✅ PR-4 |
| 제출 단건 | `GET /api/v1/pending-recipes/{id}` (본인만) | ✅ PR-4 |
| 제출 삭제 | `DELETE /api/v1/pending-recipes/{id}` (soft, 200+메시지, 재삭제 404) | ✅ PR-4 |
| 레시피 목록 | `GET /api/v1/recipes?sort&cursor&limit` (커서) + engagement 포함 | ✅ PR-3 |
| 레시피 단건 | `GET /api/v1/recipes/{id}` → `RecipeListItemResponse` (목록 동형) | ✅ PR-3 |
| 좋아요 | `POST/DELETE /api/v1/recipes/{id}/likes` (409 `ALREADY_LIKED`/`NOT_LIKED`) | ✅ PR-5 |
| 스크랩 | `POST/DELETE /api/v1/recipes/{id}/scraps` (409) + `GET /api/v1/users/me/scraps` (커서) | ✅ PR-5 |
| 내 프로필 | `GET/PATCH /api/v1/users/me/profile` (`{user_input}` 전체 교체) | ✅ PR-7 |
| 선호도(확장) | `GET/PATCH /api/v1/users/me/preferences` (cookingSkill 등 + AI 분석 read-only) | ✅ PR-7 (우리 확장) |
| 채팅 목록 | `GET /api/v1/chat/rooms` (단순 배열, snake_case) | ✅ PR-7 |
| 채팅 메시지 | `GET /api/v1/chat/rooms/{room_id}` (suffix 없음, 단순 배열) | ✅ PR-7 |
| 채팅 삭제 | `DELETE /api/v1/chat/rooms/{room_id}` (soft) | ✅ PR-7 |
| SSE 채팅 | `POST /api/v1/chat/rooms`, `POST /api/v1/chat/rooms/{id}` | ⚪ AI 단독 — api-server 미구현 (의도) |
| 관리자 검토 | `PATCH /api/v1/admin/pending-recipes/{id}` 단일 (부분수정+승격) | ✅ PR-6 |
| 관리자 단건 (video_url) | `GET /api/v1/admin/recipes?video_url=` | ✅ PR-6 |
| 승인 미충족 | 400 `PENDING_RECIPE_INCOMPLETE` (구 422) | ✅ PR-6 |
| 전 요청·응답 JSON 키 | snake_case 전역 통일 (Jackson `SNAKE_CASE`) | ✅ 2026-05-17 |
| 헬스체크 | `GET /` 추가 (`/health` 유지) | ⬜ PR-8 미적용 |

> **DB 스키마**: 현 V1+V2+V3 가 이미 naengo-ai 의 실 SQLAlchemy 모델(app/models/*.py)과 거의 정합. naengo-ai/db/schema.sql 의 정규화 설계는 미적용 상태(aspirational). **소셜 로그인 4 컬럼(`provider`/`provider_id`/`password_hash` nullable/`deleted_at`)은 그대로 유지**, 그 외에는 naengo-ai 의 실모델을 따라가면 추가 ALTER 가 거의 없음. §3 참조.

---

## 1.1 현행 엔드포인트 표면 (PR-7 기준, 진실원본)

> 응답: 성공 = raw JSON(스네이크 케이스 응답 키), 실패 = `{"error":{"code","message","details?"}}`.
> 인증: `/api/v1/auth/**` 와 `GET /api/v1/recipes**` 는 permitAll, `/api/v1/admin/**` 는 ADMIN, 그 외 인증 필요.

| Method | Path | 설명 | 응답 |
|---|---|---|---|
| `POST` | `/api/v1/auth/signup` | 회원가입 | `AuthResponse` + Set-Cookie |
| `POST` | `/api/v1/auth/login` | 로그인 | `AuthResponse` + Set-Cookie |
| `POST` | `/api/v1/auth/social/kakao` | 카카오 소셜 로그인 | `AuthResponse` + Set-Cookie |
| `POST` | `/api/v1/auth/logout` | 로그아웃 (쿠키 만료, 멱등) | 204 |
| `GET` | `/api/v1/users/me` | 내 정보 | `UserMeResponse` |
| `PATCH` | `/api/v1/users/me` | 닉네임 수정 | `UserMeResponse` |
| `POST` | `/api/v1/users/me/password` | 비밀번호 변경 (LOCAL) | 204 |
| `DELETE` | `/api/v1/users/me` | 회원 탈퇴 (익명화) | 204 + 쿠키 만료 |
| `GET` | `/api/v1/users/me/profile` | 취향 입력 조회 | `{user_input:[]}` |
| `PATCH` | `/api/v1/users/me/profile` | 취향 입력 전체 교체 | `{user_input:[]}` |
| `GET` | `/api/v1/users/me/preferences` | 선호도(+AI분석) 조회 (확장) | `UserPreferencesResponse` |
| `PATCH` | `/api/v1/users/me/preferences` | 직접입력 선호도 부분 갱신 (확장) | `UserPreferencesResponse` |
| `GET` | `/api/v1/users/me/scraps` | 내 스크랩 (커서) | `RecipeListResponse` |
| `GET` | `/api/v1/recipes?sort&cursor&limit` | 공개 레시피 목록 (커서) | `RecipeListResponse` |
| `GET` | `/api/v1/recipes/{id}` | 레시피 단건 | `RecipeListItemResponse` |
| `POST` | `/api/v1/recipes/{id}/likes` | 좋아요 (중복 409) | `RecipeStatsResponse` |
| `DELETE` | `/api/v1/recipes/{id}/likes` | 좋아요 취소 (미적용 409) | `RecipeStatsResponse` |
| `POST` | `/api/v1/recipes/{id}/scraps` | 스크랩 (중복 409) | `RecipeStatsResponse` |
| `DELETE` | `/api/v1/recipes/{id}/scraps` | 스크랩 취소 (미적용 409) | `RecipeStatsResponse` |
| `POST` | `/api/v1/pending-recipes` | 레시피 제출 (201) | `PendingRecipeResponse` |
| `GET` | `/api/v1/pending-recipes` | 내 제출 목록 (배열) | `PendingRecipeResponse[]` |
| `GET` | `/api/v1/pending-recipes/{id}` | 내 제출 단건 | `PendingRecipeResponse` |
| `DELETE` | `/api/v1/pending-recipes/{id}` | 제출 soft delete | `{message}` |
| `GET` | `/api/v1/chat/rooms` | 내 채팅방 (배열) | `ChatRoomListItemResponse[]` |
| `GET` | `/api/v1/chat/rooms/{id}` | 채팅 메시지 (배열) | `ChatMessageResponse[]` |
| `DELETE` | `/api/v1/chat/rooms/{id}` | 채팅방 soft delete | `{message}` |
| `GET` | `/api/v1/admin/pending-recipes?status` | (확장) 검토 목록 | `AdminPendingRecipeListResponse` |
| `GET` | `/api/v1/admin/pending-recipes/{id}` | (확장) 검토 단건 | `AdminPendingRecipeDetailResponse` |
| `PATCH` | `/api/v1/admin/pending-recipes/{id}` | 부분 수정 + 승인/반려 통합 | `PendingRecipeResponse` |
| `GET` | `/api/v1/admin/recipes?video_url=` | video_url 중복 조회 | `RecipeListItemResponse` |
| `POST` | `/api/v1/admin/users/{id}/block` `…/unblock` | (확장) 사용자 차단/해제 | `AdminUserBlockResponse` |
| `GET` | `/health` | 헬스체크 | `{status,message}` |
| `GET` | `/oauth/kakao/**` | (local 전용) dev OAuth 콜백 | — |

> SSE (`POST /api/v1/chat/rooms`, `POST /api/v1/chat/rooms/{id}`) 는 **AI 서버 단독** — api-server 미구현 (의도).

---

## 2. api-3.json 정독 — 바꿔야 할 부분

### 2-1. URL prefix 통일 (`/api/v1/...`)

api-3.json, naengo-front, naengo-admin 모두 `/api/v1/...` 를 사용. 현 api-server 만 `/api/...`.

| 컨트롤러 | Before | After |
|---|---|---|
| `AuthController` | `/api/auth/**` | `/api/v1/auth/**` |
| `UserMeController` | `/api/users/me/**` | `/api/v1/users/me/**` |
| `RecipeController` | `/api/recipes/**` | `/api/v1/recipes/**` + `/api/v1/pending-recipes/**` (분리) |
| `LikeController` | `/api/recipes/{id}/like` | `/api/v1/recipes/{id}/likes` |
| `ScrapController` | `/api/recipes/{id}/scrap` + `/api/scraps/my` | `/api/v1/recipes/{id}/scraps` + `/api/v1/users/me/scraps` |
| `ChatController` | `/api/chat/**` | `/api/v1/chat/**` |
| `AdminRecipeController` | `/api/admin/pending-recipes/**` | `/api/v1/admin/pending-recipes/**` + `GET /api/v1/admin/recipes` 신설 |
| `AdminUserController` | `/api/admin/users/**` | `/api/v1/admin/users/**` (api-3.json 에는 없는 우리만의 확장 — 유지) |

> `SecurityConfig` 의 인증 화이트리스트 / permitAll 경로도 동일하게 `/api/v1/...` 로 갱신 필요. CORS 노출 헤더는 변경 없음.

### 2-2. 레시피 제출 흐름 분리 — `recipes` ↔ `pending-recipes`

api-3.json 은 사용자 제출과 정식 레시피를 **path 자체로 분리**.

- `POST /api/v1/pending-recipes` — 201 + `PendingRecipeResponse`
  - 필수: `title`, `content`
  - 선택: 그 외 모든 구조화 필드 (`ingredients` 등)
  - 응답에 `status`(=`PENDING`), `pending_recipe_id`, `reviewed_at: null`, `admin_note: null` 포함
- `GET /api/v1/pending-recipes` — `PendingRecipeResponse[]` (페이지네이션 없이 단순 배열, `is_active=true` 만)
- `GET /api/v1/pending-recipes/{id}` — 본인 제출 한 건
- `DELETE /api/v1/pending-recipes/{id}` — **soft delete (`is_active=false`)**

**현 api-server 차이**:
- 현재 `POST /api/recipes` 가 pending 에 INSERT 하고 있지만 응답이 `RecipeCreateResponse(pendingRecipeId, status)` 로 빈약. api-3.json 은 전체 `PendingRecipeResponse` 반환을 요구.
- `GET /api/recipes/my` 는 페이지네이션 + 일부 필드만. api-3.json 은 **단순 배열** + 전체 필드.
- `DELETE /api/recipes/{id}` 는 hard delete. api-3.json 은 soft (`is_active`).
- 사용자용 단건 조회 미구현.

### 2-3. 좋아요 / 스크랩 — 멱등 X, POST + DELETE 분리

api-3.json:
- `POST /api/v1/recipes/{id}/likes` — 이미 좋아요면 409. 200 응답으로 `RecipeStatsResponse {likes_count, scrap_count}` 반환.
- `DELETE /api/v1/recipes/{id}/likes` — 안 눌렀으면 409. 200 응답으로 동일.
- `POST/DELETE /api/v1/recipes/{id}/scraps` — 동형.

**현 api-server 차이**:
- 토글 한 endpoint (`POST` 단일) → 이력성/멱등성 약함. 409 충돌 시그널 없음.
- ErrorCode 에 `ALREADY_LIKED`, `NOT_LIKED`, `ALREADY_SCRAPPED`, `NOT_SCRAPPED` 추가 필요.

### 2-4. 레시피 목록 — 커서 기반 페이지네이션 + 사용자 컨텍스트 포함

api-3.json `GET /api/v1/recipes?sort&cursor&limit`:
- `sort`: `latest` (기본) / `likes`. **`popular` 아님** — 현 api-server `popular` 매핑 변경 필요.
- `cursor`: 이전 응답의 `next_cursor`. 형식은 서버 정의 (예: `42_1` = `(sort_key, recipe_id)`).
- 응답: `RecipeListResponse { items, next_cursor, has_next }`.
- 각 item 에 `likes_count`, `scrap_count`, `is_liked`, `is_scrapped` 포함.

**현 api-server 차이**:
- `page`/`size` 기반 offset 페이지네이션. 커서로 전환 필요.
- `RecipeListItemResponse` 에 `is_liked`/`is_scrapped` 미포함. 현재 사용자 기준 join 로직 추가.
- `recipe_id` 가 아니라 `id` 라는 필드명 — 응답 직렬화 이름 맞춤.

### 2-5. 레시피 단건 — `RecipeListItemResponse` 와 동형

api-3.json `GET /api/v1/recipes/{recipe_id}` 는 **목록 항목과 같은 스키마 (`RecipeListItemResponse`)** 를 반환. 즉, 별도 detail 응답 X.

**현 api-server 차이**:
- 별도 `RecipeDetailResponse` 가 존재 (`authorId`, `authorNickname` 등 포함). api-3.json 에는 author 식별 정보 없음.
- author 표시는 노출 정책 결정 사안 → api-3.json 정합을 따르면 `RecipeListItemResponse` 한 종류로 통합. author nickname 노출이 필요하면 별도 endpoint(`/recipes/{id}/author`) 또는 응답 확장 합의.

### 2-6. 내 프로필 (`/api/v1/users/me/profile`) — `user_input` 만

api-3.json:
- `GET` → `UserProfileResponse { user_input: string[] }` (default `[]`)
- `PATCH` 요청 `{ user_input: string[] }`, 응답 동형. **전체 교체** (빈 배열 전달 시 초기화).

**현 api-server 차이**:
- `UserPreferencesResponse` 가 직접 입력 4필드 + AI 분석 7필드 모두 노출.
- 요청은 `PUT` + `UserPreferencesUpdateRequest` (4필드 부분 갱신).
- 메서드 (`PATCH` vs `PUT`), 페이로드 모양 모두 다름.

**판단**:
- naengo-front (`patchProfileInput([])`) 는 `PATCH` + `{user_input}` 만 사용. api-3.json 정합 필요.
- 다만 `cookingSkill`/`preferredCookingTime`/`servingSize` 는 우리만의 직접 입력 필드 — **별도 endpoint 로 분리** 하거나 (예: `PATCH /api/v1/users/me/preferences`), 응답 확장. api-3.json 미정의 영역이라 자체 결정 가능.
- AI 분석 read-only 필드는 별도 endpoint (`GET /api/v1/users/me/ai-profile`) 또는 노출 보류.

### 2-7. 내 스크랩 — `/api/v1/users/me/scraps` (커서)

api-3.json: `GET /api/v1/users/me/scraps?cursor&limit`. 응답 = `RecipeListResponse` (정렬 = 스크랩 시각 내림차순, 각 item `is_scrapped=true`).

**현 api-server 차이**:
- 경로 `/api/scraps/my` → `/api/v1/users/me/scraps`
- offset → cursor
- 정렬 기준이 "스크랩 시각" 임을 명시 (현재 `RecipeListMapper` 가 사용하는 `created_at` 은 recipe 생성 시각)
- 응답 item 에 `is_scrapped` 강제 셋팅

### 2-8. 채팅 — GET/DELETE 만 API 서버 책임, SSE 는 AI 서버

api-3.json:
- `GET /api/v1/chat/rooms` — 활성 채팅방 (updated_at 내림차순)
- `POST /api/v1/chat/rooms` — **SSE** (AI 서버 단독)
- `GET /api/v1/chat/rooms/{id}` — 메시지 시간순 (recipes 포함 enriched)
- `POST /api/v1/chat/rooms/{id}` — **SSE**
- `DELETE /api/v1/chat/rooms/{id}` — soft delete

**현 api-server 차이**:
- 현재 `GET /api/chat/rooms/{id}/messages` (의미는 같지만 path 다름) → `/api/v1/chat/rooms/{id}` 로 통일.
- `DELETE` 미구현. AI 서버가 `is_active=false` 로 토글하는 것과 충돌 없도록 합의 필요 (DDL 은 api-server 단독 관리, DML 은 둘 다 가능 — 양쪽이 같은 UPDATE 를 칠 수 있음).
- 메시지의 `recipes` 필드는 AI 서버가 enrichment 함 (`chat_messages.recipe_ids JSONB` 만 저장됨). api-server 가 GET 으로 반환할 때 동일 enrichment 를 수행해야 함 → `recipe_ids` 를 읽고 `recipes` 테이블 join 해서 `RecipeResponse[]` 로 변환.
- SSE 두 endpoint 는 **AI 서버 단독 책임**. api-server 는 절대 구현하지 않는다.

### 2-9. 관리자 검토 — `PATCH` 단일 endpoint 로 통합

api-3.json: `PATCH /api/v1/admin/pending-recipes/{id}` 한 개로 다 처리:
- 전달하지 않은 필드는 변경 X
- `status` 변경 시 `reviewed_at = NOW()`
- `status=APPROVED` 로 바꾸면 `Recipe` 로 승격 + `RecipeStats` + embedding 자동 생성
- 승인 필수 필드 미충족 시 400 (현 우리는 `PENDING_RECIPE_INCOMPLETE` 422)

**현 api-server 차이**:
- 현재 `POST /api/admin/pending-recipes/{id}/{approve,reject}` 두 endpoint 로 분리.
- naengo-admin 은 이미 `PATCH /api/v1/admin/pending-recipes/{id}` 를 사용 → 명백히 정합 필요 (admin/src/api/recipes.ts:50).
- 승인 시 콘텐츠도 함께 수정 가능 (`title`, `description`, `ingredients` 등) — 현재 우리는 콘텐츠 편집 미지원.
- 상태 코드: 미충족은 api-3.json 이 400, 우리는 422 → **400 으로 변경** 권장 (admin 클라이언트 호환성).

### 2-10. `GET /api/v1/admin/recipes?video_url=` — 신설

naengo-admin (`admin/src/api/recipes.ts:11`) 이 사용 중. 레시피 등록 전 `video_url` 중복 확인용. 현 api-server 에 없음 → 신설.

응답: 단건 `RecipeResponse` (목록 X). 없으면 404.

### 2-11. 응답 wrapper — `ApiResponse<T>` vs raw

api-3.json 의 모든 응답은 **raw JSON** (래퍼 없음). 현 api-server 는 `ApiResponse<T>` (`{success, message, data}`) 로 감쌈.

- 두 클라이언트 모두 raw 를 기대:
  - naengo-front (Dart): `json['user_input']` 으로 바로 접근 (래퍼 없음 가정)
  - naengo-admin (TS axios): `data` 가 `Recipe` / `PendingRecipe` 자체 (래퍼 없음 가정)
- **결정 필요**:
  - 옵션 (A) `ApiResponse<T>` 폐기, raw 직렬화. 에러 응답은 별도 표준 (`{code, message, details}` 권장 — naengo-ai 의 `architecture/api/05-error-response.md` 와 정합).
  - 옵션 (B) 클라이언트가 래퍼를 풀게 강제. → 작업량 큼, naengo-front 다수 호출 갱신. 비추.
  - **추천**: 옵션 (A). 다음 PR 의 최우선.

### 2-12. 헬스체크 — `GET /` 추가

api-3.json: `GET /` → `{status, message}`. 현재 우리는 `/health`. 양쪽 모두 노출 권장 (배포 환경 LB health check 호환성).

---

## 3. DB 스키마 — naengo-ai vs naengo-api-server 결합

### 3-1. 결합 원칙

> 사용자 결정 (2026-05-13):
> - **소셜 로그인 관련 스키마는 api-server 의 것을 유지** (3.1).
> - 그 외에는 naengo-ai 의 스키마를 반영 (3.2 — 레시피 관련 + 사용자 선호도).

### 3-2. naengo-ai 실 스키마 (= `app/models/*.py`) vs 의도 스키마 (= `db/schema.sql`)

naengo-ai 레포 안에 두 가지 schema source 가 공존:

| 항목 | `app/models/*.py` (실 사용) | `db/schema.sql` (aspirational) |
|---|---|---|
| `recipes` | 단일 테이블 + JSONB(`ingredients`, `instructions`, `category`, `tags`, `tips`) | 정규화 (`recipe_ingredients`, `recipe_steps`, `recipe_labels`, `recipe_classifications`, `recipe_media`, `recipe_embeddings`) |
| `user_profiles.preferred_cooking_time` | `Integer` | `preferred_cooking_time_minutes` (rename) |
| `users.email` | UNIQUE NOT NULL | 동일 |
| `users.password_hash` | NOT NULL | 동일 |
| 소셜 로그인 컬럼 | **없음** | **없음** |
| `chat_messages.recipe_ids` | JSONB (id 배열) | 동일 |
| pgvector embedding | `recipes.embedding VECTOR(1536)` 단일 컬럼 | 별도 `recipe_embeddings` 테이블 |
| 실제 운영 DB | ✅ 사용 중 | ❌ 미적용 |

**결론**: api-server 가 정합해야 할 대상은 `app/models/*.py` (실 사용). `db/schema.sql` 은 미래형이므로 채택하지 않는다.

### 3-3. 현 api-server V1+V2+V3 ↔ naengo-ai 실 모델 갭

| 컬럼 / 테이블 | api-server (V1+V2+V3) | naengo-ai (`app/models`) | 결합 결과 |
|---|---|---|---|
| `users.user_id` 타입 | `BIGSERIAL` / `BIGINT` | `Integer` (`SERIAL`) | 🔴 **충돌**. JPA `Long` 사용 → api-server 가 옳지만 AI 가 같은 DB 에서 `Integer` 컬럼을 INSERT/UPDATE 함. **AI 측 모델 변경 요청** 필요 (`Integer` → `BigInteger`). 같은 PostgreSQL 에서 BIGINT/INTEGER 혼용은 가능하지만 AI 가 `Integer` 로 INSERT 시 21억 한계. MVP 에서는 사실상 무해, 정합만 맞추면 됨. |
| `users.email` | nullable (탈퇴 익명화용) | NOT NULL | api-server 가 옳음. AI 모델은 INSERT 만 하므로 nullable 이어도 동작에 문제 없음. **AI 모델 NOT NULL 표기는 ORM 레벨 가드**일 뿐, DB DDL 은 nullable 유지. |
| `users.password_hash` | nullable (소셜 로그인) | NOT NULL | api-server 가 옳음. **유지.** AI 모델은 social 비지원이므로 항상 채움. |
| `users.provider`, `provider_id` | V1 + V2 추가 | 없음 | api-server 가 옳음. **유지.** AI 가 user INSERT 안 하므로 (`TEMP_USER_ID` 만 SELECT) 영향 없음. |
| `users.deleted_at` | V3 추가 | 없음 | api-server 가 옳음. **유지.** |
| `user_profiles.preferred_cooking_time` | `Integer` | `Integer` | ✅ 정합 (양쪽 다 `preferred_cooking_time`). `schema.sql` 의 `_minutes` 접미사는 무시. |
| `user_profiles.user_input` | JSONB NOT NULL DEFAULT '[]' | JSONB nullable=False default=list | ✅ 정합 |
| `user_profiles.allergies` 등 AI 분석 7필드 | JSONB nullable | JSONB nullable | ✅ 정합 |
| `recipes` 컬럼 22개 | 모두 보유 (description, ingredients JSONB, ingredients_raw, instructions JSONB, servings, cooking_time, calories, difficulty, category, tags, tips, content, video_url, image_url, is_active, author_type, author_id, created_at, embedding) | 동일 22개 | ✅ 정합 |
| `pending_recipes` 컬럼 | 모두 보유 | 동일 | ✅ 정합 |
| `chat_rooms`, `chat_messages` | room_id BIGSERIAL | room_id Integer | 동일 충돌 (위 user_id 와 같음) |
| `recipe_stats`, `likes`, `scraps` | 동형 | 동형 | ✅ 정합 |
| 트리거 (likes/scraps 카운터, recipe_stats(0,0) 자동 생성) | api-server V1 에 보유 | (AI 가 직접 카운터 INSERT/UPDATE 안 함) | api-server 가 트리거로 처리. AI 가 likes/scraps INSERT/DELETE 하지 않는다면 무관. |

### 3-4. 결합 결과 — 필요한 마이그레이션 (V4 후보)

현 V1+V2+V3 가 이미 95% 정합. 추가 ALTER 불필요. 단 다음 후속만 정리:

- [ ] **V4 (선택)** — AI 팀과 user_id 타입 합의 후 `recipes.recipe_id` / `users.user_id` / `chat_rooms.room_id` / `pending_recipes.pending_recipe_id` 의 PK 타입을 INTEGER vs BIGINT 로 통일. 현행 BIGSERIAL 유지가 무방. **DDL 변경 없음 → V4 발행 불필요**.
- [ ] **V4-alt** — naengo-ai 의 `db/schema.sql` (정규화) 가 미래에 채택되면 그때 별도 V5 로 마이그레이션. 현 시점 보류.
- [ ] AI 팀에 SQLAlchemy `Integer` → `BigInteger` 변경 요청 (PR 발행). 코드 한 줄 수정, 운영 무영향.

### 3-5. naengo-ai DB 시드 / 어드민 사용자 충돌

`naengo-ai/db/seeds/admin_user.sql` 이 admin user 1 명을 직접 INSERT. api-server 의 `provider='LOCAL'`, `provider_id=NULL` 충돌 가능성 확인 필요. 시드 SQL 도 V2 의 `uq_provider_provider_id` UNIQUE 제약을 어기지 않는지 검토.

---

## 4. 자체 코드 리뷰 — naengo-api-server

### 4-1. 잘 된 점 ✅

- **도메인 분리 깔끔**: `domain/{user, recipe, chat, like, scrap, admin}` + `global/{auth, config, exception, logging, controller, dto}`. 패키지가 자기 책임 명확.
- **마이그레이션 일관**: V1+V2+V3 가 잘 누적. 트리거 책임이 명시적.
- **소셜 로그인**: `User.anonymize()`, `provider`/`provider_id`, V2 의 UNIQUE 가 모두 정합.
- **JWT + 쿠키 dual 노출**: 모바일/웹 양쪽 호환. `AuthCookieFactory` 가 prod/local env 분리.
- **트랜잭션 경계**: `AdminRecipeService.approve` 가 명확한 트랜잭션 boundary. `ensureCompleteForApproval` 가드.
- **DB 트리거로 카운터**: 애플리케이션이 race 걱정 없이 단순.
- **테스트**: Testcontainers 기반 통합 테스트 19건 PASS (auth-cookie, recipe-flow, cors, request-id).

### 4-2. api-3.json 정합 갭 (위 §2 와 중복) — 결합 표

| ID | 항목 | 영향 클라이언트 |
|---|---|---|
| G1 | `/api/v1/` prefix 미사용 | front, admin 양쪽 |
| G2 | `POST /pending-recipes` 응답 빈약 (`RecipeCreateResponse` → `PendingRecipeResponse`) | front |
| G3 | `GET /pending-recipes` 페이지네이션이 단순 배열과 다름 | front |
| G4 | `DELETE /pending-recipes/{id}` hard vs soft | front |
| G5 | 좋아요/스크랩 토글 vs POST/DELETE 분리 | front, admin |
| G6 | 레시피 목록 offset vs cursor | front |
| G7 | 레시피 단건 `RecipeDetailResponse` vs `RecipeListItemResponse` 일치 | front |
| G8 | `is_liked`/`is_scrapped` 응답 누락 | front |
| G9 | 프로필 응답 모양 (`user_input` 만 vs 11필드) | front |
| G10 | 채팅 메시지 path suffix `/messages` 차이 | front |
| G11 | 채팅방 soft delete 미구현 | front |
| G12 | 관리자 검토 PATCH 통합 vs approve/reject 분리 | admin |
| G13 | `GET /admin/recipes?video_url=` 신설 | admin |
| G14 | `ApiResponse<T>` 래퍼 vs raw | front, admin |
| G15 | 헬스체크 path `/health` vs `/` | (외부 LB) |

### 4-3. 코드 단위 개선점 (api-3.json 무관)

| 위치 | 지적 | 권고 |
|---|---|---|
| `RecipeController.java:42` | sort 기본 `latest`, 미지원 시 `INVALID_INPUT`. api-3.json 의 `latest`/`likes` enum 과 우리 `popular` 가 불일치 | enum 매핑: `latest` / `likes`. `popular` 폐기 |
| `RecipeService.delete()` (L172) | `pendingRecipeRepository.delete(pending)` — hard delete | `pending.cancel()` (`is_active=false`) 로 soft. 이미 메서드 존재 (`PendingRecipe.cancel`) — 단순 교체 |
| `ScrapController` | `/api/recipes/{id}/scrap` (단수) | api-3.json 의 `/scraps` (복수) 로 변경 |
| `LikeController` | 동일 (`/like` → `/likes`) | 동일 |
| `Recipe.embedding` 미매핑 | "AI 서버가 관리, 엔티티에 매핑하지 않음" — 좋은 결정 | 유지. 단 schema 에는 존재 |
| `AdminRecipeService.ensureCompleteForApproval` | 422 (`UNPROCESSABLE_ENTITY`) | api-3.json 은 400. 변경 필요 |
| `UserMeService.getPreferences` | AI 분석 11필드 모두 노출 | api-3.json 정합 시 `user_input` 만. AI 분석 노출 필요하면 별도 endpoint |
| `RecipeListMapper` | `created_at` 정렬 가정 | 스크랩 목록은 `scraps.created_at` 정렬 — repository 쿼리 수정 필요 |
| `ApiResponse<T>` (L1) | `{success, message, data}` 래핑 | 폐기 또는 옵션 off. 에러 응답은 `{error: {code, message, details}}` 표준화 (naengo-ai 정책 정합) |
| `RecipeRepository` | offset pagination (`Pageable`) | 커서 기반 native query 추가 (`WHERE (likes_count, recipe_id) < (?, ?) ORDER BY likes_count DESC, recipe_id DESC`) |
| `ChatMessageRepository` (미확인) | recipe_ids 만 저장, GET 시 recipes 조인 필요 | enrichment 서비스 추가 (id 배치 조회 → RecipeResponse[]) |
| `ErrorCode` | `ALREADY_LIKED`/`NOT_LIKED`/`ALREADY_SCRAPPED`/`NOT_SCRAPPED` 부재 | 5-1 좋아요/스크랩 분리 시 추가 |
| `ChatController` | `DELETE` 미구현 | 5-2 (보류된 채팅방 숨김) 구현 |
| `SecurityConfig` | `/api/auth/**`, `/api/recipes` 등 permitAll/authenticated 매트릭스 | `/api/v1/**` 로 갱신 시 동시에 점검 — `/api/v1/admin/**` 는 `hasRole('ADMIN')` 명시 |
| `GlobalExceptionHandler` | 에러 응답이 `ApiResponse.fail()` 사용 | raw 표준 (`{error: {code, message}}`) 으로 변경 |
| `AdminUserController` | api-3.json 미정의 — 우리만의 확장 | 유지 OK. naengo-admin 추후 사용 가능성 (관리자 화면 보강 시) |
| `DevOAuthController` | 로컬 dev 전용. prod 비활성 확인 | `application-prod.yml` 의 profile 가드 확인 |

### 4-4. 보안 / 권한

- 모든 `/api/v1/admin/**` 는 `hasRole('ADMIN')` 강제. `SecurityConfig` 의 `authorizeHttpRequests` 매트릭스 명시.
- `/api/v1/users/me/**` 는 `authenticated()`.
- `/api/v1/recipes` (GET) 은 `permitAll()` 으로 두되, 응답에 `is_liked`/`is_scrapped` 가 들어가야 하므로 **익명 호출 시 false 로 셋팅**.
- `/api/v1/pending-recipes/**` 모두 `authenticated()`.
- CORS 는 이미 분리 — 변경 없음.

---

## 5. front / admin / ai 와의 통합 — 구현해야 할 것

### 5-1. 클라이언트 인벤토리

| 클라이언트 | 베이스 URL | 호출 양식 |
|---|---|---|
| `naengo-front` (Flutter) | `dart-define NAENGO_API_BASE` (현재 AI 서버 `43.201.62.254:8000` 가리킴) | raw HTTP (no wrapper) |
| `naengo-admin` (React) | `VITE_API_URL` + `/api/v1` (default `http://localhost:8000/api/v1`) | axios, raw JSON |
| `naengo-ai` (FastAPI) | (자체 호스팅) | SSE + REST |

> **핵심**: front 와 admin 모두 현재 AI 서버 (`8000`) 를 직접 가리킨다. api-server 가 **api-3.json 의 모양 그대로** 비-AI endpoint 를 제공하면 클라이언트는 base URL 만 갈아끼우면 된다.

### 5-2. 통합 작업 카탈로그

#### A. api-server 측 (우리 책임)

- [x] **A-1.** `/api/v1/...` prefix 일괄 적용 — **2026-05-13 완료 (PR-1)**. 9개 컨트롤러 + SecurityConfig matcher 갱신. `/health`, `/oauth/**` 는 별도 namespace 로 유지.
- [x] **A-2.** `PendingRecipeController` 신설 — **2026-05-16 완료 (PR-4)**. `RecipeController` 에서 pending 분리:
  - `POST /api/v1/pending-recipes` (201 + `PendingRecipeResponse`)
  - `GET /api/v1/pending-recipes` (단순 배열, 본인 + `is_active=true`, 최신순)
  - `GET /api/v1/pending-recipes/{id}` (본인만, 없으면 404)
  - `DELETE /api/v1/pending-recipes/{id}` (soft delete via `PendingRecipe.cancel()`, 200+메시지, 재삭제 404)
- [x] **A-3.** `PendingRecipeResponse` DTO 신설 — **2026-05-16 완료 (PR-4)**. api-3.json snake_case. **사용자 endpoint 전용**; admin DTO 통합은 PR-6 으로 이연 (admin PATCH 작업과 동시).
- [x] **A-4.** `RecipeController` 갱신 — **2026-05-16 완료 (PR-3)**:
  - `GET /api/v1/recipes` — 커서 기반 pagination (`sort`/`cursor`/`limit`), `is_liked`/`is_scrapped`/`likes_count`/`scrap_count`/`created_at` 포함, sort=latest|likes (popular 폐기)
  - `GET /api/v1/recipes/{id}` — `RecipeListItemResponse` 동형 반환 + 현재 사용자 is_liked/is_scrapped
  - `RecipeDetailResponse` 폐기. `RecipeListResponse` = `{items, next_cursor, has_next}`. DTO JSON 키는 api-3.json 정합 snake_case (`@JsonProperty`).
- [x] **A-5.** `LikeController` 갱신 — **2026-05-16 완료 (PR-5)**. `POST/DELETE /api/v1/recipes/{id}/likes` 분리, 200+`RecipeStatsResponse`, 중복=409 `ALREADY_LIKED`, 미적용 취소=409 `NOT_LIKED`, 미존재=404.
- [x] **A-6.** `ScrapController` 갱신 — **2026-05-16 완료 (PR-5)**. `POST/DELETE /api/v1/recipes/{id}/scraps` 분리 + 409 `ALREADY_SCRAPPED`/`NOT_SCRAPPED`. list endpoint 경로 이동 `/api/v1/scraps/my` → `GET /api/v1/users/me/scraps` (커서, PR-3 의 is_scrapped/is_liked 유지).
- [x] **A-7.** `UserMeController` 프로필 분리 — **2026-05-16 완료 (PR-7)**:
  - `GET /api/v1/users/me/profile` → `{ user_input: [...] }` 만 (`UserProfileResponse`, snake_case)
  - `PATCH /api/v1/users/me/profile` → `{ user_input: [...] }` 전체 교체 (`UserInputUpdateRequest`, 빈 배열 초기화, 필수)
  - 직접 입력 추가 필드(`cookingSkill` 등)는 `GET/PATCH /api/v1/users/me/preferences` 확장 endpoint 로 분리 (PUT → PATCH)
- [x] **A-8.** `ChatController` 갱신 — **2026-05-16 완료 (PR-7)**:
  - `GET /api/v1/chat/rooms` (단순 배열, snake_case, updated_at DESC)
  - `GET /api/v1/chat/rooms/{room_id}` — `/messages` suffix 제거, 단순 배열, recipes enrichment
  - `DELETE /api/v1/chat/rooms/{room_id}` — soft delete (`is_active=false`, 200+메시지, 재삭제 404)
  - `ChatRoomListResponse`/`ChatMessageListResponse` 래퍼 폐기, DTO snake_case (`room_id`/`message_id`/`created_at`/`updated_at`)
  - **SSE 두 endpoint 는 미구현** (AI 서버 책임)
- [x] **A-9.** `AdminRecipeController` 갱신 — **2026-05-16 완료 (PR-6)**:
  - `PATCH /api/v1/admin/pending-recipes/{id}` 단일 endpoint (콘텐츠 부분 수정 + status 전이 + APPROVED 승격). 응답 `PendingRecipeResponse`
  - 기존 `POST /approve`, `/reject` **제거** (naengo-admin 은 이미 PATCH 사용). list/detail GET 은 콘솔 편의 확장으로 유지
  - `GET /api/v1/admin/recipes?video_url=...` 신설 (`AdminRecipeLookupController`, 단건 / 404)
  - 승인 미충족 응답 422 → **400** (`PENDING_RECIPE_INCOMPLETE`)
- [x] **A-10.** `ApiResponse<T>` 래퍼 폐기 + 에러 응답 `{error: {code, message, details}}` 표준화 — **2026-05-13 완료 (PR-2)**. 신규 `ErrorResponse` record + `GlobalExceptionHandler` / `JwtAuthenticationEntryPoint` / `JwtAccessDeniedHandler` 전면 갱신. 4개 공용 코드(`UNAUTHENTICATED`/`FORBIDDEN`/`VALIDATION_FAILED`/`INTERNAL_ERROR`)는 enum override, 도메인 코드는 enum name() 그대로.
- [ ] **A-11.** 헬스체크 `GET /` 추가 (`/health` 유지)
- [x] **A-12.** `ErrorCode` 추가 — **2026-05-16 (PR-5)**: `ALREADY_LIKED`/`NOT_LIKED`/`ALREADY_SCRAPPED`/`NOT_SCRAPPED` (409) 추가 완료. `RECIPE_VIDEO_URL_NOT_FOUND` 는 PR-6 (admin video_url) 에서 추가 예정.
- [x] **A-13.** SecurityConfig 매트릭스 갱신 — **2026-05-13 완료 (PR-1)**. `/api/v1/admin/**` hasRole('ADMIN'), `/api/v1/recipes**` GET permitAll, `/api/v1/recipes/my` 인증 필수, 그 외 authenticated.
- [ ] **A-14.** 통합 테스트 갱신 (path 변경 + 응답 모양 변경 반영) + 신규 endpoint 테스트 추가 — **부분 완료**: PR-1 (path) + PR-2 (응답 모양) 갱신됨. 신규 endpoint 테스트는 각 PR 진행 시 동시에 추가.

#### B. naengo-front 측 (전달 필요)

- [ ] **B-1.** `NAENGO_API_BASE` 를 API 서버로 전환 (인증/레시피/사용자/스크랩/제출/프로필). 채팅 SSE 는 AI 서버 유지.
- [ ] **B-2.** 좋아요/스크랩 호출을 POST/DELETE 분리로 변경 (현재 토글 가정 코드 `_toggleRecipeReaction` 분기 필요)
- [ ] **B-3.** 레시피 목록 커서 페이지네이션 — `getRecipes` 에 `cursor` 인자 추가
- [ ] **B-4.** `auth_service.dart` 에 API 서버 `/api/v1/auth/{signup,login,social/*}` 호출 신설 (현재 AI 서버는 인증 부재 → 우리 API 서버가 인증의 source of truth)
- [ ] **B-5.** JWT 토큰 저장 / Authorization 헤더 첨부 / 401 시 로그인 화면 리디렉트
- [ ] **B-6.** 응답 래핑 폐기 후 `data` 가 raw 가 됨에 따라 (A-10 적용 후) 디시리얼라이저 검증

#### C. naengo-admin 측 (전달 필요)

- [ ] **C-1.** `VITE_API_URL` 을 API 서버 도메인으로 변경 (`baseURL = ${...}/api/v1`)
- [ ] **C-2.** 채팅 ts 의 `BASE_URL` 만 AI 서버 유지 (SSE 가져오기 위해)
- [ ] **C-3.** 인증 헤더 — 관리자 로그인 도입 시 JWT 첨부 (axios interceptor)
- [ ] **C-4.** PATCH approve/reject 의 현 우리 `POST /approve` 호환을 즉각 끊을 수 없으므로 A-9 와 동시 배포 (양쪽 endpoint 일시 공존 권장)

#### D. naengo-ai 측 (협의 필요)

- [ ] **D-1.** SQLAlchemy `Integer` → `BigInteger` 변경 요청 (PK 타입 정합)
- [ ] **D-2.** AI 서버 인증 도입 — 현재 `TEMP_USER_ID` 사용. JWT secret 공유 후 `Authorization: Bearer` 검증 → `sub` 에서 user_id 추출 (Phase 0-1 합의 미완)
- [ ] **D-3.** SSE 응답에서 `chat_messages.recipe_ids` 만 저장하던 정책 그대로 유지 (api-server 가 read 시 enrichment)
- [ ] **D-4.** RAG 인덱스 빌드 시 `is_active=true` 만 포함 (PENDING 누락 정책 합의 — ai-server-contract §4-4)
- [ ] **D-5.** `recipes.embedding` 채우는 메커니즘 합의 — 옵션 B (AI cron) 잠정 (ai-server-contract §4-5)
- [~] **D-6.** 채팅방 soft delete 쓰기 권한 — **api-server 단독 채택·적용**(PR-7: `DELETE /chat/rooms/{id}` + 탈퇴 시 `is_active=false`). **잔여 합의**: 탈퇴 사용자 `chat_messages` PII hard delete/스크럽 → 안건서 [`docs/changes/chat-withdrawal-ai-agreement.md`](../changes/chat-withdrawal-ai-agreement.md) (옵션 A 권고, AI 팀 회신 대기).
- [ ] **D-7.** `naengo-ai/db/schema.sql` 의 정규화 설계 채택 시점 공유 — 채택되면 V5 마이그레이션 필요

#### E. 공유 자원 / 환경

- [ ] **E-1.** RDS 엔드포인트 합의 (api-server + ai 가 같은 DB 접속). 현재 로컬 docker-compose 각각 별도 → 통합 docker-compose 또는 RDS staging 환경 마련
- [ ] **E-2.** JWT secret 공유 (`JWT_SECRET` env) — 양 서버 동시 배포 시 무중단 회전 절차
- [ ] **E-3.** S3 버킷 + IAM (이미지 업로드 presigned URL). 현 미구현 → Step 2-4b 보류 상태
- [ ] **E-4.** CORS — api-server `application.yml` 의 `cors.allowed-origins` 에 front (로컬 + 배포) / admin (로컬 + 배포 vercel) 도메인 모두 명시
- [ ] **E-5.** OpenAPI 문서화 — api-server 가 `/api/v1/openapi.json` 노출 (springdoc-openapi). api-3.json 과 cross-check 자동화

---

## 6. 권고 PR 순서

1. ~~**PR-1 (P0)**: URL prefix `/api/v1` 전면 적용 + `SecurityConfig` 매트릭스 정리 + 통합 테스트 path 갱신.~~ — **2026-05-13 완료**. 19건 통합 테스트 PASS.
2. ~~**PR-2 (P0)**: `ApiResponse<T>` 래퍼 폐기 + 표준 에러 응답 도입.~~ — **2026-05-13 완료**. 19건 통합 테스트 PASS.
3. ~~**PR-3 (P0)**: 레시피 목록 커서 페이지네이션 + `is_liked`/`is_scrapped` 포함 + single endpoint 응답 통합 (`RecipeListItemResponse`).~~ — **2026-05-16 완료**. 19건 통합 테스트 PASS.
4. ~~**PR-4 (P0)**: pending-recipes 분리 (`/api/v1/pending-recipes`) + soft delete.~~ — **2026-05-16 완료**. 20건 통합 테스트 PASS.
5. ~~**PR-5 (P0)**: 좋아요/스크랩 POST/DELETE 분리 + 409 + `ALREADY_*`/`NOT_*` ErrorCode.~~ — **2026-05-16 완료**. 21건 통합 테스트 PASS.
6. ~~**PR-6 (P0)**: admin `PATCH` 통합 + `GET /admin/recipes?video_url=` + 400 응답.~~ — **2026-05-16 완료**. 22건 통합 테스트 PASS.
7. ~~**PR-7 (P1)**: 프로필 endpoint 응답 모양 정합 (`user_input` 만) + 채팅 path 정합 + 채팅방 soft delete.~~ — **2026-05-16 완료**. 24건 통합 테스트 PASS.
8. **PR-8 (P1)**: AI 팀 협의 항목 반영 (D-1~D-7) + 헬스체크 `GET /` (A-11). ← **다음 (협의 필요)**
   - ~~요청·응답 DTO snake_case 전역 정합~~ — **2026-05-17 선반영 완료** (User 인증 API 공유 작업과 함께)

> **진행 로그**
> - 2026-05-13 PR-0 (사전): docs 정리 (archive 이동 + README 통합), 구글 소셜 전면 제거 (`user-domain-todo.md §1`).
> - 2026-05-13 PR-1 완료: `/api/v1` prefix (A-1, A-13).
> - 2026-05-13 PR-2 완료: `ErrorResponse` 표준화 (A-10).
> - 2026-05-16 PR-3 완료: 레시피 목록/단건 커서 + engagement (A-4, A-6 부분). `RecipeDetailResponse` 폐기, `RecipeListItemResponse`/`RecipeListResponse` api-3.json 정합 snake_case.
> - 2026-05-16 PR-4 완료: pending-recipes 분리 (A-2, A-3). `PendingRecipeController`/`PendingRecipeService`/`PendingRecipeResponse` 신설, `RecipeController`/`RecipeService` 에서 pending 제거, soft delete. 통합 테스트 20건 PASS.
> - 2026-05-16 PR-5 완료: 좋아요/스크랩 POST/DELETE 분리 (A-5, A-6, A-12). `RecipeStatsResponse` 신설, `LikeToggleResponse`/`ScrapToggleResponse` 폐기, 409 `ALREADY_*`/`NOT_*` 4종, scrap list 경로 `/api/v1/users/me/scraps` 이동. 통합 테스트 21건 PASS.
> - 2026-05-16 PR-6 완료: admin 통합 PATCH (A-9). `PendingRecipeAdminUpdateRequest` 신설, POST `/approve`·`/reject` 폐기, `GET /api/v1/admin/recipes?video_url=` 신설, 승인 미충족 422→400. `RecipeApproval/Rejection` DTO 4종 폐기. 통합 테스트 22건 PASS.
> - 2026-05-16 PR-7 완료: 프로필/채팅 정합 (A-7, A-8). `/profile` user_input 전용 + `/preferences` 확장 분리, 채팅 plain array + snake_case + `/messages` suffix 제거 + soft DELETE. `ChatRoomListResponse`/`ChatMessageListResponse` 폐기. 통합 테스트 24건 PASS (신규 `ProfileChatIntegrationTest` 2건).
> - 2026-05-17 User 인증 API 마무리: 회원가입/카카오 신규가입 시 `user_profiles` 자동 생성, `SocialAuthIntegrationTest` 5건 신설. 통합 테스트 29건 PASS. 팀 공유 문서 [`auth-user-api.md`](../auth-user-api.md) 발행.
> - 2026-05-17 전역 snake_case 적용: `spring.jackson.property-naming-strategy=SNAKE_CASE`. `AuthResponse`/`UserMeResponse`/`UserPreferences*`/요청 DTO 등 camelCase 잔존분 일괄 snake_case. 명시적 `@JsonProperty` 는 우선 유지(`id` 등). 통합 테스트 29건 PASS.
> - 2026-05-17 탈퇴 시 chat 자원 처리(옵션1 폴백): `withdraw` 에서 본인 `chat_rooms` soft delete(`is_active=false`, `ChatRoomRepository.deactivateAllByUserId`). 무충돌(PR-7/D-6 권한 내), AI 합의 없이 적용. 메시지 hard delete/PII 스크럽은 AI 합의 후 승격(D-6 / user-domain-todo §6-2). 통합 테스트 30건 PASS.

각 PR 별로 `docs/changes/SPEC-...-CL01.md` change-log 동반.

---

## 7. 후속 작업 체크리스트

- [ ] 본 문서를 AI 서버 팀과 공유 → §5-D 합의
- [ ] 본 문서를 front / admin 담당자에게 공유 → §5-B / §5-C 작업 분담
- [ ] `api-server-tasks.md` §1 인벤토리 갱신 (현행 상태 vs 본 문서 카탈로그)
- [ ] PR-1 부터 순서대로 발행
- [ ] api-3.json 갱신 시 본 문서 §2 / §3 cross-check
