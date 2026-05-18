# API 서버 문서 인덱스

> 본 문서는 `docs/` 의 입구다. 어디서 무엇을 찾을지 한 페이지로 정리한다.
> 작업 시 **본 인덱스에 등장하는 문서만** 진실원본으로 본다. `docs/archive/` 의 문서는 history 보존 목적이고 현행 동작과 일치하지 않을 수 있다.

---

## 0. 한 줄 요약

API 서버는 앱(프론트) 과 1차로 마주하는 백엔드다. 책임은 (1) DB I/O 정본 관리, (2) 인증/인가 (자체 + 소셜), (3) AI 서버와의 통신. 자세한 정의는 [`api-server-tasks.md §0`](api-server-tasks.md).

---

## 1. 현재 상태 (2026-05-13)

| 영역 | 상태 |
|---|---|
| DB 마이그레이션 | V1+V2+V3 운영 가능. 추가 V4 미예정 ([§3](#3-db-스키마)). |
| 인증 | 자체 회원가입 / 로그인 / **카카오 소셜** / 쿠키 + JWT dual. **구글은 미지원** (2026-05-13 제거 완료). |
| 도메인 구현 | User / Recipe / Pending / Like / Scrap / Chat / Admin 모두 1차 완료. **api-3.json 정합 작업 진행 중** — PR-1~PR-7 완료 + User 인증 마무리 + 전역 snake_case 적용. PR-8(AI 협의 + 헬스체크 `GET /`) 남음 ([§4](#4-앞으로-할-일)). |
| 응답 규약 | 성공 = raw JSON (래퍼 없음). 실패 = `{"error":{"code","message","details"}}`. **전 요청·응답 JSON 키 snake_case** (Jackson `SNAKE_CASE`, 2026-05-17). |
| 클라이언트 | naengo-front (Flutter), naengo-admin (React) 가 모두 `/api/v1/...` prefix 를 기대. api-server 도 `/api/v1/...` 로 통일됨 (PR-1). |
| 테스트 | Testcontainers 기반 통합 테스트 **30건 PASS** (Auth 8 / Cors 4 / ProfileChat 3 / Recipe 6 / RequestId 4 / SocialAuth 5). |
| 인프라 | 로컬 docker-compose. AWS RDS / S3 / 배포는 팀원 담당, 미완료. |

---

## 2. 문서 지도 (어디서 무엇을 보나)

### 2.1 출발점

- **본 README** — 현행 상태, 인덱스, 진행 우선순위.
- [`auth-user-api.md`](auth-user-api.md) — **인증·사용자 API 가이드 (팀 공유용)**. 회원가입/로그인/카카오/로그아웃/마이페이지/탈퇴/프로필 요청·응답·에러·통합 흐름.
- [`kakao-oauth-runbook.md`](kakao-oauth-runbook.md) — **카카오 OAuth 운영 키 설정 + 브라우저 e2e 런북** (배포/운영 담당용, `user-domain-todo §4-1·§4-6`).
- [`api-server-tasks.md`](api-server-tasks.md) — Step 1~8 작업 카탈로그. 인벤토리 + 결정사항. **마스터 문서.**

### 2.2 외부 contract

- [`api-3.json`](api-3.json) — AI 서버 OpenAPI 3.1.0 dump (최신 스냅샷). front/admin 이 따라가는 contract.
- [`spec/ai-server-contract.md`](spec/ai-server-contract.md) — api-1 시점 갭분석 (history 참고용. 본 README §3 의 결합 표가 우선).
- [`spec/api3-alignment-and-integration.md`](spec/api3-alignment-and-integration.md) — **api-3.json 기준 정합 작업 카탈로그 (2026-05-13)**. front/admin/ai 통합 작업 목록 포함.

### 2.3 도메인 명세 (배경 — 일부 PR-1~7 이전)

> ⚠️ 아래 도메인 명세들은 **PR-1~7 이전에 작성**되어 경로/응답/메서드가 일부 낡았다
> (예: 토글 endpoint, `/api/` prefix, `PUT /profile`, `POST /approve`, 페이지네이션).
> **현행 엔드포인트·응답의 진실원본은
> [`spec/api3-alignment-and-integration.md §1.1`](spec/api3-alignment-and-integration.md)**.
> 아래 문서는 도메인 의도·비즈니스 규칙 배경 자료로만 본다.

| 도메인 | 명세서 |
|---|---|
| Recipe | [`spec/recipe-create-v2.md`](spec/recipe-create-v2.md), [`spec/recipe-read-v2.md`](spec/recipe-read-v2.md), [`spec/recipe-delete-v2.md`](spec/recipe-delete-v2.md) |
| Like / Scrap | [`spec/like-toggle.md`](spec/like-toggle.md), [`spec/scrap-toggle.md`](spec/scrap-toggle.md), [`spec/scrap-list.md`](spec/scrap-list.md) |
| User (자체/소셜 인증) | [`spec/user-me-get.md`](spec/user-me-get.md), [`spec/user-me-update.md`](spec/user-me-update.md), [`spec/user-password-change.md`](spec/user-password-change.md), [`spec/user-withdraw.md`](spec/user-withdraw.md) |
| User Profile | [`spec/user-preferences-get.md`](spec/user-preferences-get.md), [`spec/user-preferences-update.md`](spec/user-preferences-update.md) |
| Chat (read-only) | [`spec/chat-room-list.md`](spec/chat-room-list.md), [`spec/chat-message-list.md`](spec/chat-message-list.md) |
| Admin | [`spec/admin-pending-recipe-list.md`](spec/admin-pending-recipe-list.md), [`spec/admin-recipe-review.md`](spec/admin-recipe-review.md), [`spec/admin-user-block.md`](spec/admin-user-block.md) |
| Upload (S3) | [`spec/upload-presigned-url.md`](spec/upload-presigned-url.md) — S3 인프라 준비 후 구현 (보류) |
| Auth Cookie | [`spec/auth-cookie.md`](spec/auth-cookie.md) — JWT HttpOnly 쿠키 발급/만료 |

### 2.4 정책 / 운영 / 배포

- [`deploy-env.md`](deploy-env.md) — **운영(prod) 환경변수 주입 체크리스트 + 부팅 검증** (`.env.example` 템플릿 동반).
- [`kakao-oauth-runbook.md`](kakao-oauth-runbook.md) — 카카오 OAuth 콘솔 설정 + 브라우저 e2e (로컬 e2e 2026-05-18 완료).
- [`changes/chat-withdrawal-ai-agreement.md`](changes/chat-withdrawal-ai-agreement.md) — **탈퇴 시 chat PII hard delete AI 합의 안건서** (옵션 A 권고, AI 회신 대기).
- [`changes/auth-entry-point.md`](changes/auth-entry-point.md) — 401/403 일관 응답.
- [`changes/logging-policy.md`](changes/logging-policy.md) — X-Request-Id, MDC, PII 로그 금지.
- [`db-testing-guide.md`](db-testing-guide.md) — 로컬 DB 기동 / Flyway 검증.

### 2.5 진행 중 / 다음 작업

- [`spec/api3-alignment-and-integration.md`](spec/api3-alignment-and-integration.md) — **다음 8개 PR 의 입력**. 우선순위는 §6.
- [`spec/user-domain-todo.md`](spec/user-domain-todo.md) — **User 도메인 (로그인 + 카카오 소셜) TODO 리스트 (2026-05-13)**. 본 README 와 같이 발행.

### 2.6 템플릿

- [`spec-template.md`](spec-template.md) — 신규 명세 작성 시 복사.
- [`change-log-template.md`](change-log-template.md) — 명세 변경 시 change-log 발행.

---

## 3. DB 스키마

| 마이그레이션 | 내용 |
|---|---|
| `V1__init.sql` | 초기 스키마 (구 V4 통합 후의 모습). BIGSERIAL PK, AI contract 정합. |
| `V2__add_social_login_fields.sql` | `users.password_hash` nullable, `provider`, `provider_id`, `uq_provider_provider_id` UNIQUE. |
| `V3__add_user_deleted_at.sql` | `users.deleted_at` (탈퇴 익명화). |

> naengo-ai 의 실 모델 (`app/models/*.py`) 과 95% 정합. naengo-ai/db/schema.sql 의 정규화 설계는 채택하지 않음 (자세히는 [`spec/api3-alignment-and-integration.md §3`](spec/api3-alignment-and-integration.md)).

---

## 4. 앞으로 할 일

진행 우선순위 (자세히는 [`spec/api3-alignment-and-integration.md §6`](spec/api3-alignment-and-integration.md)):

1. ~~**User 도메인 점검 + 카카오 소셜 마무리**~~ — ✅ **완료 (2026-05-17)**. 회원가입/로그인/카카오/로그아웃/마이페이지/탈퇴/프로필 구현·테스트 완료, §2~§8 결정 기록. 외부/운영 의존 항목만 잔존 → [`spec/user-domain-todo.md`](spec/user-domain-todo.md)
2. ~~`/api/v1/...` prefix 일괄 적용~~ — ✅ PR-1 (2026-05-13)
3. ~~`ApiResponse<T>` 래퍼 폐기 + 표준 에러 응답~~ — ✅ PR-2 (2026-05-13)
4. ~~레시피 목록 커서 페이지네이션 + `is_liked`/`is_scrapped`~~ — ✅ PR-3 (2026-05-16)
5. ~~pending-recipes 분리 + soft delete~~ — ✅ PR-4 (2026-05-16)
6. ~~좋아요/스크랩 POST/DELETE 분리 + 409 + scrap list 경로 이동~~ — ✅ PR-5 (2026-05-16)
7. ~~Admin `PATCH` 통합 + `GET /admin/recipes?video_url=`~~ — ✅ PR-6 (2026-05-16)
8. ~~프로필 응답 모양 정합 + 채팅 path 정합 + 채팅방 soft delete~~ — ✅ PR-7 (2026-05-16)
9. ~~User 인증 API 마무리(프로필 자동생성/카카오 테스트) + 전역 snake_case~~ — ✅ 2026-05-17 ([`auth-user-api.md`](auth-user-api.md))
10. AI 팀 협의(D-1~D-7) + 헬스체크 `GET /` ← **다음 (PR-8, 협의 필요)**

---

## 5. archive 의 의미

- `archive/spec/` — v2 가 발행되어 대체된 v1 명세 (recipe-{create,read,delete}.md).
- `archive/changes/` — 결론이 났거나 전제 자체가 사라진 change-log.
  - `V4-integration-{issues,resolved}.md` — 2026-05-02 V1↔V4 통합 history.
  - `SPEC-20260422-02-CL01.md`, `SPEC-20260422-04-CL01.md` — v1 명세 보강 메모. v2 발행으로 자동 폐기.
  - `oauth-google-status.md` — 구글 소셜 미실현 메모. 2026-05-13 구글 제거 결정으로 폐기.
- `archive/api-1.json`, `api-2.json` — 옛 AI OpenAPI 스냅샷. 진실원본은 `api-3.json`.

**archive 는 읽지 않는다.** 단 git log / 이력 추적 시에만 참고.
