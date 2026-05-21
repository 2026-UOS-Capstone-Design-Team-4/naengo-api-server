# SPEC-20260503-02: 스크랩 토글

## 0. 메타

| 항목 | 값 |
|---|---|
| 명세서 ID | `SPEC-20260503-02` |
| 도메인 | Engagement (Scrap) |
| 기능명 | 레시피 스크랩 토글 |
| 작성자 | API 서버 담당자 |
| 작성일 | 2026-05-03 |
| 우선순위 | P0 |
| 관련 명세서 | `SPEC-20260503-01` (좋아요 토글), `SPEC-20260503-03` (스크랩 목록) |

---

## 1. 목적 (Why)

좋아요와 달리 스크랩은 **개인 모음** 의 의미를 가진다 (북마크). 사용자는 나중에 다시 보고 싶은 레시피를 모아두고 `/api/scraps/my` 에서 자기만의 컬렉션으로 조회한다. 토글 동작은 좋아요와 동일한 패턴을 따라 일관된 UX 를 보장한다.

V4 에서 카운터는 DB 트리거(`trigger_scrap_count`)가 책임지므로, 애플리케이션은 INSERT / DELETE 만.

---

## 2. 사용자 시나리오

```
As a   로그인 사용자
I want 레시피 카드의 북마크 버튼을 눌러
So that 내 스크랩 모음에 추가하거나 (이미 추가했다면) 빼낼 수 있다
```

---

## 3. API 스펙

### 3.1 엔드포인트

| Method | Path | 인증 | 설명 |
|---|---|---|---|
| `POST` | `/api/recipes/{id}/scrap` | 필수 (USER) | 스크랩 토글 |

### 3.2 Request

- Path: `id` — `recipes.recipe_id`
- Body 없음

### 3.3 Response

성공 (HTTP 200):
```json
{
  "success": true,
  "data": {
    "scrapped":   true,
    "scrapCount": 5
  }
}
```

- `scrapped`: 토글 후 상태 (`true` / `false`)
- `scrapCount`: 토글 후 `recipe_stats.scrap_count` 최신 값

실패:

| HTTP | code | 언제 |
|---|---|---|
| 401 | `UNAUTHORIZED` | 토큰 / 쿠키 없음·만료 |
| 404 | `RECIPE_NOT_FOUND` | 존재하지 않는 `recipe_id` 또는 `is_active = false` |

---

## 4. 비즈니스 규칙

1. 토글 동작 — `(user_id, recipe_id)` 존재 → DELETE / 미존재 → INSERT.
2. 대상은 `recipes` 의 `is_active = true` 만. `pending_recipes` 는 스크랩 대상 아님.
3. 자기 글 스크랩 허용 (사용자가 자기 레시피를 북마크할 수 있다).
4. **카운터 직접 갱신 금지**. `recipe_stats.scrap_count` 는 V1 트리거 `trigger_scrap_count` 단독 책임.
5. 동시성 — `uq_scraps_user_recipe` UNIQUE 가 race 안전망. INSERT 시 `DataIntegrityViolationException` 은 "이미 스크랩 상태" 로 간주.
6. 응답의 `scrapCount` 는 트리거 발화 후 값. `entityManager.flush()` 로 강제 발화 후 재조회.

---

## 5. 데이터 모델 영향

### 5.1 변경되는 테이블

| 테이블 | 변경 |
|---|---|
| `scraps` | INSERT 또는 DELETE |
| `recipe_stats` | 트리거가 `scrap_count` ±1 |

### 5.2 트랜잭션 경계

`@Transactional`: scrap INSERT/DELETE → flush → recipe_stats 조회. 단일 트랜잭션.

---

## 6. 외부 의존성

없음.

---

## 7. 권한·보안

- 인증 필수 (USER)
- 인증 통로: `Authorization: Bearer <JWT>` 또는 HttpOnly Cookie
- `user_id` 는 JWT `sub` 에서 추출

---

## 8. 성능·확장

- 호출 빈도: 중간 (좋아요보다는 낮지만 충분히 자주)
- 인덱스: `idx_scraps_user_id` + `uq_scraps_user_recipe`
- 트리거 오버헤드: 무시 가능

---

## 9. 테스트 케이스

- [ ] 정상 토글 (없음 → 스크랩): 200, `scrapped=true`, `scrapCount` += 1
- [ ] 정상 토글 (스크랩 → 취소): 200, `scrapped=false`, `scrapCount` -= 1
- [ ] 존재하지 않는 recipe_id → 404
- [ ] is_active=false → 404
- [ ] 토큰 없이 → 401
- [ ] 본인 레시피 스크랩 → 200
- [ ] 5회 연속 토글 → 0/1 정확 왕복
- [ ] 스크랩 후 `/api/scraps/my` 응답에 등장 (별도 SPEC-20260503-03 검증)

---

## 10. 결정 사항

- 좋아요와 응답 스키마를 일치시키지 않은 이유: 의미적 구분 (`liked` vs `scrapped`). 클라이언트가 두 토글 응답을 구별하기 쉬움.
- 스크랩 추가 알림은 도입하지 않음 (사적 행위).

---

## 11. 범위 밖

- 스크랩 모음별 폴더링 (Pinterest 의 보드)
- 비공개 / 공개 토글
- "스크랩 취소" 단독 endpoint
