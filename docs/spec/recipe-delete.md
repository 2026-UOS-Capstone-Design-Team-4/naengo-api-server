# SPEC-20260422-04: 레시피 삭제 (작성자 본인)

## 0. 메타

| 항목 | 값 |
|---|---|
| 명세서 ID | `SPEC-20260422-04` |
| 도메인 | Recipe |
| 기능명 | 본인이 쓴 레시피 삭제 |
| 작성자 | API 서버 담당자 |
| 작성일 | 2026-04-22 |
| 우선순위 | P0 |
| 관련 명세서 | `SPEC-20260422-02`, `SPEC-20260422-03` |

---

## 1. 목적

레시피는 정책상 **수정이 불가능**하다. 오탈자·잘못된 정보 정정의 유일한 경로가 "삭제 후 재작성" 이므로, 본인 삭제 API 는 작성 기능의 짝으로 필수다.

---

## 2. 사용자 시나리오

```
As a   로그인 사용자
I want 내가 쓴 레시피를 삭제하고
So that 잘못 올린 글을 지우거나 재작성할 수 있다
```

---

## 3. API 스펙

### 3.1 엔드포인트

| Method | Path | 인증 | 설명 |
|---|---|---|---|
| `DELETE` | `/api/recipes/{id}` | 필수 (USER) | 본인 소유 레시피 삭제 |

### 3.2 Request

- Path: `id`
- Body 없음.

### 3.3 Response

성공 (HTTP 204 No Content) — 본문 없음.

실패:

| HTTP | code | 언제 |
|---|---|---|
| 401 | `UNAUTHORIZED` | 토큰 없음/만료 |
| 403 | `FORBIDDEN` | 본인 소유가 아님 |
| 404 | `RECIPE_NOT_FOUND` | 존재하지 않는 id 또는 이미 삭제됨 |

---

## 4. 비즈니스 규칙

1. 삭제는 **하드 삭제**. soft delete 하지 않는다 (레시피 수정 불가 정책과 일관).
2. **본인만 삭제 가능**. ADMIN 의 삭제는 별도 엔드포인트 (Step 6, 반려·강제 삭제와 분리).
3. 상태(PENDING/APPROVED/REJECTED) 무관하게 삭제 가능.
4. FK CASCADE 에 의해 `recipe_stats`, `scraps`, `likes` 가 같이 삭제된다.
5. `session_logs.selected_recipe_id` 는 `REFERENCES recipes(recipe_id)` 로 FK 가 있으나 `ON DELETE` 지정이 없어 **기본값 NO ACTION** → 참조 중인 session 이 있으면 삭제 실패할 수 있음.
   - 처리: 삭제 트랜잭션에서 먼저 `UPDATE session_logs SET selected_recipe_id = NULL WHERE selected_recipe_id = ?` 실행 후 `DELETE FROM recipes WHERE recipe_id = ?`.
   - 또는 V4 마이그레이션으로 `ON DELETE SET NULL` 추가 — 본 명세 범위 밖으로 두고 코드에서 처리.

---

## 5. 데이터 모델 영향

### 5.1 변경되는 테이블

| 테이블 | 변경 |
|---|---|
| `recipes` | DELETE |
| `recipe_stats` | CASCADE 삭제 |
| `scraps` | CASCADE 삭제 |
| `likes` | CASCADE 삭제 |
| `session_logs` | `selected_recipe_id = NULL` 로 UPDATE (사전 처리) |

### 5.2 트랜잭션 경계

`@Transactional`: session_logs UPDATE + recipes DELETE 를 한 트랜잭션.

---

## 6. 외부 의존성

- AI 서버: 호출하지 않음. 삭제된 레시피가 RAG 인덱스에 남을 수 있으나, RAG 검색 결과는 DB 존재 여부로 다시 검증되므로 큰 문제 없음 (AI 팀과 소프트 정합 필요 — 보류).

---

## 7. 권한·보안

- 인증 필수.
- 권한 체크: `recipes.author_id == currentUserId`. 일치하지 않으면 403.

---

## 8. 성능·확장

- 호출 빈도: 낮음. 특별한 고려 불필요.

---

## 9. 테스트 케이스

- [ ] 본인 레시피 삭제 → 204, DB 에서 제거
- [ ] 타인 레시피 삭제 시도 → 403
- [ ] 존재하지 않는 id → 404
- [ ] 토큰 없음 → 401
- [ ] 좋아요/스크랩이 있는 레시피 삭제 → CASCADE 로 같이 삭제 확인
- [ ] session_logs 가 참조 중인 레시피 삭제 → 성공(NULL 처리됨)

---

## 10. 결정 사항

- ADMIN 의 강제 삭제는 별도 경로(Step 6). 본 엔드포인트는 "본인" 한정으로 권한 단순화.

---

## 11. 범위 밖

- 휴지통 / 복구
- 관리자 삭제
- 삭제 알림
