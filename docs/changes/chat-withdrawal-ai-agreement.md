# 합의 안건서 — 회원 탈퇴 시 chat 자원 처리 (§6-2 / api3-alignment D-6)

> 대상: AI 서버 팀 ↔ API 서버 팀
> 목적: 탈퇴 사용자의 `chat_messages` PII 완전 제거 방식 합의
> 상태: **API 서버 측 폴백(soft delete) 이미 적용·검증 완료. 잔여(hard delete) 는 본 합의 대기.**

---

## 1. 배경

- `chat_rooms` / `chat_messages` 는 **AI 서버가 메시지 primary writer** (SSE 채팅 누적). DDL 은 API 서버 Flyway 관리, `chat_rooms.is_active` soft delete write 는 API 서버 권한(PR-7 / D-6 합의안).
- 회원 탈퇴(`DELETE /api/v1/users/me`)는 `users` 행을 **삭제하지 않고 익명화**(PII nullify(`username`/`password_hash`) + `is_active=false`, `is_blocked=true`) — `recipes.author_id` 정합 보존 목적. 따라서 `users` FK `ON DELETE CASCADE` 가 발화하지 않아 chat 행이 자동 정리되지 않음. (옵션 A 채택 2026-05-21 — `deleted_at` 컬럼 없음, `is_active=false` 가 탈퇴 표식 단일화)

## 2. 현재 상태 (API 서버, 적용 완료)

`UserMeService.withdraw` 트랜잭션에서:
- `chat_rooms` 전부 **soft delete** (`ChatRoomRepository.deactivateAllByUserId` → `is_active=false`).
- 효과: 우리 API(`GET /api/v1/chat/rooms`, `/rooms/{id}`)에서 즉시 비노출. 무충돌(우리 권한 내 컬럼).
- `chat_messages` **행·본문 보존** (AI primary writer 라 단독 삭제 안 함).
- 검증: 통합 `ProfileChatIntegrationTest.withdrawDeactivatesChatRooms` + 로컬 실서버 e2e(2026-05-18) — 방 2건 `is_active=false`(행 보존), 메시지 2건 보존.

→ **미해결 리스크**: `chat_messages.content` 는 사용자가 직접 입력한 자유 텍스트 = **PII**. 탈퇴 후에도 공유 DB 에 잔존. 엄밀한 익명화/개인정보 파기 관점에서 불완전.

## 3. 합의가 필요한 결정

**Q. 탈퇴 시 그 사용자의 `chat_messages` PII 를 어떻게 완전 제거할 것인가?**

| 옵션 | 방식 | 주체 | 영향 |
|---|---|---|---|
| **A (권장)** | API 서버가 탈퇴 트랜잭션에서 `DELETE FROM chat_rooms WHERE user_id=:id` → FK `chat_messages.room_id ON DELETE CASCADE` 로 메시지 동시 삭제 | API 서버 | API 서버가 `chat_rooms` 행을 hard delete (현재 soft 만). messages 는 **DB CASCADE** 가 지움(애플리케이션이 AI 테이블에 직접 명령 X). AI 서버가 해당 room/message 를 캐시·참조하지 않음을 확인 필요 |
| B | API 서버는 soft delete 유지, AI 서버가 탈퇴 이벤트/배치로 `chat_messages` 본문 스크럽 또는 삭제 | AI 서버 | 크로스서비스 트리거/배치 신설 필요(AI 작업) |
| C | 현행 유지(soft delete 만, 메시지 보존) | — | 개인정보 파기 미흡 — 정책상 비권장 |

**API 서버 팀 권고: 옵션 A.** 이유: ① 탈퇴 시 likes/scraps/pending/profile 은 이미 hard delete 중 — chat 만 예외 두는 비대칭 해소 ② messages 삭제를 애플리케이션이 직접 안 하고 **DB FK CASCADE** 가 처리 → AI primary-writer 경계 침범 최소 ③ 단일 statement, 추가 인프라 0.

## 4. 옵션 A 채택 시 — 즉시 적용 가능한 변경 스펙

> 코드 1개 메서드 교체 + 테스트 1개 수정. 합의 즉시 반영 가능.

- `ChatRoomRepository`: `deactivateAllByUserId`(soft) → 또는 병행하여
  ```java
  @Modifying
  @Query("DELETE FROM ChatRoom r WHERE r.userId = :userId")
  int deleteAllByUserId(@Param("userId") Long userId);
  ```
- `UserMeService.withdraw`: `chatRoomRepository.deactivateAllByUserId(userId)` → `chatRoomRepository.deleteAllByUserId(userId)`.
  - 전제 확인: 현행 `V1__init.sql` 에 `chat_messages.room_id ... REFERENCES chat_rooms(room_id) ON DELETE CASCADE` 존재 → rooms 삭제 시 messages 자동 삭제. (DBv3 정규화 채택 시에도 동일 FK 유지 필요 — AI 팀 스키마 합의 항목)
- 테스트: `ProfileChatIntegrationTest.withdrawDeactivatesChatRooms` →
  - `chat_rooms` user_id 조회 0건(행 삭제), `chat_messages` 0건(CASCADE) 로 단언 변경.
- 전제: **AI 서버가 삭제된 room/message 에 대한 외부 참조/캐시 없음** 확인 (있다면 옵션 B 로).

## 5. AI 서버 팀에 묻는 것 (회신 요청)

1. 탈퇴 사용자의 `chat_rooms`/`chat_messages` 를 API 서버가 **hard delete(CASCADE)** 해도 AI 서버 동작/정합에 문제 없는가? (메시지 행 참조 캐시/외부 인덱스 여부)
2. 없으면 → 옵션 A 채택, §4 스펙으로 즉시 반영.
3. 있으면 → 옵션 B: AI 서버가 탈퇴 시그널(어떤 채널? API 서버가 호출할 endpoint/이벤트)로 메시지 스크럽. 시그널 계약 정의 필요.
4. 무관 항목: `users` 행은 어느 경우든 보존(익명화). chat 정리만 대상.

## 6. 추적

- `docs/spec/user-domain-todo.md §6-2` 잔여
- `docs/spec/api3-alignment-and-integration.md` D-6
- 합의 시 본 문서에 결정·반영일 기록 후 §4 스펙 적용 PR.
