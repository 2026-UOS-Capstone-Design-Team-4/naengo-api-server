# SPEC-20260503-09: 채팅방 메시지 내역 조회

## 0. 메타

| 항목 | 값 |
|---|---|
| 명세서 ID | `SPEC-20260503-09` |
| 도메인 | Chat (read-only) |
| 기능명 | 특정 채팅방의 메시지 시간순 조회 |
| 작성자 | API 서버 담당자 |
| 작성일 | 2026-05-03 |
| 우선순위 | P0 |
| 관련 명세서 | `SPEC-20260503-08`, `SPEC-20260502-03` (레시피 조회 v2), `SPEC-20260502-01` (AI contract) |

---

## 1. 목적

사용자가 과거 채팅방을 다시 열었을 때, AI 와 주고받은 메시지를 시간순으로 보여줘야 한다. AI 서버 `GET /api/v1/chat/rooms/{room_id}` 와 동일한 의미·schema 의 read-only 엔드포인트를 우리 API 서버에서도 제공한다 (마이페이지 / 알림 등에서 직접 호출 가능).

---

## 2. 사용자 시나리오

```
As a   로그인 사용자
I want 특정 채팅방의 모든 메시지(내가 보낸 + AI 답변 + 추천 레시피) 를 시간순으로 보고
So that 과거 추천 결과를 다시 확인하거나 이어서 채팅할 수 있다
```

---

## 3. API 스펙

### 3.1 엔드포인트

| Method | Path | 인증 | 설명 |
|---|---|---|---|
| `GET` | `/api/chat/rooms/{roomId}/messages` | 필수 (USER) | 본인 채팅방의 메시지 시간순 |

### 3.2 Request

- Path: `roomId` (= `chat_rooms.room_id`)
- Body / Query 없음 — 페이징 미적용 (한 채팅방 내 메시지 수가 제한적이라 가정)

### 3.3 Response

성공 (HTTP 200):
```json
{
  "success": true,
  "data": {
    "items": [
      {
        "messageId": 1,
        "role":      "user",
        "content":   "김치랑 두부 있는데 뭐 만들 수 있어?",
        "recipes":   null,
        "createdAt": "2026-04-29T12:00:00+09:00"
      },
      {
        "messageId": 2,
        "role":      "model",
        "content":   "김치두부찌개를 추천드려요!",
        "recipes": [
          {
            "recipeId":       42,
            "title":          "김치두부찌개",
            "description":    "칼칼하고 깊은 맛.",
            "imageUrl":       "https://.../abc.jpg",
            "authorNickname": "요리왕",
            "authorType":     "ADMIN",
            "difficulty":     "easy",
            "cookingTime":    20,
            "likesCount":     12,
            "scrapCount":     3,
            "createdAt":      "2026-04-22T14:30:00+09:00"
          }
        ],
        "createdAt": "2026-04-29T12:00:05+09:00"
      }
    ]
  }
}
```

- `role`: `"user"` / `"model"` (AI 서버 contract 와 동일 — 소문자)
- `recipes`: 메시지에 추천이 있었으면 `RecipeListItemResponse[]`, 없으면 `null`
- 페이로드 절약을 위해 채팅 안에서는 **약식**(RecipeListItemResponse) 를 노출. 클라이언트가 자세히 보려면 `GET /api/recipes/{id}` 호출

실패:

| HTTP | code | 언제 |
|---|---|---|
| 401/403 | `UNAUTHORIZED` | 토큰 없음 |
| 403 | `FORBIDDEN` | 본인 채팅방이 아님 |
| 404 | `CHAT_ROOM_NOT_FOUND` | 존재하지 않는 `roomId` 또는 본인의 비활성(`is_active=false`) 채팅방 |

---

## 4. 비즈니스 규칙

