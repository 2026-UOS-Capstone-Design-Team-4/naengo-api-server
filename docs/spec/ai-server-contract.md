# SPEC-20260502-01: AI 서버 API contract 스냅샷

## 0. 메타

| 항목 | 값 |
|---|---|
| 명세서 ID | `SPEC-20260502-01` |
| 도메인 | (외부 contract — AI 서버) |
| 기능명 | AI 서버 OpenAPI 0.1.0 의 우리 시스템과 닿는 부분 정리 |
| 작성자 | API 서버 담당자 |
| 작성일 | 2026-05-02 |
| 우선순위 | P0 (다른 모든 Step 의 입력) |
| 관련 명세서 | `SPEC-20260422-02~05` (Recipe / Upload), `api-server-tasks.md §1.5` (V4) |
| 원본 | `docs/api-1.json` (AI 서버가 제공한 OpenAPI 3.1.0 dump) |
| 스냅샷 시점 | AI 서버 OpenAPI `info.version = 0.1.0`, 본 문서 작성 시각 기준 |

> ⚠️ **본 문서는 명세 작성용이 아니라 "외부 contract 의 사실관계 캡처"**. 변경되면 새 SPEC 을 발행하지 말고 본 문서를 직접 갱신하고 영향받는 명세에 change-log 를 남긴다.

---

## 1. 목적 (Why)

