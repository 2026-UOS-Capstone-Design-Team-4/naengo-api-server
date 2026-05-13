# 로깅 정책 — Step 8-3

## 0. 메타

| 항목 | 값 |
|---|---|
| 변경 이력 ID | `logging-policy` |
| 대상 | `global/logging/RequestIdFilter`, `JwtAuthenticationFilter` MDC 확장, `logback-spring.xml` |
| 변경 종류 | 인프라 / 운영 정책 |
| 작성자 | API 서버 담당자 |
| 작성일 | 2026-05-08 |

---

## 1. 무엇을 도입했나

### 1.1 요청 ID (Correlation ID)
- `X-Request-Id` 헤더 — 클라이언트가 보내면 그 값 사용 (분산 추적 호환), 없으면 UUID 자동 생성
- MDC `requestId` 로 put → 모든 로그 라인에 자동 포함
- 응답 헤더에도 같은 ID → 클라이언트가 디버깅 시 사용 가능
- 구현: `global/logging/RequestIdFilter.java` (`@Order(HIGHEST_PRECEDENCE)`)

### 1.2 사용자 ID (인증된 요청만)
- `JwtAuthenticationFilter` 가 인증 성공 시 MDC `userId` put
- 익명 요청은 `userId` 없음 → 패턴에서 `-` 표시
- 정리는 `RequestIdFilter` 의 `finally` 가 `MDC.clear()` 호출

### 1.3 로그 패턴 (`logback-spring.xml`)
```
%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level [%X{requestId:-no-req-id}] [user=%X{userId:--}] [%thread] %logger{36} - %msg%n
```

예:
```
2026-05-08 02:15:33.421 INFO  [a3e1b2c4-...] [user=42] [http-nio-8080-exec-3] c.n.a.RecipeService - Recipe approved: pendingId=17 → recipeId=99
2026-05-08 02:15:34.001 WARN  [b7c2d1e0-...] [user=-]  [http-nio-8080-exec-4] c.n.a.JwtAuthenticationFilter - JWT expired
```

---

## 2. PII 로그 금지 정책

다음 값은 **절대 로깅 / 응답 메시지 / 예외 메시지에 포함하지 않는다**:

| 분류 | 예시 |
|---|---|
| 자체 자격증명 | password 원문 / hash, currentPassword, newPassword |
| 토큰 | JWT 원문, 카카오/구글 access token, internal token |
| 식별 PII | email, provider_id (카카오/구글 user id), 전화번호(향후 추가 시) |
| 본문성 데이터 | 레시피 `content` / `description` 원문, 채팅 메시지 본문 |

### 허용 (PII 아님)
- `userId` (정수 internal ID) — MDC 에 자동 put. 응답에도 노출됨.
- `nickname` — 사용자 노출 표시명. 응답에는 보이지만 로그에는 권장 안 함 (정책 차원).
- `pendingRecipeId` / `recipeId` 등 — 순수 internal ID.

### 권장 패턴

```java
// Good
log.info("Recipe approved: pendingId={} → recipeId={}", pendingId, recipeId);

// Bad (PII 노출)
log.info("Recipe approved: user={} title={}", user.getEmail(), recipe.getTitle());

// Bad (본문 노출)
log.debug("Submitted recipe: {}", request);  // request 안에 content 가 있음
```

### 도구적 enforcement (현재 미적용)

지금은 코드 리뷰 + 본 문서로만 정책 운용. 향후 검토:
- ArchUnit 으로 `log.*(...)` 호출에 특정 필드 사용 금지 규칙
- Logback 의 mask filter (정규식 기반)
- 운영에서는 DEBUG/TRACE 끄는 것이 1차 방어

---

## 3. 검증 (`RequestIdIntegrationTest` 4건)

- [x] X-Request-Id 헤더 미포함 → UUID 생성 응답
- [x] 클라이언트 제공 ID → echo
- [x] 연속 요청 → 서로 다른 ID
- [x] CORS 응답과 동시에 노출

전체 통합 테스트: 19/19 PASS (Auth 8 + Recipe 3 + CORS 4 + RequestId 4).

---

## 4. 후속

- 운영에서 `application-prod.yml` 에 파일 appender 추가 (rolling, retention) — 별도 PR
- 로그 집계 (CloudWatch / Loki / ELK) 결정 시 JSON 포맷으로 전환 검토
- 비밀번호 변경 / 탈퇴 / 차단 같은 보안 이벤트는 별도 audit 로그 분리 검토
- 외부 호출 (AI 서버 / S3) 의 latency / error rate 메트릭은 Step 7 / 2-4b 시점에
