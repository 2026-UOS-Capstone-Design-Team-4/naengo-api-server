# 변경점 분석 — naengo-ai / naengo-admin 신규 스냅샷 (2026-05-19)

> 입력: 팀원이 가져온 `naengo-ai`(신규 스냅샷, `naengo-ai-main/`) + `naengo-admin`(전 파일 mtime 2026-05-18, git 미적용 플랫 스냅샷) 디렉토리 검사.
> 한 줄 결론: **naengo-admin 은 더 이상 api-server(:8080)를 호출하지 않는다 — 전부 naengo-ai(:8000) 직결.** 우리 런타임 영향 0. 단 (1) `users` 소유권 합의 회귀, (2) admin 도메인 사실상 AI 이관, (3) 레시피 정규화 진행 가속 — 3건 합의 안건.

---

## 1. naengo-admin — **api-server 미호출 (AI 직결로 전환)**

근거(모두 실코드):

| 위치 | 내용 |
|---|---|
| `src/api/client.ts` | `baseURL = (VITE_API_URL ?? 'http://localhost:8000') + '/api/v1'` — 기본값 **:8000 (AI 포트)**. api-server 는 :8080. |
| `README.md` | `VITE_API_URL=http://localhost:8000` 명시 |
| `vercel.json` | 운영 rewrite `/api/:path*` → **`http://43.201.62.254:8000`** (AI 서버 IP:8000) |
| `src/api/chat.ts` | `BASE_URL`/`ADMIN_BASE_URL` 도 동일하게 :8000 직결 |
| `src/**` grep | `Authorization` / `Bearer` / `withCredentials` / `cookie` / `token` **0건** — 무인증 (AI 의 TEMP_USER_ID 모델과 정합) |

→ admin 은 **AI 서버 전용 내부 운영·테스트 클라이언트**로 재정의됨. 우리 인증 보호된 admin 엔드포인트와 무관(호출 자체가 없음).

admin 이 (AI 에) 호출하는 경로:
- `GET /api/v1/admin/recipes` (sort/cursor/limit/is_active/source_site/author_type/visibility/difficulty/q), `GET /api/v1/admin/recipes/{id}`
- `GET /api/v1/admin/pending-recipes`, `PATCH /api/v1/admin/pending-recipes/{id}`, `DELETE /api/v1/admin/pending-recipes/{id}`
- `GET|POST /api/v1/chat/rooms`, `GET|POST /api/v1/chat/rooms/{id}` (SSE), `DELETE /api/v1/admin/chat-rooms/{id}`
- `GET /api/v1/recipes`

admin TS 타입(`adminRecipes.ts`/`RecipeCard.tsx`)이 **신규 정규화 AI 스키마의 실측 증거**:
- `recipe_id`, `cooking_time_minutes`, `kcal_per_serving`, `visibility`, `author_type(ADMIN/USER/SOURCE)`, `source_site/source_dataset_id/source_dataset_name`
- 정규화 배열: `ingredients[]`(`ingredient_id`), `steps[]`(`step_id`), `labels[]`(`label_id`,`label_type`), `media[]`(`media_id`,`is_primary`), `nutrition`, `classification`
- 플래그: `has_nutrition` / `has_classification` / `has_embedding`
- pending: `pending_recipe_id` / `submission_text` / `draft_payload` / `ai_suggested_patch` / `validation_errors[]` / `import_status(NOT_IMPORTED/IMPORTED/FAILED)` / `imported_recipe_id`
- → 우리 비정규화 `Recipe`/`PendingRecipe` 와 구조 자체가 다름. **admin 이 우리 쪽을 가리키면 즉시 깨짐 — 하지만 가리키지 않으므로 무영향.**

## 2. naengo-ai 신규 스냅샷 (`naengo-ai-main/`)

