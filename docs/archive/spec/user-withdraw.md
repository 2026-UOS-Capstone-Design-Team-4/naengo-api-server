# SPEC-20260503-07: 회원 탈퇴 (익명화)

## 0. 메타

| 항목 | 값 |
|---|---|
| 명세서 ID | `SPEC-20260503-07` |
| 도메인 | User |
| 기능명 | 본인 회원 탈퇴 (익명화 방식) |
| 작성자 | API 서버 담당자 |
| 작성일 | 2026-05-03 |
| 우선순위 | P0 |
| 관련 명세서 | `SPEC-20260503-04` ~ `SPEC-20260503-06`, `SPEC-20260502-03` (탈퇴 사용자 닉네임 치환) |
| 정책 출처 | `docs/api-server-tasks.md §5` "회원 탈퇴 시 ... 익명화" 결정 |

---

## 1. 목적

사용자가 서비스 이용을 종료하고 싶을 때 PII 를 제거하면서도 작성한 레시피 / 활동 기록의 데이터 정합성은 유지해야 한다. 행 자체를 삭제하면 `recipes.author_id` FK / 작성 이력이 깨지므로, **users 행은 보존하되 PII 만 nullify** 하는 익명화 모델을 채택했다.

---

## 2. 사용자 시나리오

```
As a   로그인 사용자
I want 내 계정을 탈퇴하여
So that 더 이상 서비스에 노출되지 않고 PII 를 제거할 수 있다
```

---

## 3. API 스펙

### 3.1 엔드포인트

| Method | Path | 인증 | 설명 |
|---|---|---|---|
| `DELETE` | `/api/users/me` | 필수 (USER) | 본인 탈퇴 (익명화) |

### 3.2 Request

- Body 없음.
- (보안 강화 필요 시 `currentPassword` 를 받을 수 있음 — MVP 에서는 토큰 보유만으로 충분히 본인 확인된 것으로 간주)

### 3.3 Response

성공 (HTTP 204 No Content) — 본문 없음

실패:

| HTTP | code | 언제 |
|---|---|---|
| 401/403 | `UNAUTHORIZED` | 토큰 없음 |
| 409 | `ALREADY_WITHDRAWN` | 이미 탈퇴된 사용자 (`deleted_at IS NOT NULL`) — 토큰이 살아있는 비정상 상황 |

---

## 4. 비즈니스 규칙

### 4.1 익명화 트랜잭션 (한 트랜잭션 안에서 모두 실행)

`users` 행에 다음 갱신:
- `email = NULL` — PII 제거
- `password_hash = NULL` — 자체 로그인 차단
- `provider_id = NULL` — 소셜 로그인 차단
- `nickname = '탈퇴한 사용자_<user_id>'` — UNIQUE 제약 회피용 꼬리표. 응답 시점에 `AuthorDisplayName` 이 꼬리표 제거하여 `"탈퇴한 사용자"` 로 노출
- `is_blocked = true` — 재로그인 시도 차단 (이론상 가능)
- `is_active = false` — 활성 사용자 카운트에서 제외 (V4 신규)
- `deleted_at = NOW()` — 탈퇴 시각

### 4.2 같은 트랜잭션에서 정리되는 부속 데이터

| 테이블 | 처리 | 이유 |
|---|---|---|
| `scraps` | DELETE WHERE user_id=? | 의미 없는 기록 + DB 트리거가 `recipe_stats.scrap_count` 자동 감소 |
| `likes` | DELETE WHERE user_id=? | 동일. 트리거가 `recipe_stats.likes_count` 자동 감소 |
| `pending_recipes` | DELETE WHERE user_id=? | 사용자 작성 본문에 PII 가능성. 익명화 모델과 일관되게 제거 |
| `user_profiles` | DELETE WHERE user_id=? | 전부 PII / 선호도 |
| `recipes` (author=본인) | **보존**. `author_id` 그대로 둠. 응답 시점에 닉네임 치환 | 다른 사용자가 본 레시피를 스크랩/좋아요 했을 수 있음. 데이터 가치 보존 + author FK 가 ON DELETE SET NULL 이지만 user 행을 안 지우므로 fk 트리거 발화 안 됨 |
| `chat_rooms`, `chat_messages` | **AI 서버 합의 전까지 보류** | AI 서버가 primary writer. 두 서버 간 책임 / 시점 합의 필요. 본 PR 에서 손대지 않음 |

### 4.3 후처리

