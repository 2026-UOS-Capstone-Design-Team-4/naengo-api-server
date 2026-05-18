# V4 통합 완료 — V1 폐기 + 구 V4 가 V1 자리 흡수

## 0. 메타

| 항목 | 값 |
|---|---|
| 변경 이력 ID | `V4-integration-resolved` (V4-integration-issues 의 후속) |
| 대상 | `src/main/resources/db/migration/*.sql` + Recipe / User 도메인 코드 전체 |
| 변경 종류 | 정책 결정 + 마이그레이션 + 코드 동시 갱신 |
| 결정 출처 | 사용자 지시 ("V1 폐기, V4 가 최우선순위, 소스코드도 이에 맞게") |
| 작성자 | API 서버 담당자 |
| 작성일 | 2026-05-02 |
| 관련 문서 | [`api-server-tasks.md §1.5`](../api-server-tasks.md), [`spec/ai-server-contract.md`](../spec/ai-server-contract.md), [`changes/V4-integration-issues.md`](V4-integration-issues.md) |

---

## 1. 무엇을 결정했나 (What)

[`V4-integration-issues.md §3`](V4-integration-issues.md) 의 옵션 (b) 변형:
- 구 `V1__init.sql` 폐기
- 구 `V4__fixed_schema.sql` 폐기
- **새 V1__init.sql** 이 구 V4 의 설계를 흡수 + 구 V1 의 일부 (fridge) 보존 + Flyway/JPA 호환성 fix
- V2, V3 는 그대로 보존

---

## 2. V1 (새) 의 fixes (구 V4 대비)

| # | 항목 | 변경 |
|---|---|---|
| 1 | `BIGSERIAL` / `BIGINT` 통일 | `SERIAL` → `BIGSERIAL`, FK INTEGER → BIGINT (JPA `Long` 정합) |
| 2 | `users.password_hash` | NOT NULL → nullable (V2 가 nullable 화 하던 것을 V1 단계에서 처리) |
| 3 | `uq_provider_provider_id` UNIQUE 제약 | V1 에서 제거 (V2 가 추가하므로 중복 정의 충돌 회피) |
| 4 | `users.deleted_at` | V3 가 추가하도록 그대로 두고, V1 에는 두지 않음 |
| 5 | `fridge` 테이블 | 구 V4 에서 누락됐던 것을 V1 에 복원 |
| 6 | 인덱스 보강 | `idx_recipes_video_url` (PARTIAL), `idx_pending_recipes_user_id`, `idx_pending_recipes_status`, `idx_chat_messages_room_id`, 기타 |
| 7 | `trigger_recipe_stats_create` | recipes INSERT 시 recipe_stats(0,0) row 자동 생성 (구 V4 에는 없던 트리거. likes/scraps 트리거가 UPDATE 만 하므로 stats row 가 없으면 카운터가 묵묵히 0 으로 남는 버그를 방지) |

---

## 3. 코드 변경 요약

본 PR 동시 적용. 빌드 (`./gradlew build -x test`) PASS.

### 엔티티
- `User.java`: `preferences Map<String,Object>` 제거 → `isActive boolean` 추가, `deletedAt ZonedDateTime` 추가, `updatePreferences()` 제거
- `Recipe.java`: 전면 재작성 — 폐기 (`fullContent`, `source`, `status`) / 신설 (`description`, `ingredientsRaw`, `instructions List<String>`, `servings BigDecimal`, `cookingTime Integer`, `calories Integer`, `difficulty String`, `category List<String>`, `tags List<String>`, `tips List<String>`, `content String`, `videoUrl String`, `isActive boolean`, `authorType RecipeAuthorType`)
- `RecipeStats.java`: `incrementLikes/decrementLikes/incrementScrap/decrementScrap` 메서드 제거 (DB 트리거 단독 책임). `cascade=CascadeType.ALL` 도 제거 (트리거가 row 생성)
- 신규 `PendingRecipe.java`: `pending_recipes` 테이블의 모든 컬럼 매핑
- `Ingredient.java` record: `{name, amount}` → `{name, amount, unit, type, note}` (AI `IngredientItem` 정합)
- `RecipeSource.java` 폐기
- 신규 `RecipeAuthorType.java` (ADMIN/USER)
- `RecipeStatus.java` 보존 — `PendingRecipe` 가 사용

### Repository
- `RecipeRepository.java`: `findByStatusOrderBy*` → `findActiveOrderBy*` (status 컬럼 폐기, is_active 사용)
- 신규 `PendingRecipeRepository.java`: `findActiveByUserOrderByLatest`

