# SPEC-20260502-03: 레시피 조회 v2 (recipes 공개 + 내 pending 분리)

## 0. 메타

| 항목 | 값 |
|---|---|
| 명세서 ID | `SPEC-20260502-03` |
| 도메인 | Recipe |
| 기능명 | 공개 레시피 목록·단건 + 내 제출 레시피 목록 |
| 작성자 | API 서버 담당자 |
| 작성일 | 2026-05-02 |
| 우선순위 | P0 |
| 관련 명세서 | `SPEC-20260502-02`, `SPEC-20260502-04`, `SPEC-20260502-01` (AI contract) |
| 대체 대상 | [`SPEC-20260422-03`](recipe-read.md) |

---

## 1. 목적

- 비로그인 사용자도 **승인된** 레시피 목록·상세를 볼 수 있어야 한다 (가입 전 가치 노출).
- 로그인 사용자는 자신이 **제출한** 레시피들의 검토 상태(PENDING / APPROVED / REJECTED)를 추적할 수 있어야 한다.

V4 통합으로 두 데이터 소스가 분리되었기 때문에 엔드포인트도 분리·재정의된다.

---

## 2. 사용자 시나리오

```
As a   모든 사용자(로그인 여부 무관)
I want 승인된 레시피 목록을 최신순/인기순으로 보고
       특정 레시피의 상세 (재료·조리순서 등) 를 확인하고 싶다

As a   로그인 사용자
I want 내가 제출한 레시피들의 상태(검토 중 / 승인 / 반려) 와 관리자 메모를 보고 싶다
```

---

## 3. API 스펙

### 3.1 엔드포인트

| Method | Path | 인증 | 설명 |
|---|---|---|---|
| `GET` | `/api/recipes` | 선택 | 승인된 레시피 목록 (`recipes` 의 `is_active = true`) |
| `GET` | `/api/recipes/{id}` | 선택 | `recipes` 단건 상세 |
| `GET` | `/api/recipes/my` | 필수 | **내가 제출한 `pending_recipes` 목록** (모든 status) |

> v1 과 경로는 동일. `/api/recipes/my` 의 의미와 응답 schema 가 변경됨.

### 3.2 Request

**`GET /api/recipes`**
- Query: `page` (기본 0), `size` (기본 20, 최대 50), `sort` (기본 `latest`, 값: `latest` | `popular`)

**`GET /api/recipes/{id}`**
- Path: `id` — `recipes.recipe_id`

**`GET /api/recipes/my`**
- Query: `page`, `size`. 정렬은 `created_at DESC` 고정.

### 3.3 Response

#### 공개 목록 (`GET /api/recipes`)

```json
{
  "success": true,
  "data": {
    "items": [
      {
        "recipeId":       42,
        "title":          "김치두부찌개",
        "description":    "칼칼하고 깊은 맛.",
        "imageUrl":       "https://.../abc.jpg",
        "authorNickname": "요리왕",
        "authorType":     "ADMIN",
        "difficulty":     "easy",
        "cookingTime":    20,
        "likesCount":     12,
        "scrapCount":     3,
        "createdAt":      "2026-04-22T14:30:00+09:00"
      }
    ],
    "page":          0,
    "size":          20,
    "totalElements": 135,
    "totalPages":    7
  }
}
```

#### 단건 상세 (`GET /api/recipes/{id}`)

```json
{
  "success": true,
  "data": {
    "recipeId":        42,
    "title":           "김치두부찌개",
    "description":     "칼칼하고 깊은 맛.",
    "ingredients":     [{"name":"김치","amount":"200","unit":"g","type":"메인","note":"잘 익은 것"}],
    "ingredientsRaw":  "김치 200g, 두부 1모",
    "instructions":    ["냄비에 기름을 두르고 ...","..."],
    "servings":        2.0,
    "cookingTime":     20,
    "calories":        180,
    "difficulty":      "easy",
    "category":        ["한식","찌개"],
    "tags":            ["얼큰한","국물요리"],
    "tips":            ["김치는 충분히 익은 것이 좋다"],
    "content":         "1. ... (자유 형식)",
    "videoUrl":        "https://youtube.com/watch?v=...",
    "imageUrl":        "https://.../abc.jpg",
    "authorType":      "ADMIN",
    "authorId":        7,
    "authorNickname":  "요리왕",
    "likesCount":      12,
    "scrapCount":      3,
    "createdAt":       "2026-04-22T14:30:00+09:00"
  }
}
```

#### 내 제출 레시피 (`GET /api/recipes/my`)

```json
{
  "success": true,
  "data": {
    "items": [
      {
        "pendingRecipeId": 17,
        "title":           "할머니식 김치찌개",
        "description":     "(선택)",
        "imageUrl":        "https://.../xyz.jpg",
        "status":          "PENDING",
        "adminNote":       null,
        "reviewedAt":      null,
        "createdAt":       "2026-05-02T11:20:00+09:00"
      },
      {
        "pendingRecipeId": 12,
        "title":           "오늘의 잡채",
        "status":          "REJECTED",
        "adminNote":       "사진 화질이 너무 낮습니다. 재제출 부탁드립니다.",
        "reviewedAt":      "2026-04-30T10:00:00+09:00",
        "createdAt":       "2026-04-29T18:00:00+09:00"
      }
    ],
    "page":          0,
    "size":          20,
    "totalElements": 2,
    "totalPages":    1
  }
}
```

