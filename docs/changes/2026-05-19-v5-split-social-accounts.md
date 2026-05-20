# V5 — users / social_accounts 분리 (2026-05-19)

> 팀원 요청 반영 — 일반/소셜 유저 모델 정규화. 한 user 가 여러 소셜을 동시에
> link 할 수 있는 1:N 구조로 전환. LOCAL 가입자는 social_accounts 에 row 없음.

## 1. 결정 한 줄

> 카카오 식별자(`provider`, `provider_id`)는 더 이상 `users` 컬럼이 아니다.
> 별도 `social_accounts` 테이블의 `(provider, provider_user_id)` 로 link.

## 2. 스키마 변화

| | V4 까지 (`users` 단일) | V5 (분리) |
|---|---|---|
| 소셜 식별 키 | `users.provider`+`users.provider_id` | `social_accounts.provider`+`provider_user_id` |
| LOCAL 가입자 | `users.provider='LOCAL'`, provider_id NULL | `social_accounts` row 없음 |
| 한 user ↔ 여러 소셜 link | 불가 (1행 = 1 provider) | 가능 (UNIQUE(user_id, provider) 만 막음) |
| 동일 외부 계정 ↔ 두 user | 불가 | 불가 (UNIQUE(provider, provider_user_id)) |
| 마이그레이션 | — | `provider != 'LOCAL'` 행을 social_accounts 로 이전 후 users 컬럼 DROP |

## 3. 코드 변화 (요지)

- **`User` 엔티티**: `provider`/`providerId` 필드·`uq_provider_provider_id` 제약 제거. `anonymize()` 의 `providerId = null` 도 함께 제거(컬럼 없음). 나머지 모두 유지.
- **신규 `SocialAccount` 엔티티 + `SocialAccountRepository`** (`findByProviderAndProviderUserId` / `findByUserId` / `deleteAllByUserId`).
- **`UserRepository`**: `findByProviderAndProviderId` 제거 (소셜 조회는 `SocialAccountRepository` 가 담당).
- **`SocialAuthService.processLogin`**: 1) `social_accounts` 에서 link 조회 → 있으면 그 user 로그인 → 차단 검사 → JWT. 2) 없으면 이메일 충돌 검사 → `users` 새 행 + `social_accounts` link 행 + `user_profiles` 빈 행 동시 생성 → JWT. 모두 단일 트랜잭션.
- **`UserMeService`**:
  - `getMe`/`updateMe`: `UserMeResponse.from(user, resolveProvider(userId))` — `social_accounts` 조회 결과로 provider 표기 결정 (`LOCAL` or `KAKAO`).
  - `changePassword`: `user.getProvider() != LOCAL` 검사 → **`user.getPasswordHash() == null`** 검사로 교체 (의미 동일, 컬럼 의존 제거).
  - `withdraw`: `socialAccountRepository.deleteAllByUserId(userId)` 추가 — 같은 외부 계정 재가입 시 신규 user 로 분리.
- **`UserMeResponse.from(user)` → `.from(user, provider)`** (DTO 외형 불변, 인자만 변경).
- **`AuthProvider` enum 유지** (`LOCAL`, `KAKAO`) — 외부 API 응답 표기용으로 계속 사용. DB 컬럼과는 분리됨.

## 4. 계약 영향 (외부 클라이언트)

**없음.** `UserMeResponse` 의 JSON 외형 그대로 (`provider: "LOCAL" | "KAKAO"`). front 의 user.dart 파서/admin/AI 모두 무영향. 로그인 endpoint 도 동일.

## 5. 테스트

- 통합테스트 **30/30 PASS** (Auth 8 / Cors 4 / ProfileChat 3 / Recipe 6 / RequestId 4 / SocialAuth 5).
- `SocialAuthIntegrationTest.userCountByProviderId` 헬퍼: `users.provider_id` → `social_accounts.provider_user_id` 쿼리로 정정.

## 6. 운영/배포 영향

- prod RDS 에는 V5 가 새로 적용됨. **V4 와 동일하게 RDS 사전 점검 필수** — 다른 DDL 로 `users.provider` / `users.provider_id` 가 이미 다른 형태로 변형돼 있으면 V5 의 `DROP COLUMN` 가 실패할 수 있음.
- AI 팀이 `users` 를 직접 참조하는 코드가 `provider` 컬럼을 읽고 있다면, 그 쪽도 동시에 `social_accounts` 조회로 전환해야 함 (공유 DB 컨텍스트).

## 7. 미래 확장

- `GOOGLE`/`NAVER` 추가 시: V?? 마이그레이션으로 `CHECK (provider IN (...))` 확장 + `AuthProvider` enum 에 추가 + 신규 OAuthClient 구현 + `SocialAuthService` 의 라우팅만 늘리면 됨. user 모델은 무영향.
- "기존 LOCAL 계정에 카카오 link" 같은 계정 통합 기능은 신규 endpoint `POST /users/me/social/link` 추가로 가능 (현 구조가 이미 지원).