### Service
- `RecipeService.create()`: `recipes` INSERT → `pending_recipes` INSERT. `RecipeStats` 트랜잭션 INSERT 코드 제거 (트리거가 처리)
- `RecipeService.listMine()`: 반환 타입 `RecipeListResponse` → `PendingRecipeListResponse`. `pending_recipes` 의 `is_active=true` 만 조회
- `RecipeService.listApproved()`: `recipes` 의 `is_active=true` 만 조회. `RecipeStatus.APPROVED` 분기 폐기
- `RecipeService.detail()`: `RECIPE_NOT_APPROVED` 분기 제거 (recipes 만 노출, is_active=false 면 `RECIPE_NOT_FOUND`). PENDING/REJECTED 본인·관리자 노출 로직 폐기
- `RecipeService.delete()`: `pending_recipe` hard delete (id 가 pending_recipe_id 임. 본인만 삭제 가능). `session_logs` 선행 NULL 처리 native query 제거 (테이블 자체가 폐기됨)

### DTO
- `RecipeCreateRequest.java`: `fullContent` 폐기, AI contract 의 모든 필드 추가 (대부분 optional). `ingredients` 도 새 element schema
- `RecipeCreateResponse.java`: `recipeId` → `pendingRecipeId`
- `RecipeListItemResponse.java`: `status` 폐기, `description`/`authorType`/`difficulty`/`cookingTime` 추가
- `RecipeDetailResponse.java`: `fullContent`/`source`/`status` 폐기. AI RecipeResponse 와 1:1 매핑되는 모든 필드 추가
- 신규 `PendingRecipeListItemResponse.java`, `PendingRecipeListResponse.java`

### Controller
- `RecipeController.java`: `/api/recipes/my` 의 응답 타입을 `PendingRecipeListResponse` 로 변경. 다른 엔드포인트는 시그니처 유지 (서비스만 변경)

### ErrorCode
- `RECIPE_NOT_APPROVED` 제거
- `PENDING_RECIPE_NOT_FOUND` 추가

---

## 4. 협업자 영향 / 운영 영향

- **로컬 DB 필수 wipe**: 기존 로컬 DB 가 V1 의 옛 체크섬을 들고 있으므로 `docker compose down -v` 필요. 안내 필요
- **API 클라이언트(프론트) 영향**:
  - `POST /api/recipes` 본문 schema 가 바뀜 (`fullContent` → `content` 외 다수). 프론트가 파싱 가능한 모양으로 응답이 나오는지 확인 필요
  - `GET /api/recipes/{id}` 응답에 `description`/`servings`/`difficulty` 등 필드 다수 추가
  - `GET /api/recipes/my` 응답 schema 가 `RecipeListResponse` → `PendingRecipeListResponse` 로 변경
- **운영 미배포** 단계라 데이터 마이그레이션은 불필요

---

## 5. 회귀 테스트

- [x] `./gradlew build -x test` PASS
- [ ] 로컬 DB 기동 검증 (`docker compose down -v && up -d && ./gradlew bootRun`) → V1→V2→V3 자동 적용 + Hibernate `validate` PASS
- [ ] `db-testing-guide.md` 의 §1, §2, §5, §6 통과
- [ ] 회원가입 → 로그인 → 레시피 제출 → `/api/recipes/my` 에 PENDING 상태로 조회되는지
- [ ] DB 트리거 검증: 새 recipe INSERT 시 recipe_stats row 자동 생성, likes 토글 시 likes_count 자동 증감

---

## 6. 명세서에 미치는 영향

- `SPEC-20260422-02` (recipe-create) → **v2 발행 완료**: [`docs/spec/recipe-create-v2.md`](../spec/recipe-create-v2.md) (`SPEC-20260502-02`)
- `SPEC-20260422-03` (recipe-read) → **v2 발행 완료**: [`docs/spec/recipe-read-v2.md`](../spec/recipe-read-v2.md) (`SPEC-20260502-03`)
- `SPEC-20260422-04` (recipe-delete) → **v2 발행 완료**: [`docs/spec/recipe-delete-v2.md`](../spec/recipe-delete-v2.md) (`SPEC-20260502-04`)
- `SPEC-20260422-05` (upload presigned URL) — 영향 없음

---

## 7. 후속 작업

- [ ] 위 §5 의 로컬 DB 검증
- [ ] §6 의 v2 명세 발행 (별도 PR)
- [ ] AI 서버 팀과 `recipes.embedding` 충전 메커니즘 / `chat_messages` write 권한 합의 (`api-server-tasks.md §5`)
- [ ] Step 6 (Admin) 구현 시 `pending_recipes → recipes` 이동 + `embedding` 트리거 / 외부 호출 결정
