# SPEC-20260503-06: 비밀번호 변경 (LOCAL provider 한정)

## 0. 메타

| 항목 | 값 |
|---|---|
| 명세서 ID | `SPEC-20260503-06` |
| 도메인 | User |
| 기능명 | 본인 비밀번호 변경 |
| 작성자 | API 서버 담당자 |
| 작성일 | 2026-05-03 |
| 우선순위 | P0 |
| 관련 명세서 | `SPEC-20260503-04` ~ `SPEC-20260503-07` |

---

## 1. 목적

자체 가입(LOCAL) 사용자가 비밀번호를 갱신할 수 있어야 한다. 소셜 가입 사용자는 비밀번호가 없으므로(`password_hash IS NULL`) 본 endpoint 의 대상 아님 — 명시적으로 거부.

---

## 2. 사용자 시나리오

```
As a   자체 가입한 (LOCAL) 사용자
I want 현재 비밀번호 + 새 비밀번호를 입력해 비밀번호를 갱신하고
So that 보안을 유지하거나 잊은 비밀번호를 새 값으로 바꾼다
```

---

## 3. API 스펙

### 3.1 엔드포인트

| Method | Path | 인증 | 설명 |
|---|---|---|---|
| `POST` | `/api/users/me/password` | 필수 (USER, provider=LOCAL) | 비밀번호 변경 |

### 3.2 Request

**Body**
```json
{
  "currentPassword": "string, 1~64자, 필수",
  "newPassword":     "string, 8~64자, 영문+숫자 포함, 필수"
}
```

### 3.3 Response

성공 (HTTP 204 No Content) — 본문 없음

실패:

| HTTP | code | 언제 |
|---|---|---|
| 400 | `INVALID_INPUT` | 새 비밀번호 형식 오류 (길이/패턴) |
| 401/403 | `UNAUTHORIZED` | 토큰 없음 |
| 403 | `SOCIAL_PASSWORD_NOT_ALLOWED` | 소셜 로그인 사용자가 호출 (provider != LOCAL) |
| 401 | `INVALID_CREDENTIALS` | currentPassword 가 일치하지 않음 |

---

## 4. 비즈니스 규칙

1. **provider 가 LOCAL 인 사용자만 호출 가능**. KAKAO/GOOGLE 사용자가 호출하면 403 `SOCIAL_PASSWORD_NOT_ALLOWED`.
2. `currentPassword` 검증을 가입 시와 동일한 `BCryptPasswordEncoder.matches` 로 수행. 불일치 시 401 `INVALID_CREDENTIALS` (이메일/비번 구분 안 함과 같은 보안 정책).
3. `newPassword` 검증: `SignUpRequest` 와 동일한 규칙 — 8~64자, 영문+숫자 각 1자 이상.
4. 같은 비밀번호로 변경 요청도 허용 (별도 검증 없음). 보안적으로 무의미하지만 차단할 강한 이유 없음.
5. 성공 시 새 BCrypt 해시로 `password_hash` 갱신. 한 트랜잭션.
6. 비밀번호 변경 시 **기존 발급된 JWT 무효화 정책 미적용** (MVP). 즉, 다른 디바이스에 살아있는 토큰은 만료 전까지 유효. 이는 stateless JWT 의 일반적 한계로, 강제 무효화가 필요해지면 별도 정책(refresh + blacklist) 도입.

---

## 5. 데이터 모델 영향

| 테이블 | 변경 |
|---|---|
| `users` | `UPDATE users SET password_hash = ? WHERE user_id = ?` |

---

## 6. 외부 의존성

없음. (이메일 알림 등은 범위 밖)

---

## 7. 권한·보안

- 인증 필수
- `currentPassword` 검증으로 본인임 재확인 (탈취 토큰 방어)
- 로그에 비밀번호 원문/해시 남기지 않음
- 응답 Body 없음 (정보 누출 최소화)
- `@Valid` 검증: 두 필드 모두 `@NotBlank`, `newPassword` 는 패턴 + 길이

---

## 8. 성능·확장

- 호출 빈도: 매우 낮음
- BCrypt 비용으로 응답 시간 ~수백 ms 가능 (정상)

---

## 9. 테스트 케이스

- [ ] LOCAL 사용자 정상 변경 → 204
- [ ] 변경 후 새 비밀번호로 로그인 → 200
- [ ] 변경 후 옛 비밀번호로 로그인 → 401 `INVALID_CREDENTIALS`
- [ ] currentPassword 불일치 → 401
- [ ] newPassword 길이 7자 → 400 `INVALID_INPUT`
- [ ] newPassword 영문만 / 숫자만 → 400
- [ ] 소셜(KAKAO/GOOGLE) 사용자 호출 → 403 `SOCIAL_PASSWORD_NOT_ALLOWED`
- [ ] 토큰 없이 → 401/403

---

## 10. 결정 사항

- 비밀번호 변경 시 강제 로그아웃(=토큰 무효화) 없음. MVP. 후속 보안 강화 시 도입.
- "비밀번호 찾기"(이메일 인증으로 임시 비밀번호 발급) 는 별도 spec.

---

## 11. 범위 밖

- 비밀번호 찾기 / 재설정 (forgot password)
- 2단계 인증
- 이메일 알림
- 비밀번호 정책 강화 (특수문자 필수, 사전 검사 등)