- `db/schema.sql`(24KB) **정규화 추가 진화**: `pending_recipes`(submission_text/draft_payload/ai_suggested_patch), `recipes`(cooking_time_minutes/kcal_per_serving/classification_status), 신규 `recipe_nutrition`·`recipe_source_quality_scores`, `recipe_sources`(PUBLIC_DATA/dataset 필드). 2026-05-17 delta §3 의 정규화 방향이 **더 진행됨**.
- `app/models/user.py`: `UserProfile.preferred_cooking_time_minutes` + 호환 `@property preferred_cooking_time`. 우리 컬럼명은 `preferred_cooking_time`.
- `db/seeds/users.sql`: user_id 1/2/3 시드, `password_hash='local-dev-password-hash'`(bcrypt 아님) — **로컬 개발 전용 시드**, 운영 무관.
- `app/api/v1/endpoints/users.py`: `GET|PATCH /me`, `GET /me/profile`, **`POST /me/profile` = AI 정규화 후 append**, **`DELETE /me/profile` = user_inputs 제거**, `GET /me/scraps`. 에러코드 `RESOURCE_NOT_FOUND`/`CONFLICT`/`PROFILE_INPUT_NOT_USER_INFO`/`UPSTREAM_ERROR`.
  - → 우리 `PUT/PATCH /me/profile`(전체 교체) 와 **의미 상이**(append/remove vs replace). 동일 경로·다른 의미.
- `app/main.py`: 여전히 version `0.1.0`, `TEMP_USER_ID` 무인증 유지. AI HTTP 계약(api-3=api-4) 자체는 불변.
- 신규 admin 라우터: `/admin/pending-recipes`, `/admin/recipes`, `/admin/chat-rooms` (admin 클라이언트가 쓰는 그 경로).

### ⚠️ 소유권 회귀 — `users` 테이블
2026-05-17 delta §3 에서 `DBv3.sql` 에 있던 주석 **`// users는 우리`(users = api-server 소유 명시)가 신규 `schema.sql` 에서 삭제됨.** 동시에 신규 `schema.sql` 의 `users` 는:
- `SERIAL/INTEGER` PK (우리 `BIGSERIAL`)
- `email` / `password_hash` **NOT NULL** (우리 V2: 소셜용 nullable)
- `provider` / `provider_id` / `deleted_at` **없음** (우리 V2/V3 핵심)

→ 이 `schema.sql` 을 공유 DB 에 적용하면 우리 소셜 로그인(V2)·탈퇴 익명화(V3) 전부 깨지고 `ddl-auto: validate` 부팅 실패. **소유권 합의가 문서상으로 회귀**했으므로 AI 팀과 재확인 필수(에스컬레이션).

## 3. api-server 영향 / 액션

| # | 항목 | 영향 | 조치 |
|---|---|---|---|
| 1 | admin → AI 직결 | 런타임 영향 **0** (admin 이 우리를 호출 안 함) | 무대응. 단 README §1 "클라이언트" 기술 갱신(admin 은 AI 직결로 정정) |
| 2 | admin 도메인 사실상 AI 이관 | 우리 PR-4~7 admin 엔드포인트를 쓰는 웹 클라이언트 없음 (admin 은 AI 사용, Flutter 앱엔 admin 화면 없음) | **합의 안건**: api-server admin 엔드포인트 유지/폐기 결정. **현행 코드는 유지**(사용자 지시), 폐기 단독 금지 |
| 3 | `users` 소유권 주석 삭제 + 비정규화 충돌 | 공유 DB 에 신규 schema.sql 적용 시 우리 부팅·소셜 전부 장애 | **AI 팀에 `users`=api-server 소유 재확인 + schema.sql 단독 미적용 합의** (최우선 에스컬레이션) |
| 4 | 레시피 정규화 가속 | 2026-05-17 delta §3 (A/B) 결정 압박 ↑. 단독 적용 시 양 서버 장애는 동일 | **B 정본 머지 완료 (2026-05-19, 통합테스트 30건 PASS)**. `naengo-api-server-copied` 는 롤백용 백업 스냅샷으로 보존. 설계·deviation: `docs/changes/2026-05-19-option-b-normalization.md` |
| 5 | `/me/profile` 계약 충돌 (front·AI·우리 3자) | **확인 완료** ↓ §4 | front·AI·api-server `/me/profile` 변경 의미 합의 필요 (크로스서비스 안건) |
| 6 | `preferred_cooking_time(_minutes)` 명칭 차 | AI 모델에 호환 @property 존재 → 즉시 장애 아님 | 정규화 채택(옵션 B) 시 함께 정렬 |

