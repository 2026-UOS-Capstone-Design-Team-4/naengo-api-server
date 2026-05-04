# SPEC-20260504-03: 관리자 — 사용자 차단 / 차단 해제

## 0. 메타

| 항목 | 값 |
|---|---|
| 명세서 ID | `SPEC-20260504-03` |
| 도메인 | Admin |
| 기능명 | 사용자 `is_blocked` 토글 |
| 작성자 | API 서버 담당자 |
| 작성일 | 2026-05-04 |
| 우선순위 | P0 |
| 관련 명세서 | `SPEC-20260504-01`, `SPEC-20260504-02` |

---

## 1. 목적

악성 사용자(스팸 / 부적절 콘텐츠 / 신고 누적) 를 막기 위해 관리자가 즉시 차단할 수 있어야 한다. 이미 존재하는 `User.block()` / `User.unblock()` 도메인 메서드를 endpoint 로 노출.

---

## 2. 사용자 시나리오

```
As a   관리자
I want 신고가 들어온 사용자를 차단하여
So that 더 이상 로그인 / 활동을 할 수 없게 한다 (또는 잘못 차단됐을 경우 해제)
```

---

## 3. API 스펙

### 3.1 엔드포인트

| Method | Path | 인증 | 설명 |
|---|---|---|---|
| `POST` | `/api/admin/users/{userId}/block`   | 필수 (ADMIN) | 차단 |
| `POST` | `/api/admin/users/{userId}/unblock` | 필수 (ADMIN) | 차단 해제 |

### 3.2 Request

- Body 없음

### 3.3 Response

성공 (HTTP 200):
```json
{
  "success": true,
  "data": {
    "userId":    7,
    "isBlocked": true
  }
}
```

실패:

| HTTP | code | 언제 |
|---|---|---|
| 401/403 | UNAUTHORIZED / FORBIDDEN | 토큰 / role |
| 404 | `USER_NOT_FOUND` | 존재하지 않는 userId |
| 409 | `ALREADY_WITHDRAWN` | 이미 탈퇴된 사용자 (`deleted_at IS NOT NULL`) — 차단 / 해제 의미 없음 |

---

## 4. 비즈니스 규칙

1. **멱등** — 이미 차단된 사용자에게 block 호출 → 200, `isBlocked=true` (no-op).
2. 동일하게 unblock 도 멱등.
3. 탈퇴된 사용자(`deleted_at IS NOT NULL`) 는 이미 `is_blocked=true` 이고 영구 비활성. 토글 endpoint 는 409 `ALREADY_WITHDRAWN`.
4. 자기 자신 차단 / 자기 자신 해제는 허용 (관리자 실수도 가능; 명세 단순화).
5. **토큰 무효화는 별도 보장 없음** — 차단된 사용자가 살아있는 JWT 로 호출 시:
   - `CustomUserDetailsService.loadUserByUsername` 의 `is_blocked` 체크가 발화 → 403
   - stateless JWT 의 일반적 한계. 즉시 무효화가 필요하면 별도 PR (refresh + blacklist).
6. 응답에 `userId` + `isBlocked` 만 노출. 다른 사용자 정보는 별도 endpoint(있다면).

---

## 5. 데이터 모델 영향

| 테이블 | 변경 |
|---|---|
| `users` | UPDATE `is_blocked` |

---

## 6. 외부 의존성

없음.

---

## 7. 권한·보안

- `hasRole("ADMIN")` (SecurityConfig)
- 자기 자신 차단 가드 미적용 (관리자 책임 영역)

---

## 8. 성능·확장

- 호출 빈도: 매우 낮음

---

## 9. 테스트 케이스

- [ ] 정상 차단 → 200, `isBlocked=true`. 해당 사용자가 살아있는 토큰으로 endpoint 호출 시 다음 요청부터 403
- [ ] 정상 해제 → 200, `isBlocked=false`
- [ ] 이미 차단된 사용자에게 block → 200 (멱등)
- [ ] 이미 미차단 사용자에게 unblock → 200 (멱등)
- [ ] 존재하지 않는 userId → 404
- [ ] 탈퇴 사용자 (deleted_at IS NOT NULL) → 409 `ALREADY_WITHDRAWN`
- [ ] USER 토큰 → 403

---

## 10. 결정 사항

- 멱등 동작 — 별도 `ALREADY_BLOCKED` 에러 미정의 (ergonomic).
- 차단 사유 / 이력은 미저장 (MVP). 감사 로그가 필요해지면 별도 테이블 + 엔드포인트.

---

## 11. 범위 밖

- 차단 사유 / 이력 저장
- 자동 차단 (신고 누적 임계치)
- 일시 차단 / 해제 시각 예약
- 차단 시점 즉시 토큰 무효화