1. `chat_rooms.user_id == currentUserId` 검증. 일치하지 않으면 403 `FORBIDDEN`. 본인 권한 검증 후 메시지 조회.
2. 본인 소유라도 `is_active = false` 면 404 `CHAT_ROOM_NOT_FOUND` (UI 에 노출되지 않는 상태).
3. 메시지 정렬: `created_at ASC` (오래된 순) — 채팅 흐름의 자연스러운 순서.
4. **`recipes` 필드 매핑**:
   - `chat_messages.recipe_ids JSONB` (정수 배열) → 한 페이지 메시지의 모든 recipe_id 를 모아 한 번에 `recipes` 조회 (N+1 방지)
   - `recipes.is_active = true` 인 것만 노출. 비활성 레시피는 응답에서 제외 (UI 에서 "삭제된 레시피" 표시는 미적용)
   - `recipe_ids = NULL` 또는 빈 배열 → 응답 `recipes: null`
   - 작성자 닉네임은 `RecipeListMapper` 와 같은 패턴 (탈퇴 사용자 → `"탈퇴한 사용자"`)
5. 페이징 미적용 (MVP). 채팅이 너무 길어져 페이로드가 커지면 cursor 기반 페이징 도입.
6. **본 endpoint 는 읽기 전용**. `chat_messages` 의 INSERT 는 AI 서버 책임 (Phase 0-2 합의).

---

## 5. 데이터 모델 영향

읽기 전용.

---

## 6. 외부 의존성

없음 (AI 서버 호출 없음. AI 가 쓴 데이터를 우리 DB 에서 직접 읽음).

---

## 7. 권한·보안

- 인증 필수
- 본인 소유 검증 필수
- 응답에 다른 사용자 메시지 누출 금지

---

## 8. 성능·확장

- 호출 빈도: 채팅방 진입 시 1회. 중간.
- 인덱스: `idx_chat_messages_room_id` (V1)
- 한 채팅방의 메시지 수가 매우 많지 않다고 가정 (대부분 < 100 개). 큰 채팅방을 위한 페이징은 후속.
- recipe lookup 은 일괄 조회 + JOIN FETCH `RecipeStats` (RecipeListMapper 의 N+1 방지 패턴 그대로 사용)

---

## 9. 테스트 케이스

- [ ] 토큰 없이 → 401/403
- [ ] 본인 채팅방 → 200, 메시지 시간순
- [ ] 다른 사용자 채팅방 → 403 `FORBIDDEN`
- [ ] 존재하지 않는 `roomId` → 404 `CHAT_ROOM_NOT_FOUND`
- [ ] 본인 비활성(`is_active=false`) 채팅방 → 404
- [ ] role=`user` 메시지의 `recipes`는 항상 null (사용자 입력엔 추천 없음)
- [ ] role=`model` + `recipe_ids = [1,2,3]` → 응답 `recipes` 에 활성 레시피만 매핑. inactive 한 건은 제외
- [ ] role=`model` + `recipe_ids = NULL` → 응답 `recipes: null`
- [ ] 메시지 100건 × `recipe_ids` 평균 2건 → 단일 SQL 로 RecipeListItemResponse 일괄 매핑 (N+1 없음)

---

## 10. 결정 사항

- 응답의 `recipes` 는 **약식** (`RecipeListItemResponse`) 노출 — 풀 detail 보다 페이로드 적음. AI 서버 응답(풀 RecipeResponse) 과 schema 가 약간 다르지만, 채팅 화면에서 카드 표시에 충분
- inactive 레시피는 응답에서 제외 — "이 레시피가 삭제됐습니다" 같은 표시는 후속 UX 결정
- 페이징 미적용 — MVP 단순화. 큰 채팅방 케이스는 후속 cursor 페이징 도입 시점에 결정

---

## 11. 범위 밖

- 메시지 검색 / 필터
- cursor / offset 페이징
- 메시지 삭제 (사용자 권한)
- 메시지 react (좋아요 / 별점)
- AI 의 추천 metadata (점수 / 근거) — `chat_messages` 에 컬럼 없음
- 비활성 레시피 placeholder 표시
