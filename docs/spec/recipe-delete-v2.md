# SPEC-20260502-04: 레시피 삭제 v2 (본인 pending 제출 취소)

## 0. 메타

| 항목 | 값 |
|---|---|
| 명세서 ID | `SPEC-20260502-04` |
| 도메인 | Recipe |
| 기능명 | 본인이 제출한 pending 레시피 삭제 |
| 작성자 | API 서버 담당자 |
| 작성일 | 2026-05-02 |
| 우선순위 | P0 |
| 관련 명세서 | `SPEC-20260502-02`, `SPEC-20260502-03` |
| 대체 대상 | [`SPEC-20260422-04`](recipe-delete.md) — 보존되며 변경 사유는 [`docs/changes/SPEC-20260422-04-CL01.md`](../changes/SPEC-20260422-04-CL01.md) + [`docs/changes/V4-integration-resolved.md`](../changes/V4-integration-resolved.md) |

---

## 1. 목적

레시피는 정책상 **수정이 영구 불가능**하다. 사용자가 제출한 내용을 정정하려면 "삭제 후 재제출" 이 유일한 경로다. V4 통합 후 사용자 자산은 `pending_recipes` 에 있으므로, 본 엔드포인트는 본인 소유 `pending_recipe` 를 삭제한다.

승인되어 `recipes` 로 이동한 행의 삭제는 **관리자 권한**이며 별도 엔드포인트(Step 6 Admin) 책임이다.

---

## 2. 사용자 시나리오

```
As a   로그인 사용자
I want 내가 제출했지만 아직 노출되지 않은 (또는 반려된) 레시피를 취소하고
So that 잘못 올린 글을 정리하거나 재제출할 수 있다
```

---

## 3. API 스펙

### 3.1 엔드포인트

| Method | Path | 인증 | 설명 |
|---|---|---|---|
| `DELETE` | `/api/recipes/{id}` | 필수 (USER) | 본인 소유 `pending_recipe` 삭제 — `id` 는 `pending_recipe_id` |

> v1 과 경로는 동일. **`id` 의 의미가 `recipe_id` → `pending_recipe_id` 로 변경됨.**

### 3.2 Request

- Path: `id` — `pending_recipes.pending_recipe_id`
- Body 없음

### 3.3 Response

성공 (HTTP 204 No Content) — 본문 없음

실패:

| HTTP | code | 언제 |
|---|---|---|
| 401 | `UNAUTHORIZED` | 토큰/쿠키 없음·만료 |
| 403 | `FORBIDDEN` | 본인 소유가 아님 |
| 404 | `PENDING_RECIPE_NOT_FOUND` | 존재하지 않는 id 또는 이미 삭제됨 |

> v1 의 `RECIPE_NOT_FOUND` (404) 는 **`PENDING_RECIPE_NOT_FOUND`** 로 갱신.

---

## 4. 비즈니스 규칙

1. **하드 삭제** — soft delete 안 함. (V4 의 `pending_recipes.is_active` 는 사용자 일시 취소 / 관리자 메모용 보존 등 다른 용도. 본 엔드포인트는 row 자체를 제거.)
2. **본인만 삭제 가능**. `pending_recipes.user_id == JWT sub`. 일치하지 않으면 403.
3. status 무관 — PENDING / REJECTED 모두 본인 의지로 삭제 가능. APPROVED 상태로 row 가 남아있는 경우(드뭄)도 삭제 가능 (이미 `recipes` 로 이동된 사본은 별도).
4. 삭제로 인한 cascade 영향 없음 — `pending_recipes` 는 다른 테이블에서 참조되지 않는다 (`scraps`/`likes` 는 `recipes` 만 참조).
5. v1 의 `session_logs.selected_recipe_id` 선행 NULL 처리 코드는 **폐기** — `session_logs` 테이블 자체가 V4 에서 사라짐.

---

## 5. 데이터 모델 영향

### 5.1 변경되는 테이블

| 테이블 | 변경 |
|---|---|
| `pending_recipes` | DELETE |

### 5.2 트랜잭션 경계

`@Transactional`: 단일 DELETE.

---

## 6. 외부 의존성

없음.

---

## 7. 권한·보안

- 인증 필수 (USER)
- 권한: `pending_recipes.user_id == currentUserId`. 일치하지 않으면 403
- 인증 통로: `Authorization: Bearer <JWT>` 또는 HttpOnly Cookie

---

## 8. 성능·확장

- 호출 빈도: 낮음. 추가 고려 불필요.

---

## 9. 테스트 케이스

- [ ] 본인 제출 삭제 → 204, DB 에서 제거
- [ ] 타인 제출 삭제 시도 → 403 `FORBIDDEN`
- [ ] 존재하지 않는 `pending_recipe_id` → 404 `PENDING_RECIPE_NOT_FOUND`
- [ ] 토큰/쿠키 없음 → 401
- [ ] PENDING / REJECTED 상태 모두 삭제 가능
- [ ] 삭제 후 `/api/recipes/my` 에서 안 보임

---

## 10. 결정 사항

- v1 의 cascade 검증 (`recipes` 의 `recipe_stats` / `scraps` / `likes` CASCADE 동시 삭제) 는 본 엔드포인트와 무관해짐. 그 검증은 Step 6 의 admin 레시피 삭제 엔드포인트로 이전.
- 관리자가 사용자 레시피를 강제 삭제하는 흐름은 본 엔드포인트가 아닌 별도 엔드포인트 (`DELETE /api/admin/recipes/{recipeId}` 또는 `/api/admin/pending-recipes/{pendingRecipeId}`) 로 분리.

---

## 11. 범위 밖

- 휴지통 / 복구
- 관리자 강제 삭제 (Step 6)
- 삭제 알림
- `recipes` 의 본인 삭제 — 정책상 본인 사용자가 `recipes` 에 직접 INSERT 하지 않으므로 본인 삭제 권한도 없음. 재정정이 필요하면 관리자 협의.
