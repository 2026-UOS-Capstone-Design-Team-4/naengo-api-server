# SPEC-20260504-02: 관리자 — 레시피 승인 / 반려

## 0. 메타

| 항목 | 값 |
|---|---|
| 명세서 ID | `SPEC-20260504-02` |
| 도메인 | Admin |
| 기능명 | `pending_recipes` 의 PENDING → APPROVED / REJECTED 상태 전이 |
| 작성자 | API 서버 담당자 |
| 작성일 | 2026-05-04 |
| 우선순위 | P0 |
| 관련 명세서 | `SPEC-20260504-01` (목록), `SPEC-20260502-03` (recipe-read v2) |
| 정책 출처 | `docs/api-server-tasks.md §6 Step 6`, `docs/spec/ai-server-contract.md §5.A #4` (V4 분리 설계) |

---

## 1. 목적 (Why)

V4 통합 후 사용자 제출 레시피는 `pending_recipes` 에 머무르며, **관리자 승인 시 `recipes` 로 이동**한다. 반려 시 사용자에게 사유(`admin_note`) 가 전달돼 재제출이 가능해야 한다.

`recipes` 로의 이동은 데이터 품질을 보장하는 게이트 — `recipes` 의 NOT NULL 컬럼이 모두 채워져 있어야 한다.

---

## 2. 사용자 시나리오

```
As a   관리자
I want PENDING 상태의 제출 레시피를 검토하고
       완성도가 충분하면 승인하여 공개 목록에 노출되게 하거나
       부족하면 반려 사유와 함께 거절하여 사용자가 다시 작성하도록 안내한다
```

---

## 3. API 스펙

### 3.1 엔드포인트

| Method | Path | 인증 | 설명 |
|---|---|---|---|
| `POST` | `/api/admin/pending-recipes/{id}/approve` | 필수 (ADMIN) | 승인 — `recipes` 로 INSERT + pending status=APPROVED |
| `POST` | `/api/admin/pending-recipes/{id}/reject`  | 필수 (ADMIN) | 반려 — pending status=REJECTED + 사유 기록 |

### 3.2 Request

**Approve** — Body 선택적:
```json
{
  "adminNote": "string, 0~1000자, 선택 — 승인 메모"
}
```

**Reject** — Body 필수:
```json
{
  "reason": "string, 1~1000자, 필수 — 사용자에게 보일 반려 사유"
}
```

### 3.3 Response

**Approve 성공** (HTTP 200):
```json
{
  "success": true,
  "data": {
    "pendingRecipeId": 17,
    "recipeId":        42,
    "status":          "APPROVED",
    "reviewedAt":      "2026-05-04T10:00:00+09:00"
  }
}
```

**Reject 성공** (HTTP 200):
```json
{
  "success": true,
  "data": {
    "pendingRecipeId": 17,
    "status":          "REJECTED",
    "adminNote":       "사진 화질이 너무 낮습니다. 재제출 부탁드립니다.",
    "reviewedAt":      "2026-05-04T10:00:00+09:00"
  }
}
```

실패:

| HTTP | code | 언제 |
|---|---|---|
| 401/403 | UNAUTHORIZED / FORBIDDEN | 토큰 / role |
| 404 | `PENDING_RECIPE_NOT_FOUND` | 존재하지 않는 id |
| 409 | `PENDING_RECIPE_NOT_REVIEWABLE` | status 가 이미 APPROVED / REJECTED (PENDING 만 검토 가능) |
| 400 | `INVALID_INPUT` | reject 의 reason 누락 / 길이 초과 / approve 시 필수 필드 누락 (아래 §4-2) |
| 422 | `PENDING_RECIPE_INCOMPLETE` | 승인 대상의 필수 필드(`description`, `ingredients`, `ingredients_raw`, `instructions`, `servings`, `cooking_time`, `difficulty`, `category`) 가 NULL / 빈 값 |

---

## 4. 비즈니스 규칙

### 4.1 Approve 트랜잭션 (한 트랜잭션)

1. `pending_recipes` 단건 조회. 없으면 404.
2. `status` 가 `PENDING` 이 아니면 409.
3. `recipes` 에 NOT NULL 인 필드들이 pending 에 모두 존재하는지 검증. 누락 시 422 `PENDING_RECIPE_INCOMPLETE` (아래 §4-2 목록).
4. `recipes` 에 INSERT — `is_active=true`, `author_type='USER'`, `author_id=pending.user_id`. `embedding` 은 NULL (AI 서버 옵션 B 채택 시 자체 cron 으로 채움).
5. `pending_recipes` UPDATE — `status='APPROVED'`, `admin_note=request.adminNote`, `reviewed_at=NOW()`. **row 보존** (사용자가 `/api/recipes/my` 에서 승인 결과를 본다).
6. 응답에 새로 INSERT 된 `recipe_id` 와 `pending_recipe_id` 를 함께 반환.

