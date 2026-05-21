# SPEC-20260503-05: 내 정보 수정 (닉네임)

## 0. 메타

| 항목 | 값 |
|---|---|
| 명세서 ID | `SPEC-20260503-05` |
| 도메인 | User |
| 기능명 | 본인 닉네임 수정 |
| 작성자 | API 서버 담당자 |
| 작성일 | 2026-05-03 |
| 우선순위 | P0 |
| 관련 명세서 | `SPEC-20260503-04` (조회), `SPEC-20260503-06`, `SPEC-20260503-07` |

---

## 1. 목적

닉네임은 노출되는 표시명이므로 사용자가 자유롭게 바꿀 수 있어야 한다. 다른 식별 필드(`email`, `provider`, `provider_id`) 는 정책상 변경 불가 (소셜 로그인 정합 + 운영 추적 일관성).

---

## 2. 사용자 시나리오

```
As a   로그인 사용자
I want 내 닉네임을 새 값으로 바꾸어
So that 다른 사용자에게 다른 이름으로 보일 수 있다
```

---

## 3. API 스펙

### 3.1 엔드포인트

| Method | Path | 인증 | 설명 |
|---|---|---|---|
| `PATCH` | `/api/users/me` | 필수 (USER) | 본인 닉네임 수정 |

### 3.2 Request

**Body**
```json
{
  "nickname": "string, 2~20자, 필수"
}
```

### 3.3 Response

성공 (HTTP 200) — `SPEC-20260503-04` 의 `UserMeResponse` 와 동일 (갱신된 값 반환):
```json
{
  "success": true,
  "data": {
    "userId":    1,
    "email":     "a@b.c",
    "nickname":  "newName",
    "role":      "USER",
    "provider":  "LOCAL",
    "isActive":  true,
    "createdAt": "2026-04-22T14:00:00+09:00"
  }
}
```

실패:

| HTTP | code | 언제 |
|---|---|---|
| 400 | `INVALID_INPUT` | nickname 길이/형식 오류 |
| 401/403 | `UNAUTHORIZED` | 토큰 없음 |
| 409 | `NICKNAME_ALREADY_EXISTS` | 다른 사용자가 이미 사용 중인 닉네임 |

---

## 4. 비즈니스 규칙

1. **닉네임만** 수정 가능. `email` / `role` / `provider` / `provider_id` / `is_blocked` / `is_active` / `created_at` / `deleted_at` 은 본 endpoint 로 변경 불가 (DTO 에 필드 없음).
2. `nickname` 은 전역 UNIQUE — 충돌 시 409.
3. 같은 값으로 수정 요청해도 200 (멱등). DB 도 변경 없음.
4. `users.deleted_at IS NOT NULL` 인 사용자가 호출하면 403 (탈퇴된 사용자). 일반 흐름에서는 도달 불가.
5. 변경된 결과를 응답으로 반환 → 클라이언트가 별도 GET 안 해도 화면 갱신 가능.

---

## 5. 데이터 모델 영향

| 테이블 | 변경 |
|---|---|
| `users` | `UPDATE users SET nickname = ? WHERE user_id = ?` |

`@Transactional` 단일 UPDATE.

---

## 6. 외부 의존성

없음.

---

## 7. 권한·보안

- 인증 필수
- `user_id` 는 JWT 에서 추출
- `@Valid` 검증: `@NotBlank`, `@Size(min=2, max=20)`

---

## 8. 성능·확장

- 호출 빈도: 낮음 (닉네임은 자주 안 바꾼다)
- 추가 인덱스 불필요 (UNIQUE 인덱스 활용)

---

## 9. 테스트 케이스

- [ ] 정상 수정 → 200, 응답에 새 닉네임
- [ ] 동일 값 수정 → 200, 정상
- [ ] 다른 사용자가 쓰는 닉네임 → 409 `NICKNAME_ALREADY_EXISTS`
- [ ] 길이 1자 → 400 `INVALID_INPUT`
- [ ] 길이 21자 → 400
- [ ] 토큰 없이 → 401/403
- [ ] Body 에 `email`/`role` 보내도 무시 (DTO 에 필드 없으므로 자동 거부)

---

## 10. 결정 사항

- **선호도(preferences)** 는 본 endpoint 와 분리. `user_profiles` 가 별도 테이블·schema 이므로 별도 PR 에서 endpoint 신설.
- HTTP 메서드는 `PATCH` (부분 갱신). 향후 다른 필드(예: `bio`) 추가 가능성 대비.
- 닉네임 변경 이력 보존 안 함 (MVP 단순화).

---

## 11. 범위 밖

- 이메일 변경
- 비밀번호 변경 (별도 spec `SPEC-20260503-06`)
- 선호도 수정 (후속 spec)
- 프로필 사진
- 닉네임 변경 횟수/주기 제한
