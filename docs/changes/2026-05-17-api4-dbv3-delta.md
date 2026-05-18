# 변경점 분석 — api-4.json / DBv3.sql / ai·front (2026-05-17)

> 입력: 사용자 업로드 `api-4.json`, `DBv3.sql` + naengo-ai / naengo-front 디렉토리 검사.
> 결론 요약: **AI HTTP 계약·AI 코드 변경 없음**. **DBv3.sql = 정규화 스키마(aspirational) 채택 신호** → api-server 영향 큼(의사결정 필요). **front 는 snake_case 기대 확정 + `profile_image_url` 신규 기대**.

---

## 1. api-3.json vs api-4.json — **변경 없음 (의미상 동일)**

- 라인 수 동일(2862), raw `diff` 전체 차이는 **CRLF/BOM 차이**일 뿐.
- 정규화 JSON 비교(`utf-8-sig`, 키 정렬): **경로 추가 0 / 삭제 0 / 값 변경 0**.
- → AI 서버 OpenAPI 계약 변화 없음. 우리 PR-1~7 정합 그대로 유효. `docs/api-3.json` 갱신 불요(동일).

## 2. naengo-ai 디렉토리 — **코드 변경 없음**

- git 미적용(플랫 스냅샷, 전 파일 mtime 동일)이라 정밀 diff 불가하나:
- `app/models/recipe.py` / `pending` / `chat` / `user` 모두 **여전히 비정규화(denormalized)**: `recipes` 단일 테이블 + `ingredients/instructions/category/tags/tips` JSONB, `cooking_time`, `content`, `video_url`, `image_url`, `embedding`. api-4.json 과 정합.
- `db/schema.sql` 은 이전과 동일(= 정규화 설계). 즉 **naengo-ai 내부에서도 schema.sql(정규화) ↔ models(비정규화) 불일치**가 그대로 — schema.sql 은 미적용 aspirational (api3-alignment §3-2 에 기록한 사실 변함 없음).
- → AI 서버는 **실제로는 비정규화 모델로 동작**. DBv3.sql 을 실 사용하지 않음.

## 3. DBv3.sql vs 우리 DB — **정규화 스키마 채택 시도 (핵심 이슈)**

`DBv3.sql` 을 `naengo-ai/db/schema.sql` 과 비교: **단 2줄 차이**
- (+) 주석 `// users는 우리` ← "users 테이블은 api-server 소유" 명시
- (−) `idx_recipe_embeddings_vector` (ivfflat) 인덱스 제거

즉 **DBv3.sql ≡ naengo-ai 의 정규화 schema.sql**. 우리 현행 `V1__init.sql`(비정규화)와 **레시피 영역이 구조적으로 다름**:

| 항목 | 우리 V1 (현행, 비정규화) | DBv3 (정규화) |
|---|---|---|
| `recipes` 재료/조리/분류 | `ingredients/instructions/category/tags/tips` **JSONB 컬럼** | 별도 테이블 `recipe_ingredients`/`recipe_steps`/`recipe_labels`/`recipe_classifications` |
| 미디어/임베딩 | `recipes.image_url/video_url/embedding` 컬럼 | `recipe_media` / `recipe_embeddings` 테이블 |
| 조리시간 | `cooking_time INTEGER` | `prep/cook/total_time_minutes` |
| 상태 | `is_active` boolean | `status(DRAFT/PUBLISHED/ARCHIVED)`+`visibility`+`is_active` |
| 출처 | 없음 | `recipe_sources` + `recipes.source_*` 다수 |
| `pending_recipes` | 풀 구조화 컬럼(ingredients JSONB 등) | `suggested_patch JSONB`+`total_time_minutes`, 구조화 컬럼 없음 |
| `user_profiles` 시간 | `preferred_cooking_time` | `preferred_cooking_time_minutes` (rename) |
| PK 타입 | `BIGSERIAL/BIGINT` (JPA Long) | `SERIAL/INTEGER` |
| `users` | 우리 것 (provider/provider_id/deleted_at, email nullable, pw nullable) | 우리 것 유지 (주석 명시) |