#### 실패

| HTTP | code | 언제 |
|---|---|---|
| 404 | `RECIPE_NOT_FOUND` | 존재하지 않거나 `is_active = false` 인 `recipes.recipe_id` |
| 401 | `UNAUTHORIZED` | `/my` 에 토큰/쿠키 없음 |
| 400 | `INVALID_INPUT` | 잘못된 page/size/sort |

> v1 의 `RECIPE_NOT_APPROVED` (403) 는 폐기. `recipes` 에는 승인된 것만 존재하므로 PENDING/REJECTED 가시성 분기가 사라짐.

---

## 4. 비즈니스 규칙

1. `GET /api/recipes` 는 `recipes.is_active = true` 만 반환. status 컬럼은 V4 에서 폐기됐으므로 상태 분기 없음.
2. `GET /api/recipes/{id}`:
   - 존재하지 않거나 `is_active = false` → 404 `RECIPE_NOT_FOUND` (v1 의 본인·관리자만 PENDING 노출 분기 폐기)
   - 본인 / 관리자가 자신의 PENDING 을 보려면 `/api/recipes/my` 사용
3. `GET /api/recipes/my` 는 **`pending_recipes`** 를 조회. `is_active = true` 인 row 만 노출 (본인이 취소한 것은 안 보임). PENDING / APPROVED / REJECTED 모든 상태 반환.
   - `APPROVED` 상태로 남아있는 row 는 관리자가 승인 후 `recipes` 로 옮겨졌지만 pending row 도 보존하고 싶다면 그대로. 보통 승인 트랜잭션이 pending row 를 삭제하므로 실제로는 PENDING / REJECTED 만 보일 것.
4. **작성자 닉네임 치환**: `users.deleted_at IS NOT NULL` 이면 응답의 `authorNickname` 은 `"탈퇴한 사용자"` 고정. `author_id` 가 NULL 이면 `null`.
5. `likesCount` / `scrapCount` 는 `recipe_stats` 조인 결과. 누락 시 0. (V1 트리거가 row 자동 생성하므로 NULL 이 나오는 경우는 드뭄)
6. 정렬:
   - `latest`: `created_at DESC`
   - `popular`: `likes_count DESC, created_at DESC`
7. 페이지네이션: Spring Data `Pageable`. `size > 50` 은 50 으로 클램핑.

---

## 5. 데이터 모델 영향

읽기 전용. 스키마 변경 없음.

성능 메모: `/api/recipes` 는 `recipes JOIN recipe_stats LEFT JOIN users` 가 될 수 있음. `idx_recipes_is_active`, `idx_recipes_created_at`, `idx_recipes_author_id` 활용.

---

## 6. 외부 의존성

없음. AI 서버의 RAG 검색 결과는 `chat_messages.recipe_ids` 에 있으며, 이를 받은 클라이언트가 본 엔드포인트로 단건 / 일괄 조회한다.

---

## 7. 권한·보안

- `/api/recipes`, `/api/recipes/{id}` → `permitAll`
- `/api/recipes/my` → `authenticated()`
- 인증 통로: `Authorization: Bearer <JWT>` 또는 HttpOnly Cookie
- 응답에 PII 노출 없음 (작성자는 닉네임만)

---

## 8. 성능·확장

- 호출 빈도: **높음** (메인 페이지)
- 캐싱: MVP 미적용. 필요 시 Redis 검토
- N+1 주의: 작성자 닉네임 일괄 조회 (이미 v1 구현에 반영)

---

## 9. 테스트 케이스

- [ ] `/api/recipes` 가 `is_active = false` 행을 안 반환
- [ ] 페이징 기본값 0/20, `size=100` → 50 으로 클램핑
- [ ] `sort=popular` → 좋아요 우선 정렬
- [ ] `/api/recipes/{id}`: 존재 + active=true → 200
- [ ] `/api/recipes/{id}`: active=false → 404 `RECIPE_NOT_FOUND`
- [ ] `/api/recipes/{id}`: 응답에 ingredients/instructions/servings/category/tips 모두 포함
- [ ] `/my`: 토큰/쿠키 없음 → 401
- [ ] `/my`: 본인의 PENDING / REJECTED 가 보임 (적용 가능 시)
- [ ] `/my`: 본인이 취소(`is_active = false`) 한 row 는 안 보임
- [ ] 탈퇴 작성자의 닉네임은 `"탈퇴한 사용자"` 로 치환

---

## 10. 결정 사항

- v1 의 `status='APPROVED'` 필터는 `is_active = true` 로 단순화. 상태 머신이 `pending_recipes` 로 분리되면서 `recipes` 의 노출 토글이 boolean 으로 충분해졌다.
- 인기순 정렬 기준은 v1 과 동일 (좋아요 우선). 스크랩 가중 합산은 후속 이슈.
- 단건 응답에서 ingredients/instructions/category/tags/tips 는 모두 반환 (페이로드는 늘지만 단건 호출 빈도가 낮으므로 수용). 목록에서는 description 까지만.

---

## 11. 범위 밖

- 검색 / 카테고리·재료 기반 필터 (이후 명세)
- RAG 기반 추천 (AI 서버 책임 — `/api/v1/chat/rooms*` 의 `recipes` 이벤트)
- 스크랩 / 좋아요 여부 표기 (Step 3 명세 후 합산)
- 관리자 전용 조회 (PENDING 전수 등) — Step 6