## 4. naengo-front `/me/profile` 호출 대상 — 확인 결과

> naengo-front 는 2026-05-13 스냅샷(팀원 신규 작업 아님). 그러나 §3 #5 확인용으로 검사.

- `lib/services/naengo_api_service.dart`: `baseUrl = String.fromEnvironment('NAENGO_API_BASE', defaultValue:'http://43.201.62.254:8000')`. **프로필 포함 전 `/api/v1/...` 가 이 단일 base URL 로 나감** — front 내부에 서버별 분기 없음.
- 기본값 `43.201.62.254:8000` = admin `vercel.json` 운영 rewrite 대상과 동일 host:port. `:8000`=AI(uvicorn), api-server=:8080 → **기본 설정상 front 프로필도 AI 로 향함** (`--dart-define=NAENGO_API_BASE` 로 api-server 덮어쓰기 가능).
- front 프로필 계약: `GET /api/v1/users/me/profile` → `{user_input:[]}` (404→[]) / **`PATCH /api/v1/users/me/profile` body `{user_input:[...]}` = 전체 교체** (`auth_service.dart` `updateUserInput`).

| `/me/profile` | front 호출 | api-server (우리) | naengo-ai 신규 (`users.py` L45~162) |
|---|---|---|---|
| GET | ✅ | ✅ | ✅ |
| 변경 | **PATCH (전체 교체)** | **PATCH (전체 교체)** ✅ 일치 | **PATCH 없음** — POST(append)/DELETE(remove)만 |

- **결론**: front 의 프로필 기능은 **우리 api-server 의 PATCH-교체 계약으로 코딩**됨. 신규 AI 스냅샷엔 `PATCH /me/profile` 자체가 없어, front 가 AI(기본 base URL)를 가리키면 "취향 수정"이 **405 로 깨짐**.
- 해소 경로(택1, 3자 합의 필요): (a) 배포 시 `NAENGO_API_BASE`→api-server, (b) AI 가 `PATCH /me/profile` 추가, (c) front 가 POST/DELETE 의미로 재작성.
- api-server 액션: **없음**(우리 계약이 front 기대와 일치). 합의 안건으로만 등록.

---

> 본 분석은 "파악"만. **코드·스키마 변경 없음** (당시 사용자 지시: 함부로 지우지 말고 유지).
> 선행 분석: [`../archive/changes/2026-05-17-api4-dbv3-delta.md`](../archive/changes/2026-05-17-api4-dbv3-delta.md) (옵션 A 채택으로 archive 이동)

---

## 갱신 (2026-05-21) — 옵션 A 채택 + 운영 배포 성공으로 안건 해소

본 문서의 §3 표 #2~#5 / 부속 미결 안건들이 옵션 A 채택으로 모두 클로즈. 자세히 [`../deploy-status.md §C`](../deploy-status.md):

| 본 문서 안건 | 새 상태 |
|---|---|
| #2 admin 도메인 AI 이관 | 🟢 관망 — admin 이 우리에게 retarget 하면 그때 (현재 영향 0) |
| #3 `users` 소유권 회귀 | 🚫 N/A — DBv5 의 users 에 provider/provider_id 컬럼 자체 없음 |
| #4 레시피 정규화 가속 | ✅ 완료 — DB 팀원 DBv5 적용 + AI 003/004/005 + 우리 옵션 A, 양측 DBv5 위 동작 |
| #5 `/me/profile` 3자 계약 | 🟢 우리 측 작업 0 — 우리 PATCH(교체) 와 AI POST/DELETE 가 둘 다 운영, front 가 선택 |

§4 (naengo-front `/me/profile`) 결론 — 우리 contract = front 기대와 일치 — 그대로 유효.

진실원본: [`../auth-user-api.md`](../auth-user-api.md) + [`2026-05-21-option-a-contract-diff.md`](2026-05-21-option-a-contract-diff.md).