### 모순 (반드시 합의 필요)
- **DBv3(정규화)** 를 공유 DB 로 적용하면:
  - 우리 JPA `Recipe`/`PendingRecipe` 엔티티는 `ingredients/instructions/cooking_time/...` 컬럼을 기대 → Hibernate `ddl-auto: validate` **부팅 실패**. Recipe/Pending 도메인 **전면 재작성**(정규화 매핑) 필요.
  - **naengo-ai 의 실제 코드(models/recipe.py)도 비정규화 컬럼을 기대** → AI 서버도 DBv3 위에서 깨짐. 즉 DBv3 는 *현재 아무도 안 쓰는* 설계.
  - api-4.json(=api-3) `RecipeResponse/PendingRecipeResponse` 는 여전히 비정규화 평면 구조 → 클라이언트 계약은 정규화와 무관(서버가 join 으로 조립해야 함).
- **결론**: DBv3 는 "팀원이 제안/핸드오버한 미래형 정규화 스키마"이지, AI 서버·API 서버 어느 쪽도 실제로 돌릴 수 있는 상태가 아님. **단독 적용 시 양 서버 모두 장애**.

### 권고 (의사결정 안건 — 미적용, 합의 후 진행)
1. **(A) 보류·현행 유지 (권장 단기)**: 공유 DB 는 현행 비정규화(우리 V1 = AI 실모델 정합) 유지. DBv3 는 "차기 정규화 마이그레이션 후보"로 보관. AI 팀에 "models 도 정규화로 갈 건지/언제" 확정 요청. — 리스크 0, 즉시 안정.
2. **(B) 정규화 전면 채택**: AI(models+endpoints) + api-server(엔티티/서비스/DTO 매퍼) 동시 재작성 + 신규 V4 마이그레이션. api-4.json 응답은 join 조립. — 대규모, AI 팀과 동시 배포 필수. 단독 진행 불가.
3. `users` 는 어느 경우든 **우리 V1+V2+V3 유지** (DBv3 주석도 동의).

→ **단독으로 DBv3 적용/엔티티 변경 금지.** AI 팀과 (B) 채택 시점·주체 합의 전까지 (A).

## 4. naengo-front — snake_case 확정 + 신규 기대 필드

- git 미적용이라 정밀 diff 불가. 현행 호출/파싱 기준 사실:
- **전 응답 snake_case 파싱 확정**: `user_id`/`is_active`/`provider`/`provider_id`/`is_liked`/`created_at`/`pending_recipe_id`/`cooking_time`/`ingredients_raw`/`user_input`/`preferred_cooking_time` 등 → **우리 PR-8 전역 snake_case 결정이 정확함**(검증됨).
- **신규 기대 필드 (우리 미제공)**: `lib/models/user.dart` 가 `j['provider_id']`, **`j['profile_image_url']`** 파싱.
  - 우리 `UserMeResponse` = `user_id/email/nickname/role/provider/is_active/created_at`. **누락: `is_blocked`, `provider_id`, `profile_image_url`.**
  - api-4.json `UserResponse` = `user_id/email/nickname/role/is_active/is_blocked/created_at` (provider/provider_id/profile_image_url 없음). → front 가 계약보다 앞서 있음.
  - Dart 파서는 키 없으면 보통 null 허용이라 즉시 깨지지는 않으나, **프로필 이미지 기능을 front 가 기대** → 별도 합의 항목.
- `lib/services/recipe_service.dart` 가 `/api/v1/users/me/recipes` 류 미존재 경로 참조 → 구 mock 레이어(주석상 "API 연결 후 교체"). 실 클라이언트는 `naengo_api_service.dart`(정상 경로). front 내부 정리 대상이며 우리 영향 없음.

---

## 5. api-server 액션 (요약)

| # | 항목 | 조치 |
|---|---|---|
| 1 | api-4 vs api-3 | 변경 없음 → **무대응** |
| 2 | DBv3 정규화 | **단독 적용 금지.** AI 팀과 정규화 채택 합의 전까지 현행 유지(옵션 A). 합의 문서화 필요 |
| 3 | front snake_case | **이미 PR-8 로 충족** (검증 완료) |
| 4 | `profile_image_url`/`provider_id`/`is_blocked` 를 `UserMeResponse` 에 추가? | front 기대 + 소셜 정보 노출 → **합의 후 추가 검토** (별도 작업) |

> 본 분석은 "파악"만. 코드/스키마 변경은 없음. 옵션 결정 시 후속 작업 분리.
