# SPEC-20260504-01: 관리자 — 대기 레시피 목록 조회

## 0. 메타

| 항목 | 값 |
|---|---|
| 명세서 ID | `SPEC-20260504-01` |
| 도메인 | Admin |
| 기능명 | 사용자 제출 레시피(`pending_recipes`) 목록 / 단건 조회 (관리자 검토용) |
| 작성자 | API 서버 담당자 |
| 작성일 | 2026-05-04 |
| 우선순위 | P0 |
| 관련 명세서 | `SPEC-20260504-02` (승인/반려), `SPEC-20260504-03` (사용자 차단) |

---

## 1. 목적

관리자가 사용자가 제출한 레시피들을 한눈에 보고 승인/반려를 결정할 수 있어야 한다. PENDING 만 보는 게 기본이지만, 과거 승인/반려 이력도 조회 가능해야 추적이 된다.

---

## 2. 사용자 시나리오

```
As a   관리자(role=ADMIN)
I want 사용자 제출 레시피 목록을 상태별로 보고
       각 항목의 전체 내용을 확인하고
So that 검토 후 승인 / 반려 결정을 내릴 수 있다
```

---

## 3. API 스펙

### 3.1 엔드포인트

| Method | Path | 인증 | 설명 |
|---|---|---|---|
| `GET` | `/api/admin/pending-recipes` | 필수 (ADMIN) | 페이징 목록 — 상태별 필터 |
| `GET` | `/api/admin/pending-recipes/{id}` | 필수 (ADMIN) | 단건 상세 |

### 3.2 Request

**`GET /api/admin/pending-recipes`**
- Query:
  - `status` (선택, `PENDING` | `APPROVED` | `REJECTED`. 기본값 `PENDING`)
  - `page` (기본 0), `size` (기본 20, 최대 50)

**`GET /api/admin/pending-recipes/{id}`**
- Path: `id` — `pending_recipes.pending_recipe_id`

### 3.3 Response

#### 목록

```json
{
  "success": true,
  "data": {
    "items": [
      {
        "pendingRecipeId": 17,
        "userId":          7,
        "userNickname":    "요리왕",
        "title":           "할머니식 김치찌개",
        "description":     "...",
        "status":          "PENDING",
        "adminNote":       null,
        "reviewedAt":      null,
        "createdAt":       "2026-05-02T11:20:00+09:00"
      }
    ],
    "page":          0,
    "size":          20,
    "totalElements": 1,
    "totalPages":    1
  }
}
```

#### 단건 — pending_recipes 의 전체 컬럼 노출

```json
{
  "success": true,
  "data": {
    "pendingRecipeId": 17,
    "userId":          7,
    "userNickname":    "요리왕",
    "title":           "...",
    "description":     "...",
    "content":         "...",
    "ingredients":     [{"name":"김치","amount":"200","unit":"g","type":"메인","note":null}],
    "ingredientsRaw":  "...",
    "instructions":    ["..."],
    "servings":        2.0,
    "cookingTime":     20,
    "calories":        180,
    "difficulty":      "easy",
    "category":        ["한식","찌개"],
    "tags":            ["얼큰한"],
    "tips":            ["..."],
    "videoUrl":        null,
    "imageUrl":        "https://.../xyz.jpg",
    "status":          "PENDING",
    "adminNote":       null,
    "reviewedAt":      null,
    "createdAt":       "2026-05-02T11:20:00+09:00"
  }
}
```

실패:

| HTTP | code | 언제 |
|---|---|---|
| 401/403 | `UNAUTHORIZED` / FORBIDDEN | 토큰 없음 / 일반 사용자 토큰 |
| 400 | `INVALID_INPUT` | 잘못된 status / page / size |
| 404 | `PENDING_RECIPE_NOT_FOUND` | 단건 조회에 존재하지 않는 id |

---

## 4. 비즈니스 규칙

1. `/api/admin/**` 는 SecurityConfig 의 `hasRole("ADMIN")` 가드로 자동 보호. 일반 사용자 접근 시 403.
2. **`is_active` 무관하게 모두 노출**. 사용자가 취소(`is_active=false`) 한 row 도 관리자 화면에서 보임 (감사 / 신고 대응용).
3. 정렬: `created_at DESC` (최근 제출 순).
4. 단건 응답에 `userNickname` 포함: `users` JOIN. 탈퇴 사용자면 `"탈퇴한 사용자"` 로 치환 (`AuthorDisplayName`).
5. 페이징: `Pageable`, `size > 50` 은 50 으로 클램핑.

---

## 5. 데이터 모델 영향

읽기 전용. 스키마 변경 없음.

---

## 6. 외부 의존성

없음.

---

## 7. 권한·보안

- 인증 필수, **role=ADMIN** 필수
- SecurityConfig 의 path-based guard 가 처리 (코드 추가 없음)
- 응답에 `password_hash` 등 PII 노출 금지 — 본 endpoint 는 `users.user_id / nickname` 만 노출

---

## 8. 성능·확장

- 호출 빈도: 낮음 (관리자만)
- 인덱스: `idx_pending_recipes_status`, `idx_pending_recipes_user_id`

---

## 9. 테스트 케이스

- [ ] 토큰 없이 → 401/403
- [ ] USER 토큰으로 → 403
- [ ] ADMIN 토큰 + status=PENDING → 200, PENDING 만
- [ ] status 미지정 → 200, PENDING 기본
- [ ] status=APPROVED / REJECTED → 해당만
- [ ] 잘못된 status (`BLAH`) → 400
- [ ] 단건: 존재 → 200 (모든 필드 + userNickname)
- [ ] 단건: 없음 → 404
- [ ] 단건: 탈퇴 작성자 → `userNickname = "탈퇴한 사용자"`

---

## 10. 결정 사항

- 단건 / 목록 분리 — 목록에는 요약, 단건에 전체 필드. 큰 본문(content, ingredients) 페이로드 절약.
- `is_active=false` (사용자 취소) 도 관리자에게 노출 — 감사·신고 대응 시 필요.

---

## 11. 범위 밖

- 검색 / 필터 (제목, 작성자) — 후속
- 일괄 승인 / 반려 (batch)
- 외부 신고 시스템 연동
