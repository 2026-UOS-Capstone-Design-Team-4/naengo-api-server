# SPEC-20260504-04: 본인 선호도 조회 (user_profiles)

## 0. 메타

| 항목 | 값 |
|---|---|
| 명세서 ID | `SPEC-20260504-04` |
| 도메인 | User (선호도) |
| 기능명 | 본인의 `user_profiles` 전체 조회 |
| 작성자 | API 서버 담당자 |
| 작성일 | 2026-05-04 |
| 우선순위 | P1 (Step 4 후속) |
| 관련 명세서 | `SPEC-20260503-04` (마이페이지 조회), `SPEC-20260504-05` (선호도 수정) |

---

## 1. 목적

V4 통합 시 구 `users.preferences JSONB` 가 `user_profiles` 테이블의 풍부한 컬럼으로 분리됐다. 본 endpoint 는 두 종류의 데이터를 한 번에 노출:

1. **사용자 직접 입력** — `user_input` (자연어 문장 배열), `cooking_skill`, `preferred_cooking_time`, `serving_size`
2. **AI 분석 결과** — `allergies`, `dietary_restrictions`, `preferred_*`, `disliked_*`, `taste_keywords`, `frequently_used_*`, `recent_recipe_ids`, `ai_analyzed_at`

UI 는 두 영역을 분리해 표기 (직접 입력은 편집 가능 / AI 분석은 read-only).

---

## 2. 사용자 시나리오

```
As a   로그인 사용자
I want 내 선호도 (직접 입력 + AI 가 채팅에서 추출한 분석 결과) 를 한눈에 확인하고
So that 부정확한 부분을 직접 입력으로 보정하거나, AI 추천 정확도가 어떻게 향상되고 있는지 확인할 수 있다
```

---

## 3. API 스펙

### 3.1 엔드포인트

| Method | Path | 인증 | 설명 |
|---|---|---|---|
| `GET` | `/api/users/me/profile` | 필수 (USER) | 본인 `user_profiles` 전체 |

### 3.2 Request

- Body 없음

### 3.3 Response

성공 (HTTP 200) — row 가 존재하지 않아도 빈 default 로 반환:
```json
{
  "success": true,
  "data": {
    "userInput":                    [],
    "cookingSkill":                 null,
    "preferredCookingTime":         null,
    "servingSize":                  null,
    "allergies":                    [],
    "dietaryRestrictions":          [],
    "preferredIngredients":         [],
    "dislikedIngredients":          [],
    "preferredCategories":          [],
    "frequentlyUsedIngredients":    [],
    "tasteKeywords":                [],
    "recentRecipeIds":              [],
    "aiAnalyzedAt":                 null,
    "updatedAt":                    null
  }
}
```

실패:

| HTTP | code | 언제 |
|---|---|---|
| 401 | `UNAUTHORIZED` | 토큰 없음 |
| 409 | `ALREADY_WITHDRAWN` | 탈퇴된 사용자 |

---

## 4. 비즈니스 규칙

1. `user_id` 는 JWT 에서 추출.
2. `user_profiles` row 가 없으면 **빈 default** 응답 — 신규 사용자가 첫 GET 했을 때 명시적 INSERT 없이 응답 가능.
3. JSONB 배열 컬럼이 `null` 이면 응답에서 빈 배열 `[]` 로 정규화 (클라이언트 처리 단순화). 단 `aiAnalyzedAt` / `updatedAt` 은 `null` 그대로 (시간 없음 표현).
4. 응답 필드 순서는 직접 입력 → AI 분석 → 메타 (aiAnalyzedAt / updatedAt). 타이트한 schema 유지.

---

## 5. 데이터 모델 영향

읽기 전용. 스키마 변경 없음.

---

## 6. 외부 의존성

없음.

---

## 7. 권한·보안

- 인증 필수 (USER role)
- 다른 사용자 프로필 조회 불가 (path 가 `me` 고정)
- 응답에 PII 노출 없음 (선호도는 사용자 본인 정보)

---

## 8. 성능·확장

- 호출 빈도: 마이페이지 진입 시. 낮음~중간
- 단일 PK 조회. 추가 인덱스 불필요

---

## 9. 테스트 케이스

- [ ] 토큰 없이 → 401
- [ ] row 없는 신규 사용자 → 200 + 빈 default
- [ ] row 있는 사용자 → 200 + 모든 필드 반환
- [ ] JSONB null 컬럼 → 응답에 `[]`
- [ ] `aiAnalyzedAt`, `updatedAt` 은 null 또는 timestamp

---

## 10. 결정 사항

- 직접 입력 + AI 분석 한 응답에 통합 — UI 에서 두 섹션으로 분기. 클라이언트가 두 endpoint 호출하지 않아도 됨.
- 빈 default 응답 — INSERT 없이 GET 만으로 동작 (사용자가 명시적으로 PUT 해야 row 가 생긴다).
- `recent_recipe_ids` 는 단순 ID 배열 — 풀 RecipeListItemResponse 로 변환은 본 endpoint 에서 안 함 (필요하면 클라이언트가 별도 `GET /api/recipes?ids=...` 호출, 후속 spec).

---

## 11. 범위 밖

- 다른 사용자 프로필 조회 (사생활)
- recentRecipeIds → 풀 레시피 객체 변환
- 선호도 수정 (별도 spec `SPEC-20260504-05`)
- AI 분석 트리거 (자동, AI 서버 영역)
