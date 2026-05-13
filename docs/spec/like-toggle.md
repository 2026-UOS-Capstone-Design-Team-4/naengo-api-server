# SPEC-20260503-01: 좋아요 토글

## 0. 메타

| 항목 | 값 |
|---|---|
| 명세서 ID | `SPEC-20260503-01` |
| 도메인 | Engagement (Like) |
| 기능명 | 레시피 좋아요 토글 |
| 작성자 | API 서버 담당자 |
| 작성일 | 2026-05-03 |
| 우선순위 | P0 |
| 관련 명세서 | `SPEC-20260503-02` (스크랩 토글), `SPEC-20260503-03` (스크랩 목록), `SPEC-20260502-03` (레시피 조회 v2) |

---

## 1. 목적 (Why)

사용자가 마음에 드는 레시피에 좋아요를 누르고 다시 누르면 취소할 수 있어야 한다. UI 는 단일 버튼이므로 endpoint 도 토글식이 자연스럽다. 좋아요 수는 레시피 정렬·인기도의 핵심 지표라 카운터 정합성이 중요하다.

V4 에서 카운터는 DB 트리거(`trigger_likes_count`)가 책임지므로, 애플리케이션은 **단순한 INSERT / DELETE** 만 한다.

---

## 2. 사용자 시나리오

```
As a   로그인 사용자
I want 레시피 카드의 하트 버튼을 눌러
So that 좋아요를 추가하거나 (이미 눌렀다면) 취소할 수 있다
```

---

## 3. API 스펙

### 3.1 엔드포인트

| Method | Path | 인증 | 설명 |
|---|---|---|---|
| `POST` | `/api/recipes/{id}/like` | 필수 (USER) | 좋아요 토글 |

### 3.2 Request

- Path: `id` — `recipes.recipe_id`
- Body 없음

### 3.3 Response

성공 (HTTP 200):
```json
{
  "success": true,
  "data": {
    "liked":      true,
    "likesCount": 13
  }
}
```

- `liked`: 토글 후 상태 (`true` = 좋아요 누름, `false` = 취소됨)
- `likesCount`: 토글 후 `recipe_stats.likes_count` 의 최신 값

실패:

| HTTP | code | 언제 |
|---|---|---|
| 401 | `UNAUTHORIZED` | 토큰 / 쿠키 없음·만료 |
| 404 | `RECIPE_NOT_FOUND` | 존재하지 않는 `recipe_id` 또는 `is_active = false` 인 레시피 |

---

## 4. 비즈니스 규칙

1. 토글 동작:
   - `(user_id, recipe_id)` 가 `likes` 에 존재 → DELETE → `liked = false`
   - 존재하지 않음 → INSERT → `liked = true`
2. 좋아요 대상은 **`recipes` 테이블의 `is_active = true` 행만**. `pending_recipes` 는 좋아요 대상이 아니다 (사용자에게 노출되지 않으므로 토글 시도 자체가 비정상).
3. **자기 글 좋아요 허용**. 작성자도 자신의 레시피에 좋아요 가능.
4. **카운터 직접 갱신 금지**. `recipe_stats.likes_count` 변경은 V1 의 트리거 `trigger_likes_count` 가 단독으로 한다. 애플리케이션이 같이 갱신하면 카운트가 두 배가 된다.
5. 동시성 처리:
   - `(user_id, recipe_id)` UNIQUE 제약이 race condition 의 안전망. 두 동시 INSERT 가 들어오면 한 쪽은 UNIQUE 위반.
   - 우리 코드는 "exists check → INSERT" 후 `DataIntegrityViolationException` 을 받으면 "이미 좋아요 상태" 로 간주하여 `liked = true` 반환.
   - 두 동시 DELETE 는 idempotent (한 쪽만 실제 row 를 지우고, 다른 쪽은 0 row affected) → 어느 쪽이든 `liked = false`.
6. 응답의 `likesCount` 는 카운터 트리거가 발화한 후 값을 반환해야 한다. JPA persist/delete 후 `entityManager.flush()` 로 트리거를 발화시킨 뒤 `recipe_stats` 를 재조회.

---

## 5. 데이터 모델 영향

### 5.1 변경되는 테이블

| 테이블 | 변경 |
|---|---|
| `likes` | INSERT 또는 DELETE |
| `recipe_stats` | 트리거가 `likes_count` ±1 |

### 5.2 트랜잭션 경계

`@Transactional`: like INSERT/DELETE → flush → recipe_stats 조회. 한 트랜잭션 안에서 처리.

---

## 6. 외부 의존성

없음.

---

## 7. 권한·보안

- 인증 필수 (USER)
- 인증 통로: `Authorization: Bearer <JWT>` 헤더 또는 HttpOnly Cookie
- `user_id` 는 JWT `sub` 에서 추출. Body / Path 로 받지 않는다.

---

## 8. 성능·확장

- 호출 빈도: **높음** (목록 / 상세 페이지에서 자주 호출)
- DB 측: `idx_likes_user_id` + `uq_likes_user_recipe` UNIQUE 인덱스로 exists check 가 빠르다.
- 트리거 오버헤드: 작은 UPDATE 1회 (`recipe_stats` row 1건). 무시 가능.

---

## 9. 테스트 케이스

- [ ] 정상 토글 (없음 → 좋아요): 200, `liked=true`, `likesCount` += 1
- [ ] 정상 토글 (좋아요 → 취소): 200, `liked=false`, `likesCount` -= 1
- [ ] 존재하지 않는 recipe_id → 404 `RECIPE_NOT_FOUND`
- [ ] `is_active = false` 인 recipe → 404 `RECIPE_NOT_FOUND`
- [ ] 토큰 없이 요청 → 401
- [ ] 본인 레시피 좋아요 → 200 (허용)
- [ ] 한 사용자가 같은 레시피에 5번 토글 → DB 의 `likes_count` 가 0 또는 1 사이를 정확히 왕복
- [ ] (선택) 동시 토글 race: 같은 사용자가 동시에 2번 호출 → 둘 다 200, 최종 상태가 `liked=true` 또는 `liked=false` 둘 중 하나로 일관

---

## 10. 결정 사항

- 응답에 `likesCount` 를 포함하는 이유: 클라이언트가 별도 GET 없이 즉시 화면 반영하기 위함.
- "이미 좋아요 누름" 케이스에 별도 에러를 두지 않는다 (토글이 의도된 동작).
- 인증 헤더 + HttpOnly Cookie 양쪽 지원은 `JwtAuthenticationFilter` 의 책임 (별도 PR 에서 쿠키 지원 추가).

---

## 11. 범위 밖

- 좋아요 알림 (작성자에게 푸시)
- 본인 좋아요 목록 조회 (스크랩과 달리 좋아요 목록 노출 정책은 미정)
- "좋아요 취소" 단독 endpoint (DELETE) — 토글 한 종으로 충분