- 응답 후 클라이언트는 토큰 / 쿠키를 폐기해야 함 (서버는 stateless JWT 라 강제 무효화 불가; 다른 디바이스의 토큰은 만료까지 유효)
- 향후 보안 강화 시: 탈퇴 시각 이후 발급된 JWT 라도 `is_blocked=true` 또는 `deleted_at IS NOT NULL` 인 사용자는 거부하는 가드를 `JwtAuthenticationFilter` 에 추가 가능 (별도 PR)

---

## 5. 데이터 모델 영향

### 5.1 변경되는 테이블

| 테이블 | 변경 |
|---|---|
| `users` | UPDATE (PII nullify + flag toggle + deleted_at) |
| `scraps` / `likes` / `pending_recipes` / `user_profiles` | DELETE WHERE user_id=? |
| `recipe_stats` | 트리거가 카운터 자동 감소 (likes/scraps DELETE 발화) |
| `recipes` | 변경 없음 (행 보존) |

### 5.2 트랜잭션 경계

`@Transactional` 단일 트랜잭션. 위 모든 처리가 atomic.

---

## 6. 외부 의존성

- AI 서버: **합의 필요** — 탈퇴 사용자의 `chat_rooms` / `chat_messages` 처리 시점·주체. 본 PR 에서는 미처리 (`api-server-tasks.md §5` 보류 항목으로 등록).

---

## 7. 권한·보안

- 인증 필수
- `user_id` 는 JWT 에서 추출 (Path / Body 로 받지 않음 → 다른 사용자 탈퇴 위조 차단)
- 응답 Body 없음

---

## 8. 성능·확장

- 호출 빈도: 매우 낮음
- 영향 범위가 크므로 (4~5 테이블 DELETE) 트랜잭션 시간이 길어질 수 있음 → 본인 활동량이 매우 큰 사용자(스크랩/좋아요 수만 건) 케이스 모니터링 필요
- 트리거가 N건 likes 의 카운터를 N번 UPDATE 함 → 성능 우려 시 TRUNCATE 류 일괄 처리로 추후 최적화

---

## 9. 테스트 케이스

- [ ] 정상 탈퇴 → 204, DB 상태:
  - `users.deleted_at IS NOT NULL`
  - `users.email IS NULL`, `password_hash IS NULL`, `provider_id IS NULL`
  - `nickname = '탈퇴한 사용자_<user_id>'`
  - `is_blocked = true`, `is_active = false`
- [ ] 탈퇴 후 본인의 `scraps` / `likes` row 삭제됨
- [ ] 탈퇴 후 본인의 `recipe_stats` 영향 받은 레시피의 카운터 정확히 감소 (트리거 검증)
- [ ] 탈퇴 후 본인의 `pending_recipes` row 삭제됨
- [ ] 탈퇴 후 본인이 작성했던 `recipes` row 는 그대로 (author_id 도 그대로)
- [ ] 탈퇴 후 같은 이메일로 신규 가입 가능 (NULL 이라 UNIQUE 충돌 없음)
- [ ] 토큰 없이 호출 → 401/403
- [ ] 이미 탈퇴된 사용자 토큰으로 재호출 → 본 endpoint 직접 호출 시 409 `ALREADY_WITHDRAWN` 가 정의돼 있으나, 실 동작은 **`CustomUserDetailsService` 의 `is_blocked` 체크가 먼저 발화하여 403** 이 떨어진다. 방어 in depth 로 수용 (사용자는 동일하게 차단됨).

---

## 10. 결정 사항

- `pending_recipes` 삭제 채택: 본문에 PII 가능성. 보존하면 익명화 모델 약해짐.
- `recipes` 보존 + 닉네임 치환: 데이터 가치 / 다른 사용자의 활동 보존이 우선.
- `chat_*` 보류: AI 서버 책임 / 합의 항목.
- `currentPassword` 미요구: 토큰 보유 = 본인 확인 가정. 탈취 토큰 시나리오 방어 필요해지면 추가.
- 닉네임 꼬리표 형식 `'탈퇴한 사용자_<user_id>'`: UNIQUE 회피 + 응답 시 꼬리표 제거 → 사용자에게는 `"탈퇴한 사용자"` 로 보임.

---

## 11. 범위 밖

- 탈퇴 철회 (grace period 동안 복구) — MVP 미제공
- 탈퇴 사유 수집
- 탈퇴 알림 (이메일)
- AI 서버 측 채팅 데이터 파기 동기화 (합의 필요)
- 탈퇴 시점 이후 다른 디바이스 토큰 강제 무효화 (별도 PR)
