# SPEC-20260507-01: 인증 쿠키 (HttpOnly) — JWT 발급/만료 + 헤더 병행 지원

## 0. 메타

| 항목 | 값 |
|---|---|
| 명세서 ID | `SPEC-20260507-01` |
| 도메인 | Auth (인증 통로 — 인프라성) |
| 기능명 | JWT 를 HttpOnly Cookie 로 발급/만료 + Authorization 헤더 병행 지원 |
| 작성자 | API 서버 담당자 |
| 작성일 | 2026-05-07 |
| 우선순위 | P0 |
| 합의 출처 | `docs/api-server-tasks.md §3 인증 흐름 갱신` (2026-05-02) |
| 관련 명세서 | 모든 보호 endpoint (signup/login/social/me/recipes/admin/...) — 본 명세는 횡단 인증 통로 |

---

## 1. 목적 (Why)

기존 인증 통로는 `Authorization: Bearer <JWT>` 헤더 단일 지원. 이번 합의 (`api-server-tasks.md §3`) 로 **HttpOnly Cookie 도 병행 지원**:

- **브라우저** — 자동으로 쿠키 동봉. JS 가 토큰을 직접 다루지 않으므로 XSS 시 토큰 유출 위험 감소
- **모바일 / 외부 서버** — 기존 `Authorization` 헤더 그대로 사용

본 spec 은 두 통로의 발급·전달·만료 정책을 캡처. 응답 schema 는 변경 없이 (모든 인증 endpoint 가 기존처럼 body 의 `accessToken` 도 노출), 추가로 `Set-Cookie` 헤더가 함께 떨어진다.

---

## 2. 사용자 시나리오

```
As a   브라우저 사용자
I want 로그인 후 별도 토큰 저장 / 헤더 부착 없이도 자동으로 인증된 요청을 보내고
       탈퇴/로그아웃 시 서버가 쿠키를 즉시 만료시켜 다른 탭에서도 차단되게 한다

As a   모바일 클라이언트
I want 응답 body 의 accessToken 을 받아 SecureStorage 에 저장하고
       이후 Authorization 헤더로 부착 (변경 없음)
```

---

## 3. 동작 스펙

### 3.1 발급 시점 — 인증 endpoint 응답

| Endpoint | Body | Set-Cookie |
|---|---|---|
| `POST /api/auth/signup` | `AuthResponse{userId, nickname, role, accessToken}` | `NAENGO_AT=<jwt>; HttpOnly; ...` |
| `POST /api/auth/login` | 동상 | 동상 |
| `POST /api/auth/social/kakao` | 동상 | 동상 |
| `POST /api/auth/social/google` | 동상 | 동상 |

### 3.2 만료 시점 — Set-Cookie value="" + Max-Age=0

| Endpoint | 동작 |
|---|---|
| `POST /api/auth/logout` | 쿠키 만료. 인증 없이도 호출 가능 (멱등). 응답 204 |
| `DELETE /api/users/me` (탈퇴) | 익명화 트랜잭션 + 쿠키 만료. 응답 204 |

### 3.3 검증 — 모든 보호 endpoint

`JwtAuthenticationFilter` 가 다음 순서로 토큰 추출:
1. `Authorization: Bearer <token>` 헤더 (있으면 우선)
2. 위가 없으면 쿠키 `NAENGO_AT` (이름은 `auth.cookie.name` 설정)

추출 후 검증·로딩은 기존과 동일.

### 3.4 쿠키 속성

| 속성 | 값 | 비고 |
|---|---|---|
| Name | `${auth.cookie.name}` (기본 `NAENGO_AT`) | env override 가능 |
| Value | JWT | base64url 형식. 길이 길음 |
| HttpOnly | true | JS 에서 접근 불가 (XSS 방어) |
| Secure | local: false / prod: true | HTTPS 필수 |
| SameSite | Lax (기본) / Strict / None | 운영에서 strict 가 필요하면 환경변수로 |
| Path | `/` | 전 endpoint 적용 |
| Max-Age | 발급: `${auth.cookie.max-age-seconds}` (기본 86400 = JWT 만료와 일치) / 만료: 0 | |
| Domain | (선택) | `${auth.cookie.domain}` 설정 시. 기본 비움 (호스트 한정) |

---

## 4. 비즈니스 규칙