AI 서버 docs (<http://43.201.62.254:8000/docs>, raw: `docs/api-1.json`) 와 우리 DB / API 의 가정이 어긋나는 지점을 한눈에 보기 위함. V4 마이그레이션 후보(`api-server-tasks.md §1.5`) 와 Step 5/6/7 명세 작성의 입력이 된다.

---

## 2. AI 서버 endpoint 표

| Method | Path | 인증 | 설명 | 요청 | 응답 |
|---|---|---|---|---|---|
| `GET` | `/` | 없음 | 헬스 | — | `{status, message}` |
| `GET` | `/api/v1/chat/rooms` | (없음, `user_id=1` 고정) | 활성 채팅방 목록 (updated_at DESC, `is_active=true`만) | — | `ChatRoomResponse[]` |
| `POST` | `/api/v1/chat/rooms` | 없음 | 새 채팅방 + 첫 메시지 (SSE) | `ChatRequest` | `text/event-stream` (`room`, `message`, `recipes` 이벤트) |
| `GET` | `/api/v1/chat/rooms/{room_id}` | 없음 | 메시지 시간순 목록 | path: `room_id: integer` | `ChatMessageResponse[]` |
| `POST` | `/api/v1/chat/rooms/{room_id}` | 없음 | 기존 채팅방 메시지 (SSE, 직전 10개 컨텍스트) | path: `room_id: integer` + `ChatRequest` | SSE |
| `DELETE` | `/api/v1/chat/rooms/{room_id}` | 없음 | 소프트 삭제 (`is_active=false`) | path: `room_id: integer` | `{message}` |
| `GET` | `/api/v1/recipes?ids=1&ids=2&...` | 없음 | ID 목록으로 레시피 일괄 조회 | query: `ids: integer[]` | `RecipeResponse[]` |
| `GET` | `/api/v1/admin/recipes?video_url=...` | 없음 | `video_url` 로 단건 조회 (등록 전 중복 확인) | query: `video_url: string` | `RecipeResponse` |

> **부재 항목**: `/internal/embed`, 추천 단독 endpoint, 재료 분석 단독 endpoint, 인증 endpoint. 추천은 모두 `POST /api/v1/chat/rooms*` SSE 안에 묶여 있다.

---

## 3. 컴포넌트 스키마 — 요약

### 3.1 `RecipeResponse` (RAG / 채팅 응답에서 사용)
| 필드 | 타입 | 필수 | 비고 |
|---|---|---|---|
| `id` | int | ✓ | `recipes.recipe_id` 와 동일하게 매핑되어야 함 |
| `title` | string | ✓ | |
| `description` | string | ✓ | **현재 우리 스키마에 없음** |
| `ingredients` | `IngredientItem[]` | (default `[]`) | element schema 확장됨 (§3.3) |
| `ingredients_raw` | string | ✓ | **현재 없음** |
| `instructions` | string[] | (default `[]`) | **현재 없음** (우리는 `full_content TEXT` 한 덩어리) |
| `servings` | number(float) | ✓ | **현재 없음** |
| `cooking_time` | int | ✓ | **현재 없음** (단위: 분) |
| `calories` | int \| null | optional | **현재 없음** |
| `difficulty` | enum `easy`/`normal`/`hard` | ✓ | **현재 없음** |
| `category` | string[] | (default `[]`) | **현재 없음** |
| `tags` | string[] | (default `[]`) | **현재 없음** |
| `tips` | string[] | (default `[]`) | **현재 없음** |
| `video_url` | string \| null | optional | **현재 없음** (그러나 `/api/v1/admin/recipes?video_url=` 가 이를 키로 검색하므로 인덱스 필요) |
| `image_url` | string \| null | optional | 우리 `recipes.image_url VARCHAR(512)` 와 일치 |
| `author_type` | enum `ADMIN`/`USER` | ✓ | **우리 `source ('STANDARD'/'USER')` 와 의미 충돌** — §4-1 참조 |

### 3.2 `ChatMessageResponse`
| 필드 | 타입 | 필수 |
|---|---|---|
| `message_id` | int | ✓ |
| `role` | string (`user`/`model`) | ✓ |
| `content` | string | ✓ |
| `recipes` | `RecipeResponse[]` \| null | optional |
| `created_at` | datetime | ✓ |

> 우리 `session_logs` 의 컬럼들(`extracted_ingredients`, `user_feedback`, `recommended_recipe_ids`, `selected_recipe_id`, `chat_messages JSONB`, `status`) 은 AI 응답 어디에도 매핑되지 않음. AI 측은 **per-message 테이블** (가칭 `chat_messages`) 을 쓰는 것으로 보임. §4-3 참조.

### 3.3 `IngredientItem`
| 필드 | 타입 | 필수 |
|---|---|---|
| `name` | string | ✓ |
| `amount` | string | ✓ |
| `unit` | string | ✓ |
| `type` | string | ✓ (메인/부재료 등) |
| `note` | string \| null | optional (default `""`) |

> 우리 `recipes.ingredients JSONB` 의 element 는 `{name, amount}` 만 받게 명세됨(`SPEC-20260422-02 §3.2`). AI 응답을 그대로 저장하려면 element schema 확장 필요.

### 3.4 `ChatRoomResponse`
| 필드 | 타입 |
|---|---|
| `room_id` | **int** |
| `title` | string |
| `created_at` / `updated_at` | datetime |

> 현재 우리 `chat_rooms.room_id` 는 `VARCHAR(100)`. **타입 불일치 → V4 후보**.

### 3.5 `ChatRequest`
| 필드 | 타입 | 필수 |
|---|---|---|
| `prompt` | string | ✓ |
| `image` | string \| null | optional (`data:image/jpeg;base64,...`) |

---

## 4. 우리 DB / 명세 와의 정합 갭

### 4-1. `recipes` — 컬럼 다수 누락 + `author_type` ↔ `source` 의미 충돌
- 누락 컬럼 (RecipeResponse 가 SSE 와 RAG 결과로 그대로 노출하므로 DB 보유 필요): `description`, `ingredients_raw`, `instructions`(text[] 또는 JSONB), `servings`(numeric), `cooking_time`(int), `calories`(int null), `difficulty`(enum/check), `category`(text[]), `tags`(text[]), `tips`(text[]), `video_url`(varchar) + index
- `IngredientItem` 의 `unit`/`type`/`note` 도 누락 → JSONB element schema 확장
- `source` 와 `author_type`:
  - 우리 `source`: `STANDARD`(운영자 시드) / `USER`(사용자 업로드)
  - AI 의 `author_type`: `ADMIN`(관리자가 등록) / `USER`(사용자 업로드)
  - 의미가 거의 같지만 **이름·값이 다름**. V4 에서 정합 결정 필요. 후보:
    - 옵션 (a) `source` 그대로 두고 응답 매핑 시 `STANDARD → ADMIN` 으로 변환
    - 옵션 (b) `recipes` 에 `author_type VARCHAR(20)` 컬럼을 새로 두고 점진적으로 `source` 를 deprecate
    - 옵션 (c) `source` 를 rename + check 제약 변경 (가장 큰 변경)
  - **추천**: 옵션 (a) 가 가장 작은 변경. V4 에 컬럼 변경 없이 매핑 레이어로 흡수. 단, AI 서버가 우리 DB 에 직접 INSERT/UPDATE 하는 경우는 대응 안됨 → AI 측이 우리 DB 를 직접 쓰는지 합의가 필요.

### 4-2. `chat_rooms.room_id` 타입 불일치
- 현재: `VARCHAR(100) PRIMARY KEY` (UUID 를 AI 서버가 생성한다고 가정)
- AI 응답: `room_id: integer`
- AI 서버는 자체 BIGSERIAL/INTEGER PK 를 쓰는 것으로 보임. 우리 스키마가 이를 따라가야 한다면:
  - `chat_rooms.room_id` → `BIGSERIAL PRIMARY KEY`
  - 의존하는 `session_logs.room_id` 도 BIGINT 로 함께 변경
- **단, 데이터 마이그레이션은 V4 가 신규 환경 기준이므로 0건 → 1줄 ALTER 로 충분**. 운영 환경에 데이터가 이미 있으면 별도 백필 필요 (현재 운영 미배포 상태이므로 부담 없음).

### 4-3. `session_logs` 가 AI 의 per-message 모델과 불일치
AI 의 `ChatMessageResponse` 는 명백히 다음 구조의 테이블 위에 있음:
```
chat_messages
  message_id   BIGSERIAL PK
  room_id      BIGINT FK → chat_rooms
  role         VARCHAR(10) CHECK (role IN ('user','model'))
  content      TEXT
  recipes      JSONB  -- RecipeResponse[] 캐시 또는 recipe_ids BIGINT[]
  created_at   TIMESTAMPTZ
```
- 우리 `session_logs` 의 `extracted_ingredients` / `user_feedback` / `recommended_recipe_ids` / `selected_recipe_id` / `chat_messages JSONB` / `status` 컬럼들은 **현재 AI API 에 등장하지 않음**. AI 측이 내부적으로 더 가질 가능성이 있으나 우리가 read 하는 구조는 위 6컬럼.
- 결정 필요: V4 에서
  - 옵션 (A) `session_logs` 를 폐기하고 `chat_messages` 테이블을 새로 만든다 (AI 서버가 실제 쓰는 테이블 이름과 합의 필요)
  - 옵션 (B) `session_logs` 는 그대로 두고, AI 가 별도로 `chat_messages` 를 만들도록 권한 위임 (혼재)
  - 옵션 (C) AI 와 합의 후 두 모델 중 하나로 수렴
- **현 시점 추천**: AI 서버 팀과 합의 전이므로 **V4 에는 포함하지 않고 §1.5 후보로만 유지**. 합의 후 V5/V6 로 분리 적용.

### 4-4. `recipes.status` (PENDING/APPROVED/REJECTED) 가 AI 측 contract 에 부재
- AI 의 RAG 검색 / `GET /api/v1/recipes` / `GET /api/v1/admin/recipes` 어디에도 `status` 필터 없음.
- 우리 `recipes.status` 는 우리 "노출 가시성" 정책 (PENDING 은 일반 목록에서 제외, ADMIN 만 승인) 에 종속. AI 가 이를 무시하고 RAG 검색 결과로 PENDING 을 넘길 가능성.
- 결정 필요:
  - 옵션 (A) AI 서버가 RAG 인덱스 빌드 시 `status='APPROVED' AND embedding IS NOT NULL` 만 포함하도록 합의
  - 옵션 (B) API 서버가 AI 응답을 받은 뒤 `status` 필터를 한 번 더 적용 (소프트 정합)
  - 옵션 (C) `status` 개념 자체를 폐기하고 "전부 노출" 로 정책 변경
- **V4 범위 외** — 합의 사항. `api-server-tasks.md §5 보류 항목` 에 등록.

### 4-5. `/internal/embed` endpoint 부재
- 현재 AI API 에 임베딩 단독 endpoint 없음. 모든 추천은 SSE 채팅 흐름 안.
- 우리 Phase 0-3 의 "관리자 승인 → AI `/internal/embed` 호출 → `recipes.embedding` UPDATE" 흐름은 **현 시점 동작 불가**.
- 옵션:
  - 옵션 (A) AI 서버가 `/internal/embed` 신설하기 전까지 `recipes.embedding` 을 NULL 로 두고, AI 서버가 RAG 인덱스를 빌드할 때 자체적으로 channel 외 임베딩 (예: 주기 cron 으로 우리 DB 의 `embedding IS NULL AND status='APPROVED'` 를 찾아 채움)
  - 옵션 (B) `embedding` 컬럼의 책임을 AI 서버에 완전 이양 — `api-server-tasks.md §0 테이블별 책임` 표는 이미 그렇게 되어 있음. 즉 AI 가 알아서 채우는 게 원안.
- **결정**: 원안(옵션 B)이 유지된다면 우리 코드에는 임베딩 호출 흔적 없이 그저 NULL 로 두면 됨. Phase 0-3 의 "API 서버 트랜잭션 커밋 후 AI 호출" 문구를 **"AI 서버가 자체 스케줄로 채움. API 서버는 임베딩에 손대지 않음"** 으로 수정 필요.

### 4-6. 인증 미구현
- AI docs `info.description` 명시: *"현재 사용자 인증이 구현되어 있지 않습니다. 모든 API는 임시로 user_id = 1 사용자를 기준으로 동작합니다."*
- Phase 0-1 (JWT secret 공유) 은 AI 서버 미구현 → 양 서버 토큰 통일은 차후. 현재 AI 서버를 직접 호출할 일이 있다면 user_id 가 무의미.
- 영향:
  - **API 서버가 AI 서버를 호출할 일이 사실상 없음** (관리자 승인 → 임베딩 호출 흐름이 위 §4-5 로 인해 사라졌으므로)
  - 프론트가 AI 서버를 직접 호출하는 SSE 채팅 흐름이 메인. user_id 는 추후 인증 도입 시 실 사용자로 치환되어야 함

---

## 5. 결론 — V4 의 실제 적용 상태

> ⚠️ **2026-05-04 갱신 — §5.A 의 통합 이슈 12건 모두 해결**.
> 사용자 결정으로 **V4 가 V1 자리를 완전히 흡수** (옵션 (b) 변형 채택) → 구 V1 / 구 V4 모두 폐기, 새 V1 이 구 V4 의 설계를 흡수하면서 12건의 통합 이슈를 모두 해결. 자세한 결정·결과는 [`docs/changes/V4-integration-resolved.md`](../changes/V4-integration-resolved.md). 본 §5 의 옵션 (a)/(b)/(c) 권고는 history 보존 목적으로 두되 **현행은 옵션 (b) 변형이 적용 완료된 상태**.

> **2026-05-02 원본 메모**: 본 §5 의 1차 권고(점진적 ALTER) 와는 달리, 사용자가 **`V4__fixed_schema.sql` 을 fresh-DB 스타일 `CREATE TABLE` 모음** 으로 작성·푸시했음 (commit `48204b4`). 따라서 실 적용 상태와 본 갭분석의 권고가 일치하지 않으며, **§5.A 통합 이슈** 가 추가로 발생 → **위 2026-05-04 갱신으로 해결**.

### 5.A 구 `V4__fixed_schema.sql` 의 통합 이슈 (history — 모두 해결됨)

`src/main/resources/db/migration/V4__fixed_schema.sql` 분석 결과:

| # | 이슈 | 심각도 | 설명 |
|---|---|---|---|
| 1 | Flyway 적용 실패 | 🔴 P0 | `CREATE TABLE Users (...)` 등이 `IF NOT EXISTS`/`DROP` 없이 작성됨. V1~V3 가 이미 `users`/`recipes`/... 를 만들어두기 때문에, 같은 DB 에서 V4 가 실행되면 `relation "users" already exists` 로 즉시 실패. 신규 환경(`docker compose down -v`) 에서도 V1→V2→V3 가 먼저 적용되므로 동일하게 실패. |
| 2 | V3 의 `users.deleted_at` 미존재 | 🔴 P0 | V4 의 `Users` 정의에 `deleted_at` 컬럼이 없음. 회원 탈퇴 익명화 기능(`api-server-tasks.md §5`)의 전제가 사라짐. |
| 3 | V1 의 `fridge` 테이블 미존재 | 🟠 P1 | V4 가 `fridge` 를 재정의하지 않음. Step 5 (Fridge) 명세의 전제가 사라짐. |
| 4 | `session_logs` 미존재 + `Chat_Messages` 신설 | 🟡 P2 | AI 측 contract 와는 정합 (per-message 모델). 단, V1 의 `session_logs` 가 그대로 남아 좀비 테이블이 됨. |
| 5 | 타입 narrowing: `BIGSERIAL` → `SERIAL` | 🔴 P0 | `Users.user_id`, `Recipes.recipe_id`, `Chat_Rooms.room_id` 등이 `INTEGER` 가 됨. JPA 엔티티들이 `Long` 으로 매핑되어 있어(`User.userId`, `Recipe.recipeId`) Hibernate `validate` 모드 실패. |
| 6 | `Recipe` 엔티티-스키마 드리프트 | 🔴 P0 | `Recipe` 엔티티는 `full_content TEXT`, `source` enum, `status` enum 을 가짐. V4 의 `Recipes` 테이블은 `description TEXT`, `content TEXT`, `author_type` enum 을 가지며 `status` 컬럼 없음 (별도 `Pending_Recipes` 테이블로 분리). 부트 시 validate 실패 + Recipe 도메인 코드 전면 재작성 필요. |
| 7 | `User.preferences JSONB` 누락 | 🔴 P0 | V4 가 `User_Profiles` 테이블로 분리. `User` 엔티티의 `preferences` 매핑이 깨짐. |
| 8 | `Pending_Recipes` 분리 설계 | 🟡 P2 | 사용자 업로드 → `Pending_Recipes`, 승인 시 `Recipes` 로 이동. 현재 `RecipeService.create()` (대상: `recipes`) 와 충돌. 의도적 설계 변경이라면 코드 전면 개편 필요. |
| 9 | DB 트리거로 `Recipe_Stats` 자동 증감 | 🟠 P1 | `trigger_likes_count`, `trigger_scrap_count` 가 `Likes`/`Scraps` INSERT/DELETE 시 `Recipe_Stats` 카운터를 변경. 그런데 `api-server-tasks.md §6 Step 3-3` 은 애플리케이션 트랜잭션에서 카운터를 직접 증감하기로 결정. 둘 다 작동하면 **카운트 두 배 증가**. 정책 통일 필요. |
| 10 | `Chat_Messages.recipe_ids JSONB` vs AI 응답 `recipes RecipeResponse[]` | 🟡 P2 | AI `ChatMessageResponse.recipes` 는 RecipeResponse[] 를 통째로 반환. 우리 V4 는 ID 목록만 저장. 조회 시 매번 `Recipes` 조인 필요 — 무방하나 명세 정합성 검토. |
| 11 | `recipes.embedding` 책임 | 🟢 OK | V4 가 `Recipes.embedding VECTOR(1536)` 보유. AI 서버가 채우는 `Phase 0-3` 옵션 (B) 와 정합. |
| 12 | `recipes.is_active` (V4) vs `status` | 🟡 P2 | V4 `Recipes.is_active BOOLEAN` 으로 노출/숨김 토글. 기존 `status` (PENDING/APPROVED/REJECTED) 개념은 `Pending_Recipes` 로 이전. **노출 정책이 boolean 으로 단순화** 되었음. |

### 5.B 권고 — 어떻게 풀 것인가 (사용자 결정 필요)

**옵션 (a) — V4 를 ALTER 기반으로 재작성 (권장도: 高)**
- 현 `V4__fixed_schema.sql` 폐기 (또는 git rm) → ALTER TABLE / DROP TABLE / CREATE TABLE 새 테이블 형식의 마이그레이션 새로 작성
- V1+V2+V3 적용 후 V4 가 ALTER 로 patch → 신규/기존 환경 모두 안전
- 엔티티 재작성 범위는 그대로지만 운영 데이터 보존 가능
- 이슈 #1, #2, #3 자동 해결

**옵션 (b) — V1/V2/V3 폐기 + V4 가 유일 마이그레이션 (권장도: 中)**
- 현 V1/V2/V3 파일 삭제, `V4__fixed_schema.sql` 을 `V1__init.sql` 로 이름 변경 (또는 V4 그대로 두고 V1/V2/V3 만 삭제)
- 단, **`flyway_schema_history` 가 V1/V2/V3 적용 기록을 들고 있는 환경에서는 `MissingMigration` 으로 기동 실패** → `flyway clean` 또는 history 수동 삭제 필요
- 운영 미배포 / dev DB 만 있는 현 시점에서는 가능. 하지만 협업자별 로컬 DB 도 모두 wipe 해야 함
- 이슈 #1~#3 자동 해결, 협업자에게 수동 작업 부담 발생

**옵션 (c) — 현 V4 에 `DROP TABLE IF EXISTS ... CASCADE` 프리픽스 추가**
- 위에 모두 `DROP TABLE IF EXISTS Users CASCADE; CREATE TABLE Users (...)` 식으로 보강
- 이슈 #1 해결, V3 deleted_at / V1 fridge 는 V4 에서 직접 추가해야 함 (이슈 #2, #3)
- 트레이드오프: 운영 데이터 wipe. dev/MVP 단계 한정

### 5.C 어느 옵션이든 동반되는 코드 작업

V4 의 schema 의도(=AI contract 정합) 를 받아들이는 한, 다음은 옵션 무관 필수:
- `User` 엔티티: `preferences` 제거, `is_active` 추가 → 새 `UserProfile` 엔티티 신설
- `Recipe` 엔티티: `fullContent` → 삭제(`description`+`content` 로 분리), `source` → 삭제(`authorType` 신설), `status` → 삭제(`isActive` 로 대체), 새 11개 필드 추가
- 새 엔티티: `PendingRecipe` (현 사용자 업로드 흐름의 새 대상), `ChatMessage`
- `RecipeService.create()` 타깃을 `recipes` → `pending_recipes` 로 변경
- 관리자 승인 흐름: `UPDATE recipes SET status='APPROVED'` → `INSERT INTO recipes ... + DELETE FROM pending_recipes`
- `RecipeStatus`/`RecipeSource` enum 폐기, `RecipeAuthorType` 신설
- Like/Scrap 트랜잭션의 카운터 증감 코드 **삭제** (V4 트리거가 처리) — 또는 트리거 제거 + 코드 유지 (이슈 #9)
- `SPEC-20260422-02/03/04` 명세 v2 발행 (이미 SPEC-20260422-02-CL01 에 메모됨)

### 5.D 본 갭 분석의 §3.1 표 갱신 (V4 이후)

V4 가 채택한 컬럼명/타입에 맞춰 §3.1 표를 갱신하면 좋다 (post-V4 정합 확인 시점에). 현재는 "AI contract 가 요구하는 모양" 위주로 적혀 있어 V4 와 거의 일치하지만, V4 는 추가로 `is_active`, `content` (자유 형식) 를 가짐.

---

## 6. 후속 작업 체크리스트

- [ ] 본 문서를 AI 서버 팀과 공유 → §4-2 / §4-3 / §4-4 / §4-5 합의
- [ ] V4 마이그레이션 `V4__align_recipe_with_ai_contract.sql` 작성 (§5 "V4 에 포함" 항목)
- [ ] `recipes` 엔티티 / DTO / 매퍼 갱신 — 새 컬럼 노출, `author_type` 매핑
- [ ] `SPEC-20260422-02` Recipe 작성 명세에 새 필드 반영 (Body 가 받을 필드 결정)
- [ ] `SPEC-20260422-03` Recipe 조회 명세의 응답 필드 갱신 (AI 응답과 같은 형태로 노출 결정 시)
- [ ] `api-server-tasks.md §0` 테이블별 책임 표 갱신 — 임베딩이 정말 AI 단독 작성이라면 명시 강화
- [ ] `api-server-tasks.md §5` 에 위 미합의 항목 등록

---

## 7. 변경 추적

본 문서가 갱신되면 영향받은 명세별 `change-log` 를 발행. 변경 시 `0. 메타` 의 "스냅샷 시점" 도 함께 갱신.
