# SPEC-20260503-04: 내 정보 조회 (마이페이지)

## 0. 메타

| 항목 | 값 |
|---|---|
| 명세서 ID | `SPEC-20260503-04` |
| 도메인 | User |
| 기능명 | 본인 마이페이지 정보 조회 |
| 작성자 | API 서버 담당자 |
| 작성일 | 2026-05-03 |
| 우선순위 | P0 |
| 관련 명세서 | `SPEC-20260503-05` (수정), `SPEC-20260503-06` (비밀번호 변경), `SPEC-20260503-07` (탈퇴) |

---

## 1. 목적

마이페이지 첫 진입 시 사용자의 기본 정보(이메일/닉네임/가입 방식 등)를 한 번에 받아 화면에 띄우기 위함. 닉네임 수정 / 비밀번호 변경 / 탈퇴 같은 후속 동작의 기본 컨텍스트가 된다.

---

## 2. 사용자 시나리오

```
As a   로그인 사용자
I want 내 계정 정보(이메일, 닉네임, 로그인 방식, 가입일)를 한 화면에서 확인하고 싶다
```

---

## 3. API 스펙

### 3.1 엔드포인트

| Method | Path | 인증 | 설명 |
|---|---|---|---|
| `GET` | `/api/users/me` | 필수 (USER) | 본인 마이페이지 정보 |

### 3.2 Request

- Body 없음

### 3.3 Response

성공 (HTTP 200):
```json
{
  "success": true,
  "data": {
    "userId":    1,
    "email":     "a@b.c",
    "nickname":  "tester",
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
| 401 | `UNAUTHORIZED` | 토큰/쿠키 없음 (`JwtAuthenticationEntryPoint` 가 401 + ApiResponse 일관 응답 — `docs/changes/auth-entry-point.md`) |

---

## 4. 비즈니스 규칙

1. `user_id` 는 JWT `sub` 에서 추출. Path / Query 로 받지 않는다 (다른 사용자 조회는 본 endpoint 가 아님).
2. **선호도(preferences)** 는 본 응답에 포함하지 않는다. V4 에서 `user_profiles` 테이블로 분리되었고, 별도 endpoint (후속 spec) 가 책임진다.
3. 응답 필드:
   - `email`: 가입 시 이메일. 소셜 사용자는 placeholder 일 수 있음 (`kakao_<id>@social.naengo.com`)
   - `nickname`: 자체/소셜 가입 시 결정된 값
   - `role`: `USER` / `ADMIN`
   - `provider`: `LOCAL` / `KAKAO` / `GOOGLE`
   - `isActive`: 계정 활성화 여부 (탈퇴 시 false)
   - `createdAt`: 가입 시각
4. 탈퇴 처리된 본인이 호출하는 케이스는 사실상 발생하지 않음 (탈퇴 시 토큰이 더 이상 유효하지 않게 처리되므로). 만약 토큰이 살아있고 호출되면 401/403 을 반환하면 충분 (본 명세 범위 밖, JwtAuthenticationFilter / EntryPoint 책임).

---

## 5. 데이터 모델 영향

읽기 전용. 스키마 변경 없음.

---

## 6. 외부 의존성

없음.

---

## 7. 권한·보안

- 인증 필수 (USER)
- 인증 통로: `Authorization: Bearer <JWT>` 또는 HttpOnly Cookie (2026-05-02 합의)
- 응답에 `password_hash`, `provider_id` 등 민감 필드 노출 금지

---

## 8. 성능·확장

- 호출 빈도: 중간 (마이페이지 진입 시 1회)
- 단일 PK 조회. 추가 인덱스 불필요.

---

## 9. 테스트 케이스

- [ ] 정상 호출 → 200, 본인의 `userId/email/nickname/role/provider/isActive/createdAt`
- [ ] 토큰 없이 호출 → 401/403
- [ ] 응답에 `passwordHash`, `providerId`, `preferences` 부재
- [ ] 탈퇴 후 본인 토큰으로 호출 (가정) → 401/403 (별도 처리; 본 명세 범위 밖)

---

## 10. 결정 사항

- preferences 분리: V4 의 `user_profiles` 가 풍부한 컬럼으로 분리. 본 응답에 모두 노출하면 페이로드가 커지고, UI 도 별도 화면이라 분리.

---

## 11. 범위 밖

- 선호도(preferences) 조회 — 후속 spec
- 다른 사용자 프로필 조회
- 활동 통계 (작성 레시피 수, 좋아요 수 등)
