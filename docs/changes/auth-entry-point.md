# Auth EntryPoint / AccessDeniedHandler 도입 — 401/403 일관 응답

## 0. 메타

| 항목 | 값 |
|---|---|
| 변경 이력 ID | `auth-entry-point` |
| 대상 | `global/auth/JwtAuthenticationEntryPoint.java`, `global/auth/JwtAccessDeniedHandler.java`, `global/config/SecurityConfig.java` |
| 변경 종류 | 보안 설정 / 일관성 확보 |
| 발견 경로 | Step 3 / 4 / 5 / 6 의 "알려진 미흡" — 미인증 시 401 명세였으나 실제는 403 |
| 작성자 | API 서버 담당자 |
| 작성일 | 2026-05-04 |

---

## 1. 무엇이 문제였나

Spring Security 의 기본 동작:
- 토큰 없이 `authenticated()` endpoint 호출 → **403 Forbidden** + 빈 body
- USER 가 ADMIN endpoint 호출 → **403 Forbidden** + 빈 body

명세서 (`SPEC-20260503-04` 등) 는 미인증 시 **401 UNAUTHORIZED** 를 정의했으나 실 동작과 정합되지 않음. 또한 응답 body 가 비어 있어 클라이언트가 메시지 파싱 일관성 확보 불가.

---

## 2. 어떻게 바꿨나

### 신규 컴포넌트
- `JwtAuthenticationEntryPoint implements AuthenticationEntryPoint`
  - 미인증 진입 시점 (Spring Security 의 `ExceptionTranslationFilter` 가 호출) → 401 + `ApiResponse.fail("로그인이 필요합니다.")` JSON 응답
- `JwtAccessDeniedHandler implements AccessDeniedHandler`
  - 인가 실패 (`AccessDeniedException`) → 403 + `ApiResponse.fail("접근 권한이 없습니다.")` JSON 응답

### SecurityConfig 등록
```java
.exceptionHandling(eh -> eh
        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
        .accessDeniedHandler(jwtAccessDeniedHandler))
```

### Jackson 호환성
Spring Boot 4 + Jackson 3 환경에서는 `com.fasterxml.jackson.databind.ObjectMapper` 빈이 자동 등록되지 않음. 두 핸들러 모두 `tools.jackson.databind.json.JsonMapper` (Jackson 3) 정적 인스턴스를 사용하여 의존성 최소화.

---

## 3. 영향 / 명세 정정

명세서 본문은 그대로 (모두 401 로 적혀 있어 이제 실 동작과 정합). 단, 다음 위치의 "알려진 미흡" / "Spring 기본 403" 메모는 본 PR 로 해소:

- `docs/spec/user-me-get.md §3.3` — `토큰/쿠키 없음 (현 동작은 Spring 기본 403; entry point 미구현)` 메모는 더 이상 현행 아님 (다음 spec 갱신 시 정리)
- `docs/api-server-tasks.md §6 Step 3 알려진 미흡` — 본 변경 이력으로 해소 표시
- Step 4 / 5 / 6 의 "401/403" 표기 — 이제 모두 401 로 통일 동작

---

## 4. 회귀 테스트

- [x] 토큰 없이 `GET /api/users/me` → **401** + `{"message":"로그인이 필요합니다.","success":false}`
- [x] USER 토큰으로 `GET /api/admin/pending-recipes` → **403** + `{"message":"접근 권한이 없습니다.","success":false}`
- [x] 정상 인증 + 인가 → 기존과 동일하게 200
- [x] 잘못된 / 만료된 토큰 → 401 (`JwtAuthenticationFilter` 가 SecurityContext 비우면 EntryPoint 가 발화)

---

## 5. 후속

- 비밀번호 변경 시 기존 토큰 강제 무효화 (refresh + blacklist) — 별도 PR
- 차단(`is_blocked=true`) 사용자가 살아있는 토큰으로 호출 시: `CustomUserDetailsService` 가 USER_BLOCKED 예외를 던지면 `JwtAuthenticationFilter` 가 catch → context clear → EntryPoint 가 401 발화. 명세상 403 USER_BLOCKED 와 다른 동작이 될 수 있음. 후속 정리 시 EntryPoint 가 원인 분석해 다른 status / message 로 응답하도록 분기 검토.