1. **응답 body 의 `accessToken` 은 그대로 유지** — 모바일 호환 + 기존 클라이언트 깨짐 방지.
2. **헤더 우선 순위** — 쿠키와 헤더가 동시에 오면 헤더의 토큰만 사용 (모바일이 의도적으로 cookieless 동작하는 케이스 보호).
3. **로그아웃 멱등** — 쿠키 없는 호출도 200 + Max-Age=0 쿠키 발급 (no-op). stateless JWT 라 서버가 토큰 자체를 무효화하지는 않는다.
4. **탈퇴 시 자동 만료** — `DELETE /api/users/me` 가 익명화 트랜잭션 후 쿠키 만료 헤더 동봉. 사용자가 별도로 logout 호출 안 해도 다음 요청에서 쿠키 부재.
5. **비밀번호 변경 시점 무효화 미적용** — stateless JWT 한계. 만료 전까지 다른 디바이스 토큰은 살아있음. 후속 PR 에서 refresh + blacklist 도입 시 재검토 (`auth-entry-point.md §5` 후속).
6. **CSRF** — `SameSite=Lax` 가 기본 보호. 운영에서 `Strict` 까지 강화 가능. 명시적 CSRF 토큰은 미적용 (REST API + JSON body 만 받음).

---

## 5. 데이터 모델 영향

없음. 쿠키는 stateless.

---

## 6. 외부 의존성

없음. AI 서버는 자체 secret 으로 JWT 검증 (`Phase 0-1` 합의). API 서버의 쿠키 정책 변경은 AI 서버에 영향 없음.

---

## 7. 권한·보안

- HttpOnly 로 XSS 시 토큰 유출 방어
- Secure (prod) 로 HTTPS 강제
- SameSite=Lax 로 CSRF 1차 방어
- 로그에 쿠키 값(JWT) 마스킹 / 미출력

---

## 8. 성능·확장

- 영향 거의 없음. ResponseCookie 빌드는 가벼움.
- JwtAuthenticationFilter 의 추가 분기 (헤더 → 쿠키) 도 O(1) 수준.

---

## 9. 테스트 케이스

### 발급
- [ ] `POST /api/auth/signup` → 201 + `Set-Cookie: NAENGO_AT=<jwt>; ...; HttpOnly; SameSite=Lax; Path=/`
- [ ] `POST /api/auth/login` 동상 → 200
- [ ] `POST /api/auth/social/kakao` 동상 → 200
- [ ] body 의 `accessToken` 과 쿠키 value 가 일치 (둘 다 같은 JWT)

### 검증
- [ ] 헤더만 보내면 통과 (기존 그대로) → 200
- [ ] 쿠키만 보내면 통과 → 200
- [ ] 헤더 + 쿠키 동시 → 헤더만 사용
- [ ] 만료된 JWT 가 쿠키에 있어도 → 401 (필터가 invalid 처리)
- [ ] 토큰 모두 없음 → 401 (`JwtAuthenticationEntryPoint`)

### 만료
- [ ] `POST /api/auth/logout` 쿠키 보유 상태 → 204 + `Set-Cookie: NAENGO_AT=; Max-Age=0; ...`
- [ ] `POST /api/auth/logout` 쿠키 없는 상태 → 204 (멱등, no-op 쿠키)
- [ ] `DELETE /api/users/me` (탈퇴) → 204 + `Max-Age=0` 쿠키 + DB 익명화

### 환경 분리
- [ ] local: `Secure=false`
- [ ] prod: `Secure=true` (env)
- [ ] `AUTH_COOKIE_DOMAIN=.example.com` 설정 시 쿠키 헤더에 `Domain=.example.com` 포함

---

## 10. 결정 사항

- **모바일 호환**: 응답 body 의 accessToken 유지 — 기존 클라이언트 깨짐 방지
- **헤더 우선**: 쿠키와 헤더가 동시에 오면 헤더 사용 (의도적 cookieless 동작 보호)
- **logout endpoint**: stateless JWT 라 토큰 자체 무효화는 안 함. 쿠키 만료만 함
- **refresh token 미도입**: 본 PR 스코프 외. 향후 보안 강화 시 별도 spec
- **CSRF token 미도입**: SameSite=Lax + REST/JSON 한정으로 충분. 필요해지면 별도 spec

---

## 11. 범위 밖

- Refresh token / 토큰 회전
- 토큰 블랙리스트 (탈퇴/비밀번호 변경 시 즉시 무효화)
- CSRF token
- 다중 디바이스 세션 관리 / 강제 로그아웃
- AI 서버 측 쿠키 발급 (AI 는 stateless API, 자체 secret 으로 JWT 검증만)
