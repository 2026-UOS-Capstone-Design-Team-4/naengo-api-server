# SPEC-20260422-03: 레시피 조회

## 0. 메타

| 항목 | 값 |
|---|---|
| 명세서 ID | `SPEC-20260422-03` |
| 도메인 | Recipe |
| 기능명 | 레시피 목록 / 단건 / 내 레시피 조회 |
| 작성자 | API 서버 담당자 |
| 작성일 | 2026-04-22 |
| 우선순위 | P0 |
| 관련 명세서 | `SPEC-20260422-02` (작성), `SPEC-20260422-04` (삭제) |

---

## 1. 목적

비로그인 사용자도 승인된 레시피 목록과 상세를 볼 수 있어야 한다(가입 전 서비스 가치 노출). 동시에 작성자는 자신이 쓴 PENDING/REJECTED 레시피를 추적할 수 있어야 한다("내 레시피" 탭).

---

## 2. 사용자 시나리오

```
As a   모든 사용자(로그인 여부 무관)
I want 승인된 레시피 목록을 최신순/인기순으로 보고 싶다

As a   로그인 사용자
I want 내가 쓴 레시피를 상태(검토 중/승인/반려)와 함께 보고 싶다
```

---

## 3. API 스펙

### 3.1 엔드포인트

| Method | Path | 인증 | 설명 |
|---|---|---|---|
| `GET` | `/api/recipes` | 선택 | APPROVED 레시피 목록 (페이징·정렬) |
| `GET` | `/api/recipes/{id}` | 선택 | 레시피 단건 |
| `GET` | `/api/recipes/my` | 필수 | 내가 작성한 레시피 (모든 상태) |

### 3.2 Request

**`GET /api/recipes`**
- Query: `page`(기본 0), `size`(기본 20, 최대 50), `sort`(기본 `latest`, 값: `latest` | `popular`)

**`GET /api/recipes/{id}`**
- Path: `id` (레시피 PK)

**`GET /api/recipes/my`**
- Query: `page`, `size`. 정렬은 최신순 고정.

### 3.3 Response

**목록 공통 형태**
```json
{
  "success": true,
  "data": {
    "items": [
      {
        "recipeId": 42,
        "title": "김치볶음밥",
        "imageUrl": "https://.../abc.jpg",
        "authorNickname": "요리왕",
        "status": "APPROVED",
        "likesCount": 12,
        "scrapCount": 3,
        "createdAt": "2026-04-22T14:30:00+09:00"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 135,
    "totalPages": 7
  }
}
```

**단건**
```json
{
  "success": true,
  "data": {
    "recipeId": 42,
    "title": "김치볶음밥",
    "fullContent": "1. ...",
    "imageUrl": "...",
    "source": "USER",
    "status": "APPROVED",
    "authorId": 7,
    "authorNickname": "요리왕",
    "ingredients": [{"name":"김치","amount":"200g"}],
    "likesCount": 12,
    "scrapCount": 3,
    "createdAt": "2026-04-22T14:30:00+09:00"
  }
}
```

실패:

| HTTP | code | 언제 |
|---|---|---|
| 404 | `RECIPE_NOT_FOUND` | 존재하지 않는 id |
| 403 | `RECIPE_NOT_APPROVED` | PENDING/REJECTED 상태이며 조회자가 작성자·관리자가 아닐 때 |
| 401 | `UNAUTHORIZED` | `/my` 에 토큰 없이 접근 |
| 400 | `INVALID_INPUT` | 잘못된 page/size/sort |

---

## 4. 비즈니스 규칙

1. `GET /api/recipes` 는 `status='APPROVED'` 만 반환.
2. `GET /api/recipes/{id}`:
   - APPROVED: 누구나 조회 가능.
   - PENDING/REJECTED: **작성자 본인** 또는 **ADMIN** 만 조회 가능. 그 외는 403 `RECIPE_NOT_APPROVED`.
3. `GET /api/recipes/my` 는 조회 사용자가 작성한 모든 상태(PENDING/APPROVED/REJECTED) 반환.
4. **작성자 닉네임 치환**: `users.deleted_at IS NOT NULL` 이면 응답의 `authorNickname` 은 `"탈퇴한 사용자"` 로 고정 (DB 의 `탈퇴한 사용자_<id>` 꼬리표는 응답에서 제거). `author_id` 가 NULL 이면 `"알 수 없음"` 또는 `null`.
5. `likesCount`/`scrapCount` 는 `recipe_stats` 조인 결과. 없으면 0.
6. 정렬:
   - `latest`: `created_at DESC`.
   - `popular`: `likes_count DESC, created_at DESC`.
7. 페이지네이션: Spring Data `Pageable` 사용. `size > 50` 은 50 으로 클램핑.

---

## 5. 데이터 모델 영향

- 읽기 전용. 스키마 변경 없음.
- 성능: `/api/recipes` 는 `recipes JOIN recipe_stats JOIN users` 가 될 수 있음. `idx_recipes_status` / `idx_recipes_created_at` 활용.

---

## 6. 외부 의존성

없음.

---

## 7. 권한·보안

- `/api/recipes`, `/api/recipes/{id}` 는 `permitAll` (SecurityConfig 이미 해당). 단 인증 사용자일 경우 PENDING 본인 글 허용 로직이 작동해야 하므로 필터가 토큰을 선택적으로 처리해야 함 — 현 `JwtAuthenticationFilter` 는 토큰 없을 때 통과하므로 OK.
- `/api/recipes/my` 는 `authenticated()`.

---

## 8. 성능·확장

- 호출 빈도: **높음** (메인 화면 진입 시마다).
- 캐싱: MVP 에서는 불필요. 필요 시 Redis 도입 검토.
- N+1 주의: `items` 응답에 닉네임이 들어가므로 Repository 에서 fetch join 또는 projection DTO 로 단일 쿼리 처리.

---

## 9. 테스트 케이스

- [ ] `/api/recipes` 가 APPROVED 만 반환
- [ ] 페이징 기본값 0/20 적용, `size=100` 은 50 으로 클램핑
- [ ] `sort=popular` 정렬 확인
- [ ] 단건: APPROVED → 누구나 200
- [ ] 단건: PENDING + 비작성자 → 403
- [ ] 단건: PENDING + 본인 → 200
- [ ] 단건: PENDING + ADMIN → 200
- [ ] `/my`: 토큰 없음 → 401
- [ ] `/my`: 본인 레시피만 반환, PENDING 포함
- [ ] 탈퇴 작성자의 닉네임은 "탈퇴한 사용자" 로 치환

---

## 10. 결정 사항

- 인기순 정렬 기준은 좋아요 수 우선. 스크랩까지 가중 합산할지는 후속 이슈.
- `ingredients` 는 단건 조회에서만 반환. 목록에서는 생략 (payload 절약).

---

## 11. 범위 밖

- 검색 / 필터 (카테고리, 재료 기반 필터 등) — 이후 명세
- RAG 기반 추천 (AI 서버 책임)
- 스크랩 / 좋아요 여부 표기 (Step 3 명세 후 합산)
