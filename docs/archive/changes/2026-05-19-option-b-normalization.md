# 옵션 B 구현 — 레시피 정규화 (2026-05-19)

> **2026-05-19: 정본 머지 완료.** 본 문서는 머지된 B 의 설계·deviation·미해결 안건 기록이다.
> 머지 전 산출물(롤백용 백업 스냅샷)은 `naengo-api-server-copied/` 트리에 그대로 보존.
> 입력 정본 스키마: `naengo-ai/naengo-ai-main/db/schema.sql` (정규화 설계).

## 1. 한 줄 결론

naengo-ai 정규화 schema.sql 의 recipe 서브시스템을 채택. **api-3.json 평면 계약은 불변**
(클라이언트 영향 0) — DB 만 정규화하고 서버가 평면 ↔ 정규화 변환. 통합테스트 **30건 PASS**
(Auth 8 / Cors 4 / ProfileChat 3 / Recipe 6 / RequestId 4 / SocialAuth 5).

## 2. 의도적 deviation (정본 schema.sql 대비)

| # | 항목 | 결정 | 사유 |
|---|---|---|---|
| 1 | PK/FK 타입 | `BIGSERIAL/BIGINT` 유지 (schema.sql 의 `SERIAL/INTEGER` 미채택) | int4↔int8 전환은 JPA `Long`·전 도메인·테스트 ~40파일 파급, 기능 이득 0. 정규화 가치는 테이블 분리지 정수 폭이 아님 |
| 2 | `users` / `user_profiles` | 우리 V1+V2+V3 유지. `user_profiles.preferred_cooking_time → _minutes` 리네임만 적용 | `users` 소유권은 AI 팀 합의 사안(별도 에스컬레이션). profile 컬럼 리네임은 B 정렬 항목(#8) |
| 3 | AI 파이프라인 테이블 | DDL 은 V4 에 생성(공유 DB schema.sql 정합·AI 공동배포용)하되 **JPA 미매핑** | `recipe_sources/_extractions/_image_generations/_embeddings/_quality_scores/_classifications` 는 AI 소유. `ddl-auto: validate` 는 미매핑 테이블 무검사 |
| 4 | 평면 계약 | `RecipeListItemResponse`/`PendingRecipeResponse`/admin DTO 외형 불변 | front/admin/AI contract(api-3=api-4) 보존. 서버가 JOIN/조립 |

## 3. 변경 파일

- **마이그레이션**: `V4__normalize_recipes.sql` — 구 비정규화 recipes/pending_recipes/likes/scraps/recipe_stats 폐기 → 정규화 recipe 서브시스템(분리 테이블 + 트리거 + 인덱스) 생성, `user_profiles` 컬럼 리네임.
- **엔티티**: `Recipe`(정규화 코어) 재작성 + 신규 `RecipeIngredient`/`RecipeStep`/`RecipeLabel`/`RecipeMedia`/`RecipeDraft`. `PendingRecipe` 재작성(`submission_text` + `draft_payload` JSONB, 평면 accessor 유지). `RecipeAuthorType` 에 `SOURCE` 추가. `RecipeStats` 불변.
- **레포지토리**: `RecipeRepository`(video 파생쿼리 제거) + 신규 `Recipe{Ingredient,Step,Label,Media}Repository`. `PendingRecipeRepository` 불변.
- **매핑/서비스**: 신규 `RecipeNormalizer`(평면↔정규화 순수함수), `RecipeListMapper` 재작성(자식 일괄조회 후 그룹 조립). `PendingRecipeService.create`(draft 빌드), `AdminRecipeService`(promote→정규화 자식 INSERT, video_url 조회→`recipe_media`). `UserProfile` 컬럼명 정렬.
- `RecipeService`/`ChatService`/`LikeService`/`ScrapService`/DTO/`PendingRecipeResponse` — **무변경**(안정 인터페이스 보존: `findActiveByIds`, `RecipeListMapper.toItems`, `RecipeListItemResponse.recipeId()`, PendingRecipe 평면 getter).

## 4. 평면 ↔ 정규화 매핑 규칙

읽기: `cooking_time←cooking_time_minutes`, `calories←kcal_per_serving`, `ingredients←recipe_ingredients`,
`ingredients_raw←raw_text 또는 "name amount unit" 재구성`, `instructions←recipe_steps(step_no)`,
`category/tags/tips←recipe_labels(label_type)`, `image_url/video_url←recipe_media(media_type)`.
쓰기(승격): 역방향 분해 + `recipe_stats/classifications/quality_scores` 는 DB 트리거 자동 생성.
PendingRecipe: `content↔submission_text`, 구조화 필드 ↔ `draft_payload`(JSONB, 키는 schema.sql 과 1:1, `ingredients_raw` 는 배열 — 평면 경계에서 join/split).

## 5. 미해결 (B 머지 전 합의 필요)

- `users` 테이블 소유권 (AI 팀) — schema.sql 에서 `// users는 우리` 주석 삭제됨, 재확인 선행.
- AI 가 models/endpoints 를 정규화로 동시 전환 + 합동 컷오버 (공유 DB → 단독 머지 시 양 서버 장애).
- B 채택 결정 자체 (현재 정본은 옵션 A 유지).
