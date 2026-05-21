# API 서버 문서 인덱스

> 본 문서는 `docs/` 의 입구다. 어디서 무엇을 찾을지 한 페이지로 정리한다.
> 작업 시 **본 인덱스에 등장하는 문서만** 진실원본으로 본다. `docs/archive/` 의 문서는 history 보존 목적이고 현행 동작과 일치하지 않을 수 있다.

---

## 0. 한 줄 요약

API 서버는 앱(프론트) 과 1차로 마주하는 백엔드다. 책임은 (1) DB I/O 정본 관리, (2) 인증/인가 (자체 + 소셜), (3) AI 서버와의 통신. 자세한 정의는 [`api-server-tasks.md §0`](api-server-tasks.md).

---

## 1. 현재 상태 (2026-05-21)

| 영역 | 상태 |
|---|---|
| DB 스키마 | **DBv5 단일** — DB 팀원이 SQL 로 운영 RDS 에 직접 적용한 ground truth. 우리 Flyway 는 V1=DBv5 + `baseline-on-migrate=true`. AI 도 003/004/005 까지 적용. 양측 모두 같은 스키마 위. 자세히 [§3](#3-db-스키마) |
| 인증 | 자체 회원가입 / 로그인 (`username` 기반) / **카카오 소셜** / 쿠키 + JWT(HS512) dual. `JWT_SECRET` AI 팀과 동일 값 공유(C3, 전달 후 대기) |
| 도메인 구현 | User / Recipe / UserRecipe / Like / Scrap / Chat / Admin 모두 옵션 A 정합 완료. 통합테스트 30/30 PASS |
| 응답 규약 | 성공 = raw JSON (래퍼 없음). 실패 = `{"error":{"code","message","details"}}`. **전 요청·응답 JSON 키 snake_case** (Jackson `SNAKE_CASE`) |
| 클라이언트 | naengo-front (Flutter) / naengo-admin (React) — 둘 다 현재 naengo-ai(:8000) 직결. 우리 endpoint 는 front 가 미래에 호출 예정. contract = [`changes/2026-05-21-option-a-contract-diff.md`](changes/2026-05-21-option-a-contract-diff.md) |
| 운영 인프라 | ECR + ECS Fargate + ALB + Secrets Manager 운영 중. ALB DNS `naengo-api-server-alb-176175450.ap-northeast-2.elb.amazonaws.com`. 자세히 [`deploy-status.md`](deploy-status.md) |
| 테스트 | Testcontainers 통합 테스트 **30건 PASS** (Auth 8 / Cors 4 / ProfileChat 3 / Recipe 6 / RequestId 4 / SocialAuth 5) |

---

## 2. 문서 지도 (어디서 무엇을 보나)

### 2.1 출발점 / 진실원본

- **본 README** — 현행 상태, 인덱스, 진행 우선순위.
- [`auth-user-api.md`](auth-user-api.md) — **인증·사용자 API 가이드 (팀 공유용)**. 회원가입/로그인/카카오/로그아웃/마이페이지/탈퇴/프로필. 옵션 A 정합으로 갱신됨.
- [`changes/2026-05-21-option-a-contract-diff.md`](changes/2026-05-21-option-a-contract-diff.md) — **외부 API contract 진실원본**. 옵션 A 후 바뀐 5건 정리 + 변하지 않은 endpoint 표 + front 호환 체크리스트.
- [`api-server-tasks.md`](api-server-tasks.md) — 작업 카탈로그 (역사적 진행 + 현 상태).

### 2.2 운영 / 배포

- [`deploy-status.md`](deploy-status.md) — **운영 배포 진척/체크리스트/입력값** (다음 세션 재진입 0순위 문서).
- [`deploy-env.md`](deploy-env.md) — **운영 환경변수 주입 체크리스트 + ECS Fargate/Secrets Manager/GitHub OIDC 단계별 절차**.
- [`kakao-oauth-runbook.md`](kakao-oauth-runbook.md) — 카카오 OAuth 콘솔 설정 + 브라우저 e2e (로컬 e2e 2026-05-18 완료).
- [`db-testing-guide.md`](db-testing-guide.md) — 로컬 DB 기동 / Flyway 검증.

### 2.3 정책 / 살아있는 안건

- [`changes/auth-entry-point.md`](changes/auth-entry-point.md) — 401/403 일관 응답 정책.
- [`changes/logging-policy.md`](changes/logging-policy.md) — X-Request-Id, MDC, PII 로그 금지.
- [`changes/chat-withdrawal-ai-agreement.md`](changes/chat-withdrawal-ai-agreement.md) — 탈퇴 시 chat PII hard delete AI 합의 안건서 (회신 대기).
- [`spec/auth-cookie.md`](spec/auth-cookie.md) — JWT HttpOnly 쿠키 발급/만료 정책 (옵션 A 후도 정합).

### 2.4 옵션 A 채택의 외부 영향 분석 (history + 진실원본)

- [`changes/2026-05-19-ai-admin-snapshot-delta.md`](changes/2026-05-19-ai-admin-snapshot-delta.md) — ai/admin 신규 스냅샷 영향 분석. 옵션 A 채택으로 C1/C2/C4 안건 클로즈 (자세히 [`deploy-status.md §C`](deploy-status.md)).

### 2.5 템플릿

- [`spec-template.md`](spec-template.md) — 신규 명세 작성 시 복사.
- [`change-log-template.md`](change-log-template.md) — 명세 변경 시 change-log 발행.

---

## 3. DB 스키마

옵션 A 채택 (2026-05-21) 으로 우리 자체 진화한 V1~V5 는 폐기. 운영 RDS = DB 팀원이 SQL 로 적용한 DBv5 = 우리 신규 V1 = AI 의 schema.sql (003~005 마이그레이션 후) 모두 동일.

| 마이그레이션 | 내용 |
|---|---|
| `V1__init.sql` | DBv5 전체 (broken 참조 2건만 정정). `users`(username/password_hash/nickname/role/is_active/is_blocked, updated_at trigger), `user_identities`(소셜 link, provider KAKAO/GOOGLE/NAVER/APPLE CHECK), `user_profiles`(JSONB NOT NULL DEFAULT), 정규화 recipes + recipe_ingredients/steps/labels/media/nutrition/classifications/stats, `user_recipes`(submission_text + draft_payload JSONB), chat_rooms/messages, likes, scraps. PK 모두 `SERIAL/INTEGER`. |

운영 prod 는 `spring.flyway.baseline-on-migrate=true` + `baseline-version="1"` — DB 팀원이 이미 적용한 상태를 baseline 으로 마크하고 migrate skip. dev/test 는 처음부터 V1 적용 (Testcontainers 가 자동).

---

## 4. 앞으로 할 일

진행 우선순위 (자세히는 [`deploy-status.md`](deploy-status.md) §"다음 액션"):

| # | 항목 | 트리거 |
|---|---|---|
| 1 | **B5 + A2 + A3** ACM + Route53 + listener:443 + KAKAO_REDIRECT_URI update + CORS 좁힘 | **운영 도메인 결정** |
| 2 | **C3** AI 측 SECRET 적용·검증 회신 | AI 팀 |
| 3 | **D3** CloudWatch billing alarm + 모니터링 | 언제든 |
| 4 | **D4** IAM 사용자 권한 좁히기 (AdministratorAccess → 좁은 정책) | 안정화 후 |
| 5 | **D1** 로컬 `.env` secret 정리 (Secrets Manager 이관 완료, 로컬 dev-only) | 언제든 |

---

## 5. archive 의 의미

- `archive/spec/` — 옵션 A 이전 (PR-1~7 시점) 의 도메인 명세 (user/recipe/like/scrap/chat/admin/upload) + 그 이전 v1 명세 (recipe-{create,read,delete}.md) + PR-1~7 정합 카탈로그 + 옛 ai-server-contract / user-domain-todo. **진실원본은 `auth-user-api.md` + `changes/2026-05-21-option-a-contract-diff.md` + 코드.**
- `archive/changes/` — 결론이 났거나 전제 자체가 사라진 change-log.
  - `2026-05-17-api4-dbv3-delta.md` — 옵션 A/B 결정 안건. A 채택으로 종결.
  - `2026-05-19-option-b-normalization.md` — 옵션 B 설계 메모. A 채택으로 사문화.
  - `2026-05-19-v5-split-social-accounts.md` — 우리 자체 V5(social_accounts) 설명. 옵션 A 의 user_identities 채택으로 폐기.
  - `V4-integration-{issues,resolved}.md` — 2026-05-02 V1↔V4 통합 history.
  - `SPEC-20260422-02-CL01.md`, `SPEC-20260422-04-CL01.md` — v1 명세 보강 메모.
  - `oauth-google-status.md` — 구글 소셜 미실현 메모.
- `archive/api-1.json`, `api-2.json` — 옛 AI OpenAPI 스냅샷.
- `api-3.json` (루트 docs) — 옵션 A 이전 스냅샷. 정합 안 됨. 참조 시 [`changes/2026-05-21-option-a-contract-diff.md`](changes/2026-05-21-option-a-contract-diff.md) 으로 보정.

**archive 는 읽지 않는다.** 단 git log / 이력 추적 시에만 참고.
