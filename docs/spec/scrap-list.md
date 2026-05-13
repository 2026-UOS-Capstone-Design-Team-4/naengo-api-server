# SPEC-20260503-03: 내 스크랩 목록 조회

## 0. 메타

| 항목 | 값 |
|---|---|
| 명세서 ID | `SPEC-20260503-03` |
| 도메인 | Engagement (Scrap) |
| 기능명 | 본인 스크랩 레시피 목록 |
| 작성자 | API 서버 담당자 |
| 작성일 | 2026-05-03 |
| 우선순위 | P0 |
| 관련 명세서 | `SPEC-20260503-02` (스크랩 토글), `SPEC-20260502-03` (레시피 조회 v2) |

---

## 1. 목적

스크랩은 사용자의 개인 컬렉션이므로 별도 화면에서 모아 볼 수 있어야 한다 (마이페이지의 "찜한 레시피" 류). 좋아요는 비대칭으로 — 본 명세에서는 다루지 않음 (정책상 좋아요 모음 노출은 미정).

---

## 2. 사용자 시나리오

```
As a   로그인 사용자
I want 내가 스크랩한 레시피 목록을 최신 스크랩 순으로 보고 싶다
```

---

## 3. API 스펙

### 3.1 엔드포인트

| Method | Path | 인증 | 설명 |
|---|---|---|---|
| `GET` | `/api/scraps/my` | 필수 (USER) | 본인 스크랩 목록 (페이징) |

### 3.2 Request

- Query: `page` (기본 0), `size` (기본 20, 최대 50)

### 3.3 Response

성공 (HTTP 200) — `RecipeListResponse` 와 동일한 형태:
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
    "totalElements": 5,
    "totalPages":    1
  }
}
```

실패:

| HTTP | code | 언제 |
|---|---|---|
| 401 | `UNAUTHORIZED` | 토큰 / 쿠키 없음 |
| 400 | `INVALID_INPUT` | 잘못된 page / size |

---

## 4. 비즈니스 규칙

1. 결과는 본인이 스크랩한 레시피 중 **`recipes.is_active = true`** 인 것만. 스크랩 후 관리자가 `is_active = false` 로 비활성화 한 레시피는 목록에서 제외.
2. 정렬은 **스크랩 시각 내림차순** (`scraps.created_at DESC`). 레시피 자체의 createdAt 이 아니라 사용자가 스크랩한 시점.
3. 응답 스키마는 공개 목록(`SPEC-20260502-03`) 의 `RecipeListItemResponse` 를 그대로 재사용. 별도 "scrappedAt" 필드는 v1 에서 미노출 (필요 시 후속 명세).
4. 페이지네이션: `Pageable`, `size > 50` 은 50 으로 클램핑.
5. 작성자 닉네임 치환은 공개 목록과 동일 (탈퇴 사용자 → "탈퇴한 사용자").
6. **N+1 방지**: `scraps JOIN recipes JOIN recipe_stats` 단일 쿼리 + 작성자 닉네임 일괄 조회 (`SPEC-20260502-03 §8` 와 동일 패턴).

---

## 5. 데이터 모델 영향

읽기 전용. 스키마 변경 없음.

---

## 6. 외부 의존성

없음.

---

## 7. 권한·보안

- 인증 필수
- 인증 통로: `Authorization: Bearer <JWT>` 또는 HttpOnly Cookie

---

## 8. 성능·확장

- 호출 빈도: 중간 (마이페이지)
- 인덱스 활용: `idx_scraps_user_id` + `idx_recipes_is_active`
- 한 사용자의 스크랩 수가 큰 케이스(>1만) 는 일반적이지 않으므로 추가 캐싱 불필요

---

## 9. 테스트 케이스

- [ ] 토큰 없이 → 401
- [ ] 본인 스크랩 0건 → 200, `items=[]`, `totalElements=0`
- [ ] 본인 스크랩 N건 → 200, 정확히 N개 (페이징 적용)
- [ ] 다른 사용자가 스크랩한 레시피는 안 보임
- [ ] `is_active = false` 인 레시피는 목록에서 제외 (스크랩 row 는 남아 있어도)
- [ ] 정렬: 가장 최근 스크랩이 첫 항목
- [ ] `size = 100` → 50 으로 클램프
- [ ] 탈퇴 작성자의 레시피 → `authorNickname = "탈퇴한 사용자"`

---

## 10. 결정 사항

- 응답에 `scrappedAt` 필드를 두지 않은 이유: UI 는 "최근 스크랩 순" 으로 자연 노출되므로 필드 자체를 굳이 노출 안 함. 필요해지면 후속.
- `is_active = false` 레시피의 스크랩 row 는 보존. 관리자가 다시 활성화하면 다시 보일 수 있음. 별도 정리 잡 불필요.

---

## 11. 범위 밖

- 좋아요 목록 조회 (정책 미정)
- 다른 사용자의 스크랩 목록 (사생활)
- 스크랩 폴더 / 분류
- 스크랩 export
