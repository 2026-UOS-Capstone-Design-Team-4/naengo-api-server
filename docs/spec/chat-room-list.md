# SPEC-20260503-08: 내 채팅방 목록 조회

## 0. 메타

| 항목 | 값 |
|---|---|
| 명세서 ID | `SPEC-20260503-08` |
| 도메인 | Chat (read-only) |
| 기능명 | 본인의 활성 채팅방 목록 |
| 작성자 | API 서버 담당자 |
| 작성일 | 2026-05-03 |
| 우선순위 | P0 |
| 관련 명세서 | `SPEC-20260503-09` (메시지 조회), `SPEC-20260502-01` (AI 서버 contract) |

---

## 1. 목적 (Why)

채팅 자체(SSE 스트리밍, 추천 호출) 는 AI 서버가 직접 처리하지만, 사용자의 **채팅방 목록 / 과거 내역** 은 마이페이지 류 UI 에서 우리 API 서버 쪽으로도 노출되는 것이 자연스럽다. 같은 DB 의 `chat_rooms` / `chat_messages` 를 read-only 로 보여준다.

응답 스키마는 AI 서버 OpenAPI 의 `ChatRoomResponse` 와 정합 (필드명·의미 동일).

---

## 2. 사용자 시나리오

```
As a   로그인 사용자
I want 내 채팅방을 마지막 활동순으로 보고
So that 과거 상담 / AI 추천 흐름을 다시 찾아갈 수 있다
```

---

## 3. API 스펙

### 3.1 엔드포인트

| Method | Path | 인증 | 설명 |
|---|---|---|---|
| `GET` | `/api/chat/rooms` | 필수 (USER) | 본인 활성 채팅방 목록 (페이징, `updated_at DESC`) |

> AI 서버의 `GET /api/v1/chat/rooms` 와 동일한 의미. 양 서버가 같은 DB 를 보고 동일 결과를 반환해야 한다 (스키마 일관).

### 3.2 Request

- Query: `page` (기본 0), `size` (기본 20, 최대 50)

### 3.3 Response

성공 (HTTP 200):
```json
{
  "success": true,
  "data": {
    "items": [
      {
        "roomId":    1,
        "title":     "김치랑 두부 있는데 뭐 만들 수 있어?",
        "createdAt": "2026-04-29T12:00:00+09:00",
        "updatedAt": "2026-04-29T12:05:00+09:00"
      }
    ],
    "page":          0,
    "size":          20,
    "totalElements": 1,
    "totalPages":    1
  }
}
```

실패:

| HTTP | code | 언제 |
|---|---|---|
| 401/403 | `UNAUTHORIZED` | 토큰 / 쿠키 없음 |
| 400 | `INVALID_INPUT` | 잘못된 page / size |

---

## 4. 비즈니스 규칙

1. 본인의 `chat_rooms.user_id == JWT sub` 행만 반환. 다른 사용자 채팅방 노출 금지.
2. **`is_active = true`** 인 채팅방만 (사용자가 숨김 처리한 것은 제외). 숨김 토글 endpoint 는 별도 spec (보류).
3. 정렬: `updated_at DESC` — 마지막 활동 순.
4. 응답 필드 (AI contract 정합):
   - `roomId` (int — V4 의 `chat_rooms.room_id BIGSERIAL`)
   - `title` — 채팅방 제목 (AI 서버가 첫 메시지로 자동 설정)
   - `createdAt` / `updatedAt`
5. 페이지네이션: `Pageable`. `size > 50` 은 50 으로 클램핑.
6. **본 endpoint 는 읽기 전용**. `chat_rooms` 의 INSERT / UPDATE / DELETE 는 AI 서버 책임 (Phase 0-2 합의).

---

## 5. 데이터 모델 영향

읽기 전용. 스키마 변경 없음.

---

## 6. 외부 의존성

없음 (AI 서버는 데이터를 쓰지만 본 endpoint 는 직접 호출하지 않는다).

---

## 7. 권한·보안

- 인증 필수
- `user_id` 는 JWT 에서 추출
- 응답에 다른 사용자 채팅방 row 가 섞이지 않도록 WHERE 필터 강제

---

## 8. 성능·확장

- 호출 빈도: 마이페이지 진입 시 1회. 낮음~중간.
- 인덱스 활용: `idx_chat_rooms_user_id`
- N+1 없음 (단일 테이블 조회).

---

## 9. 테스트 케이스

- [ ] 토큰 없이 → 401/403
- [ ] 본인 활성 채팅방 0건 → 200, `items=[]`
- [ ] 본인 N건 + 다른 사용자 M건 → 200, items 길이 = N
- [ ] `is_active = false` 인 본인 채팅방은 제외
- [ ] 정렬: 가장 최근 활동(`updated_at`) 이 첫 항목
- [ ] `size = 100` → 50 으로 클램프
- [ ] 응답 `roomId` 가 정수 (string 아님)

---

## 10. 결정 사항

- AI contract 의 `ChatRoomResponse` 와 동일 schema 채택 — 클라이언트가 양쪽(API/AI 서버) 응답을 동일하게 처리
- 마지막 메시지 미리보기 / 안 읽은 수 등은 **MVP 미포함** (UI 가 필요해지면 후속 spec)
- 5-2 (채팅방 숨김 토글) 은 AI 서버의 `DELETE /api/v1/chat/rooms/{id}` 와 권한 책임 합의 후 별도 spec

---

## 11. 범위 밖

- 채팅방 검색 / 필터
- 마지막 메시지 미리보기
- 채팅방 숨김 토글 (별도 spec, AI 서버 합의 후)
- 채팅방 생성 (AI 서버 책임)