### 4.2 승인 시 필수 필드 (검증 누락 시 422)

V4 의 `recipes` 테이블 NOT NULL 컬럼:
- `title` (pending 도 NOT NULL → 항상 통과)
- `description`
- `ingredients` (JSONB, 비어있지 않은 배열)
- `ingredients_raw`
- `instructions` (JSONB, 비어있지 않은 배열)
- `servings`
- `cooking_time`
- `difficulty`
- `category` (JSONB, 비어있지 않은 배열)

`tags`, `tips` 는 default `'[]'` 로 NOT NULL 이므로 pending 에 없어도 빈 배열로 INSERT.

### 4.3 Reject 트랜잭션

1. `pending_recipes` 단건 조회. 없으면 404.
2. `status` 가 `PENDING` 이 아니면 409.
3. UPDATE — `status='REJECTED'`, `admin_note=request.reason`, `reviewed_at=NOW()`.
4. **`recipes` 는 변경 없음**.

### 4.4 RAG 인덱스 / 임베딩

- 본 endpoint 는 AI 서버를 호출하지 않는다 (Phase 0-3 의 옵션 B 잠정 채택 — AI 서버가 자체 cron 으로 `embedding IS NULL AND is_active=true` 를 주기 처리).
- 옵션 A (AI `/internal/embed` 신설) 로 합의가 변경되면 본 spec §4-1 의 5단계 직후에 비동기 호출 추가 (Step 7).

---

## 5. 데이터 모델 영향

| 테이블 | 변경 |
|---|---|
| `recipes` | INSERT (Approve 만) — `trigger_recipe_stats_create` 가 `recipe_stats(0,0)` 자동 생성 |
| `pending_recipes` | UPDATE (status / admin_note / reviewed_at) |

---

## 6. 외부 의존성

- AI 서버: 호출 없음 (옵션 B). Phase 0-3 합의 변경 시 본 spec 보강 (CL).

---

## 7. 권한·보안

- `hasRole("ADMIN")` (SecurityConfig)
- 응답에 사용자 PII 노출 금지

---

## 8. 성능·확장

- 호출 빈도: 매우 낮음 (관리자 수동)
- INSERT + UPDATE 단일 트랜잭션 — 트리거 제외하면 짧음

---

## 9. 테스트 케이스

### Approve
- [ ] 정상 (PENDING + 모든 필수 필드 보유) → 200, recipes 에 row 추가, recipe_stats(0,0) 생성, pending status=APPROVED
- [ ] 일부 필드 NULL → 422 `PENDING_RECIPE_INCOMPLETE`
- [ ] 이미 APPROVED → 409 `PENDING_RECIPE_NOT_REVIEWABLE`
- [ ] 이미 REJECTED → 409
- [ ] 존재하지 않는 id → 404
- [ ] USER 토큰 → 403

### Reject
- [ ] 정상 → 200, pending status=REJECTED + admin_note 저장 + reviewed_at 기록. recipes 변경 없음
- [ ] reason 누락 → 400
- [ ] reason 1001자 → 400
- [ ] 이미 APPROVED / REJECTED → 409
- [ ] 존재하지 않는 id → 404
- [ ] USER 토큰 → 403

### 후속 검증
- [ ] 승인 후 `/api/recipes` 공개 목록에 새 recipe 등장
- [ ] 승인 후 `/api/recipes/{recipeId}` 단건 조회 가능
- [ ] 승인 후 사용자의 `/api/recipes/my` 에서 해당 pending row 의 status=APPROVED 표시

---

## 10. 결정 사항

- **승인 후 pending_recipes 행 보존** — 사용자가 승인 결과를 자기 `/my` 에서 볼 수 있게 (UX). recipes 와 pending_recipes 는 분리 데이터.
- 일괄 승인 / 반려는 미지원 (필요해지면 후속).
- 승인 시 admin_note 는 선택 — 일반적으로 깔끔히 승인하지만 메모를 남길 수도 있음.
- 임베딩 호출은 AI 서버 옵션 B 잠정 채택으로 제외.

---

## 11. 범위 밖

- 승인 후 recipes 행 수정 / 삭제 (별도 admin endpoint 필요)
- 승인 시 admin 이 부족 필드 직접 보정 (override) — MVP 미지원. 사용자 재제출 유도.
- 알림 (이메일 / 푸시) — 별도 spec
- 임베딩 즉시 호출 (옵션 A 채택 시 Step 7)
