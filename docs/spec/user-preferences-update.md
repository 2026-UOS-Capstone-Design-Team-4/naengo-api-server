# SPEC-20260504-05: 본인 선호도 수정 (직접 입력 영역만)

## 0. 메타

| 항목 | 값 |
|---|---|
| 명세서 ID | `SPEC-20260504-05` |
| 도메인 | User (선호도) |
| 기능명 | `user_profiles` 의 사용자 직접 입력 필드 갱신 |
| 작성자 | API 서버 담당자 |
| 작성일 | 2026-05-04 |
| 우선순위 | P1 |
| 관련 명세서 | `SPEC-20260504-04` (조회) |

---

## 1. 목적

V4 의 `user_profiles` 는 두 종류 데이터로 나뉜다:
- **직접 입력** (사용자가 마이페이지에서 적음): `user_input`, `cooking_skill`, `preferred_cooking_time`, `serving_size`
- **AI 분석** (AI 서버가 채팅 컨텍스트로 채움): 알레르기 / 식이제한 / 선호·기피 재료 등

본 endpoint 는 **직접 입력 영역만** 갱신. AI 분석 필드는 보호 (사용자가 의도치 않게 덮어쓰지 못하게).

---

## 2. 사용자 시나리오

```
As a   로그인 사용자
I want "새우 알레르기 있어요", "매운 음식 좋아해요" 같은 자연어 문장과
       내 요리 실력 / 선호 조리시간 / 인분 수를 마이페이지에서 직접 등록·수정하여
So that AI 가 추천 시 이 정보를 컨텍스트로 사용해 더 적합한 레시피를 제안한다
```

---

## 3. API 스펙

### 3.1 엔드포인트

| Method | Path | 인증 | 설명 |
|---|---|---|---|
| `PUT` | `/api/users/me/profile` | 필수 (USER) | 직접 입력 영역 갱신. row 없으면 자동 INSERT |

### 3.2 Request

**Body** — 모든 필드 선택. 보낸 필드만 반영, 미포함 필드는 기존 값 유지:
```json
{
  "userInput":            ["새우 알레르기 있어요", "매운 음식 좋아해요"],
  "cookingSkill":         "easy",
  "preferredCookingTime": 30,
  "servingSize":          2.0
}
```

- `userInput` — 문자열 배열, 각 1~500자, 최대 50개
- `cookingSkill` — `easy` / `normal` / `hard` 또는 null
- `preferredCookingTime` — 양수 (분), 또는 null
- `servingSize` — 0 < x ≤ 99.9 (NUMERIC(4,1)), 또는 null

### 3.3 Response

성공 (HTTP 200) — `SPEC-20260504-04` 의 응답 schema 와 동일 (갱신된 전체):
```json
{
  "success": true,
  "data": {
    "userInput":            ["새우 알레르기 있어요", "매운 음식 좋아해요"],
    "cookingSkill":         "easy",
    "preferredCookingTime": 30,
    "servingSize":          2.0,
    "allergies":            [],
    "dietaryRestrictions":  [],
    ...
    "updatedAt":            "2026-05-04T12:00:00+09:00"
  }
}
```

실패:

| HTTP | code | 언제 |
|---|---|---|
| 400 | `INVALID_INPUT` | 길이/패턴/범위 오류 |
| 401 | `UNAUTHORIZED` | 토큰 없음 |
| 409 | `ALREADY_WITHDRAWN` | 탈퇴된 사용자 |

---

## 4. 비즈니스 규칙

1. **AI 분석 필드는 절대 갱신하지 않음** — DTO 자체에 필드 없음. 사용자가 Body 에 `allergies` 같은 키를 보내도 무시.
2. **upsert 동작** — `user_profiles` row 가 없으면 INSERT, 있으면 UPDATE. 사용자가 명시적으로 row 를 만들 필요 없음.
3. **부분 갱신** — Body 에 포함된 필드만 변경. 미포함 필드는 기존 값 유지 (직접 입력 4개 필드 한정).
4. `updated_at` 은 트리거나 application 단에서 NOW() 갱신.
5. 응답은 갱신 후 전체 row (조회 endpoint 와 동일 schema). 클라이언트가 별도 GET 안 해도 화면 갱신 가능.

---

## 5. 데이터 모델 영향

| 테이블 | 변경 |
|---|---|
| `user_profiles` | INSERT 또는 UPDATE (4개 필드) + `updated_at` |

`@Transactional` 단일 upsert.

---

## 6. 외부 의존성

없음. AI 서버에 알림 / 트리거 없음 (AI 가 다음 분석 사이클에 자체 반영).

---

## 7. 권한·보안

- 인증 필수
- DTO 에 AI 분석 필드 부재 — 잘못 보내도 자동 거부 (defense in depth)
- 응답에 다른 사용자 데이터 포함 금지

---

## 8. 성능·확장

- 호출 빈도: 매우 낮음

---

## 9. 테스트 케이스

- [ ] 토큰 없이 → 401
- [ ] 신규 사용자가 첫 PUT → 200, row INSERT, 보낸 필드 반영, 미포함 필드 default(null/빈배열)
- [ ] 기존 row 에 부분 갱신 (1개 필드만 보냄) → 200, 보낸 필드만 변경, 나머지 보존
- [ ] AI 분석 필드 (`allergies` 등) 를 Body 에 보내면 → 무시 (DTO 에 없음). 응답에 변경 없음
- [ ] `cookingSkill="impossible"` → 400 INVALID_INPUT
- [ ] `userInput` 51개 → 400
- [ ] `userInput` 항목 1자 → 400 (1~500자)
- [ ] `preferredCookingTime = -10` → 400
- [ ] `servingSize = 100` → 400 (NUMERIC(4,1) precision)
- [ ] `updatedAt` 이 응답에서 갱신됨

---

## 10. 결정 사항

- **AI 분석 필드 분리 보호** — 사용자 실수 / 악의적 PUT 으로 AI 분석 결과를 덮어쓰지 못하게 DTO 에서 제외.
- upsert 채택 — 별도 "프로필 생성" endpoint 없이 PUT 한 번으로 충분.
- HTTP 메서드 PUT — 본 endpoint 가 본인 프로필 전체 (직접 입력 영역) 의 idempotent 갱신.
- `userInput` 은 검증된 자연어 문장 배열. 정규식 / 키워드 추출은 AI 영역.

---

## 11. 범위 밖

- AI 분석 필드 수동 갱신 (정책상 제공 안 함)
- 프로필 사진 / 자기소개
- `recent_recipe_ids` 갱신 — AI 가 채팅에서 추천한 레시피를 자동 누적 (본 endpoint 무관)
- 갱신 시 AI 서버에 push (배치 / cron 으로 처리)
