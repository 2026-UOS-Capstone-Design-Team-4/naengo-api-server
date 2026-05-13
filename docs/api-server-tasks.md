# API 서버 담당자 작업 정리

> 본 문서는 **API 서버(=naengo-api-server)** 담당자가 무엇을 해야 하는지 정리한 체크리스트다.
> AI 서버 / 프론트엔드 담당자는 자신의 영역과 인터페이스만 참고하면 된다.

> ### 🔗 외부 인터페이스 — AI 서버 API 문서 (필수 참조)
> - **AI 서버 OpenAPI/Swagger UI**: <http://43.201.62.254:8000/docs>
> - **로컬 스냅샷**: [`docs/api-1.json`](api-1.json) — 위 URL 의 `openapi.json` 을 받아 저장소에 캡처한 것. 본 문서·명세에서 "AI 서버 docs 정독" 이라고 하면 **이 파일을 읽으면 충분** (네트워크 / 사내 VPN 접근 불요).
> - 본 문서에서 "AI 서버 호출" 이 등장하는 모든 지점(임베딩, 추천, 재료 분석 등)의 **요청/응답 스키마는 위 두 자원을 진실원본으로 본다**. tasks 문서 / 명세서 본문 / 코드 상의 가정과 위 두 자원이 어긋날 경우 **AI 서버 docs 가 우선**.
> - 새 명세 작성 또는 기존 명세 갱신 시 §4 "작업 절차" 의 0번 단계로 **`docs/api-1.json` 부터 확인** 한다 (또는 docs URL 이 더 최신이면 그걸).
> - AI 서버 측에서 OpenAPI 가 갱신될 때마다 `docs/api-1.json` 도 같은 PR 에서 동기화한다. 동기화가 끊어진 사실을 발견하면 곧장 본 문서 / 관련 명세 / `change-log` 에 기록.

---

## 0. 한 줄 정의

API 서버는 **"앱(프론트)과 1차로 마주하고, 도메인 데이터의 정본(source of truth)을 관리하며, AI 서버와 신뢰 가능한 채널로 통신하는 백엔드"** 다.
즉, 다음 세 가지가 핵심 책임이다.

1. **DB I/O 관리** — Users / Recipes / Pending_Recipes / Scraps / Likes / Recipe_Stats / Chat_Rooms / Chat_Messages / User_Profiles 등 정형 데이터의 CRUD를 단독 소유.
2. **인증/인가** — 자체 회원가입·소셜 로그인 → JWT 발급 → AI 서버까지 전달되는 토큰 체계의 출발점.
3. **AI 서버와의 통신** — 사용자 요청을 AI 서버에 위임하고, AI 서버가 만든 결과(추천 결과 / 임베딩 등)를 다시 DB에 반영.

> ### 인프라 진행 상황 (AWS)
> - **DB (PostgreSQL + pgvector)**: 현재 로컬 개발용만 구성. **실 AWS RDS 이관은 팀원 담당** — 담당자가 세팅 완료하면 이 섹션에 엔드포인트·접속 정보 주입 방식(IAM/Secret Manager/env) 업데이트.
> - **S3**: 이미지 업로드(presigned URL) 대상. **버킷 생성 및 IAM 권한 설정도 팀원 담당** — 생성 전까지 API 서버의 업로드 엔드포인트 구현은 **명세만 작성하고 코드 구현은 이연**.
> - **API 서버 자체 배포** (ECS/EC2): Step 8 에서 별도 결정.
> - 로컬 개발 기본값은 `application-local.yml` 에, 운영 주입 포맷은 `application-prod.yml` 에 env 자리표시자로 이미 준비됨.

---

## 1. 현재 코드베이스 인벤토리 (2026-05-02 갱신, Step 2 일부 완료 + V4 도입 결정)

### 이미 구현된 것

| 영역 | 파일 | 상태 |
|---|---|---|
| 부트스트랩 | `ApiServerApplication.java` | OK |
| 공통 응답 | `global/dto/ApiResponse.java` | OK |
| 보안 설정 | `global/config/SecurityConfig.java` | OK (`/health` permitAll 반영) |
| JWT | `global/auth/JwtTokenProvider.java`, `JwtAuthenticationFilter.java`, `CustomUserDetailsService.java` | OK |
| 예외 | `global/exception/{CustomException, GlobalExceptionHandler, ErrorCode}.java` | OK. 미사용 `ErrorCode` 는 **선언 시점에만 추가** 정책 도입 (ErrorCode.java 하단 주석 참조) |
| OAuth | `global/auth/oauth/{Kakao,Google}OAuthClient.java`, `KakaoTokenClient.java`, `OAuthUserInfo.java`, `DevOAuthController.java` | 카카오 OK (dev 환경 검증). **구글은 미실현 — 코드 placeholder 만** (`docs/changes/oauth-google-status.md`) |
| User 도메인 | `domain/user/{entity,dto,repository,service,controller}/*` | OK (signup / login / social) + **2026-05-03 Step 4 완료**: 마이페이지 조회/수정, 비밀번호 변경, 회원 탈퇴 익명화 (`UserMeService` / `UserMeController`). **2026-05-04 후속**: `UserProfile` 엔티티 + 선호도 endpoint (`GET/PUT /api/users/me/profile`) |
| Auth 가드 | `global/auth/{JwtAuthenticationEntryPoint, JwtAccessDeniedHandler}.java` | **2026-05-04 신규** — 미인증 → 401 + ApiResponse, 미인가 → 403 + ApiResponse 일관 응답 |
| Auth 쿠키 | `global/auth/AuthCookieFactory.java` + `JwtAuthenticationFilter` 쿠키 fallback + `AuthController.logout` | **2026-05-07 신규 (`SPEC-20260507-01`)** — JWT 를 HttpOnly Cookie 로 발급/만료. Authorization 헤더 + 쿠키 양쪽 지원 (헤더 우선). `auth.cookie.*` env 분리 (local: secure=false, prod=true) |
| 통합 테스트 | `src/test/java/com/naengo/api_server/integration/{IntegrationTestSupport, AuthCookie, RecipeFlow, Cors, RequestId}IntegrationTest.java` | **2026-05-07 신규 + 2026-05-08 RequestId 추가** — Testcontainers + Spring Boot 4 + RestClient. **19건 PASS** (Auth 8 + Recipe 3 + CORS 4 + RequestId 4) |
| CORS | `global/config/{CorsProperties, SecurityConfig#corsConfigurationSource}.java` + `cors.*` env | **2026-05-07 신규 (Step 8-1)** — `allowCredentials=true` 고정 (쿠키 인증 호환). origin / methods / headers / exposed-headers 모두 env 분리 (`CORS_*`). preflight max-age 3600s |
| 로깅 정책 | `global/logging/RequestIdFilter.java`, `JwtAuthenticationFilter` MDC 확장, `logback-spring.xml` | **2026-05-08 신규 (Step 8-3)** — `X-Request-Id` 자동 부여 / MDC `requestId`+`userId` / 응답 헤더 echo. PII 로그 금지 정책은 `docs/changes/logging-policy.md` |
| 헬스체크 | `global/controller/HealthController.java` | OK (`GET /health`) |
| 설정 파일 | `application.yml` / `application-{local,prod}.yml` | OK (프로파일 분리, secret env 외부화, `aws.s3.*` 키 준비) |
| 마이그레이션 | `db/migration/V1__init.sql` (= 구 V4 가 V1 자리로 이동, fixes 적용), `V2__add_social_login_fields.sql`, `V3__add_user_deleted_at.sql` | **2026-05-02 V1 ↔ V4 통합 완료**. 구 `V1__init.sql` 폐기 + 구 `V4__fixed_schema.sql` 폐기. 새 V1 이 구 V4 의 설계를 흡수 (BIGSERIAL, V2 와 충돌하던 unique 제약 제거 등). V2/V3 는 그대로 ALTER 로 누적. **`fridge` 테이블 폐기** (사용자 결정 2026-05-02). |
| 빌드 도구 | `build.gradle` | Flyway(core + pg) 추가됨 |
| Recipe 도메인 | `domain/recipe/{entity,repository,service,controller,dto,support}/*` | OK (create→pending_recipes, read→recipes, delete→pending). `RecipeListMapper` 가 `Page<Recipe>` → `RecipeListResponse` 매핑을 공유 |
| Like 도메인 | `domain/like/{entity,repository,service,controller,dto}/*` | **2026-05-03 신규 (Step 3)** — 토글 endpoint. 카운터는 DB 트리거 책임 |
| Scrap 도메인 | `domain/scrap/{entity,repository,service,controller,dto}/*` | **2026-05-03 신규 (Step 3)** — 토글 + 본인 스크랩 목록. `RecipeListMapper` 재사용 |
| Chat 도메인 | `domain/chat/{entity,repository,service,controller,dto}/*` | **2026-05-03 신규 (Step 5)** — read-only. AI 서버가 primary writer. 채팅방 목록 + 메시지 조회 |
| Admin 도메인 | `domain/admin/{service,controller,dto}/*` | **2026-05-04 신규 (Step 6)** — pending_recipes 검토(목록/단건) + 승인(→recipes 이동)/반려 + 사용자 차단/해제 |
| 보조 유틸 | `global/auth/SecurityUtil`, `domain/user/support/AuthorDisplayName` | OK |
| 명세서 | `docs/spec/recipe-{create,read,delete}.md` (v1, 보존), **`docs/spec/recipe-{create,read,delete}-v2.md`** (2026-05-02 V4 통합 후 신규), `docs/spec/upload-presigned-url.md`, `docs/spec/ai-server-contract.md` (AI 서버 OpenAPI 0.1.0 스냅샷·갭분석), **`docs/spec/like-toggle.md`**, **`docs/spec/scrap-toggle.md`**, **`docs/spec/scrap-list.md`** (Step 3), **`docs/spec/user-me-{get,update}.md`**, **`docs/spec/user-password-change.md`**, **`docs/spec/user-withdraw.md`** (Step 4), **`docs/spec/chat-{room,message}-list.md`** (Step 5), **`docs/spec/admin-pending-recipe-list.md`**, **`docs/spec/admin-recipe-review.md`**, **`docs/spec/admin-user-block.md`** (Step 6), **`docs/spec/user-preferences-{get,update}.md`** (Step 4 후속, 2026-05-04), **`docs/spec/auth-cookie.md`** (2026-05-07, 인증 쿠키 통로) | OK |
| 로컬 개발 환경 | `docker-compose.yml` (pgvector/pg16) | OK |
| 온보딩 / 가이드 | `README.md`, `docs/db-testing-guide.md` | OK |

### 아직 없는 것 (= 만들어야 할 것)

- ~~**V4 마이그레이션 통합**~~ — **2026-05-02 완료**. 옵션 (b) 변형 채택 (V1 폐기 + 구 V4 가 V1 자리로 이동, V2/V3 는 보존)
- **Upload (S3 presigned URL) 실제 구현** (명세 있음. 코드는 AWS S3 준비 후 — Step 2-4b)
- ~~**Like / Scrap** (Step 3)~~ — **2026-05-03 완료**
- ~~**User 마이페이지 / 탈퇴 익명화** (Step 4)~~ — **2026-05-03 완료** + **2026-05-04 선호도 endpoint 후속 완료** (`user_profiles` 직접 입력 4필드)
- ~~**Chat (read-only)** (Step 5)~~ — **2026-05-03 완료** (5-2 채팅방 숨김 토글은 AI 서버 합의 후 별도 PR)
- ~~**Admin 도메인** (Step 6)~~ — **2026-05-04 완료** (pending 검토 / 승인·반려 / 사용자 차단)
- **AI 서버 연동 모듈** (Step 7)
- **통합 테스트 / 운영 준비** (Step 8) — **2026-05-07 통합 테스트 부분 완료** (Testcontainers + 11개 테스트 통과). CORS / 로깅 / 배포는 미완

---

## 1.5 V1 ↔ V4 통합 — 완료 (2026-05-02)

> **결정 (2026-05-02, 사용자 지시)**: 운영 미배포 단계이므로 V4 가 V1 자리를 **완전히** 차지한다. 구 V1 / 구 V4 를 모두 폐기하고, 새 V1 이 구 V4 의 설계를 흡수한다. V2 / V3 는 그대로 보존되어 ALTER 로 누적된다.
>
> 이는 [`docs/changes/V4-integration-issues.md`](changes/V4-integration-issues.md) 의 **옵션 (b) 변형** 에 해당한다. 협업자별 로컬 DB 는 `docker compose down -v` 로 wipe 필요.

### 적용된 변경 요약

| 항목 | Before | After |
|---|---|---|
| 마이그레이션 파일 | V1__init.sql + V2 + V3 + V4__fixed_schema.sql | **V1__init.sql (= 구 V4 + fixes) + V2 + V3** |
| `users.user_id` 등 PK | `BIGSERIAL` (구 V1) / `SERIAL` (구 V4) | `BIGSERIAL` (JPA `Long` 정합) |
| `users.password_hash` | NOT NULL (구 V1, V2 가 nullable 화) | nullable (V1 단계에서 이미 nullable, V2 는 no-op) |
| `users.provider`/`provider_id` | V2 가 추가 | V1 이 이미 보유, V2 의 `IF NOT EXISTS` 가 no-op |
| `uq_provider_provider_id` UNIQUE | V1 / V2 둘 중 한 곳에서 추가 | V2 에서 단독 추가 (V1 에서는 제외 → 중복 정의 충돌 회피) |
| `users.is_active` | 없음 | V1 에 추가 (계정 활성/탈퇴 토글) |
| `users.deleted_at` | V3 가 추가 | V3 그대로 유지 |
| `recipes` 컬럼 | `full_content` + `source` + `status` + `ingredients` 등 (구 V1) | `description` + `content` + `ingredients{name,amount,unit,type,note}` + `ingredients_raw` + `instructions` + `servings` + `cooking_time` + `calories` + `difficulty` + `category` + `tags` + `tips` + `video_url` + `is_active` + `author_type` + `embedding` (AI contract 정합) |
| `pending_recipes` | 없음 | **신규** — 사용자 제출 → 관리자 승인 → recipes 로 이동 |
| `chat_messages` | 없음 (구 V1 은 `session_logs`) | **신규** — AI 의 per-message 모델 정합 |
| `session_logs` | 있음 | **제거** |
| `chat_rooms.room_id` | `VARCHAR(100)` (UUID) | `BIGSERIAL` (AI 응답 `room_id: integer` 정합) |
| `user_profiles` | 없음 (구 V1 은 `users.preferences JSONB`) | **신규** — preferences 를 풍부한 컬럼으로 분리 |
| `recipe_stats` 카운터 갱신 | 애플리케이션 코드 (RecipeStats.increment 등) | **DB 트리거** (`trigger_likes_count`, `trigger_scrap_count`). 추가로 `trigger_recipe_stats_create` 가 recipe INSERT 시 row(0,0) 자동 생성 |
| `fridge` | V1 에 있음 | **폐기** (2026-05-02 사용자 결정 — Fridge 도메인 자체 미실현) |
| 인덱스 | 기본 | 추가: `idx_recipes_video_url` (PARTIAL), `idx_pending_recipes_user_id`, `idx_chat_messages_room_id`, 기타 |

### 적용된 코드 변경 요약 (2026-05-02 동시 PR)

- 엔티티
  - `User`: `preferences` 제거, `isActive` / `deletedAt` 추가 → `UserProfile` 은 미구현 (DB 만 준비, 엔티티는 Step 4 마이페이지 시점)
  - `Recipe`: 전면 재작성 — `source`/`status`/`fullContent` 폐기, `authorType` 신설, AI contract 의 11개 필드 모두 매핑
  - `RecipeStats`: 카운터 increment/decrement 메서드 제거 (DB 트리거가 처리). row 자체도 `trigger_recipe_stats_create` 가 자동 생성
  - 신규 `PendingRecipe` 엔티티 + `PendingRecipeRepository`
  - `Ingredient` record: `{name, amount}` → `{name, amount, unit, type, note}` (AI `IngredientItem` 정합)
- enum
  - `RecipeSource` 폐기 → `RecipeAuthorType` (ADMIN/USER) 신설
  - `RecipeStatus` 보존 — 이제 `PendingRecipe` 와 응답 DTO 에서만 사용
- 서비스
  - `RecipeService.create()` → `pending_recipes` INSERT
  - `RecipeService.listMine()` → `pending_recipes` 조회. 응답 DTO 도 `PendingRecipeListResponse` 신규
  - `RecipeService.listApproved()` → `recipes` 의 `is_active=true` 조회 (status 컬럼 없음)
  - `RecipeService.detail()` → `recipes` 만. PENDING 분기 / RECIPE_NOT_APPROVED 제거
  - `RecipeService.delete()` → 본인 `pending_recipe` hard delete (recipes 삭제는 admin Step 6)
  - `RecipeStats` 트랜잭션 INSERT 코드 제거, `session_logs` 선행 NULL 처리 코드 제거
- DTO 8개 갱신: `RecipeCreateRequest/Response`, `RecipeListItemResponse`, `RecipeDetailResponse`, 신규 `PendingRecipeListItemResponse`/`PendingRecipeListResponse`
- ErrorCode: `RECIPE_NOT_APPROVED` 제거, `PENDING_RECIPE_NOT_FOUND` 추가
- 빌드: `./gradlew build -x test` PASS

### 후속 작업

- [x] 1.5-a. AI 서버 docs 정독 → 갭분석 (`docs/spec/ai-server-contract.md`)
- [x] 1.5-b. V4 1차 작성 → 통합 이슈 12건 발견 → 옵션 (b) 변형 채택 → V1 자리로 이동 + fixes
- [x] 1.5-b'. ~~V4 재작성~~ — 본 PR 에서 V1 으로 흡수 완료
- [x] 1.5-c. **로컬 DB 재기동 검증 — 완료 (2026-05-03)**: `docker compose down -v && docker compose up -d && ./gradlew bootRun` 으로 V1→V2→V3 자동 적용 + Hibernate `validate` 통과. 검증 항목:
  - Flyway 3행 모두 `success = true` (V2 의 `provider`/`provider_id` IF NOT EXISTS 가 V1 와 충돌 없이 skip 되는 WARN 만 관측 — 정상)
  - 테이블 10개 (`users`, `user_profiles`, `recipes`, `pending_recipes`, `recipe_stats`, `scraps`, `likes`, `chat_rooms`, `chat_messages`, `flyway_schema_history`) — `fridge` 부재 확인
  - 트리거 3종 정상 동작: `trigger_recipe_stats_create` (recipes INSERT → stats(0,0) 자동), `trigger_likes_count` / `trigger_scrap_count` (INSERT/DELETE 시 카운터 ±1)
  - `users` 컬럼 11개 (`is_active`, `deleted_at`, `provider`, `provider_id` 모두 존재. `password_hash` nullable 확인. `uq_provider_provider_id` UNIQUE 제약 V2 가 추가)
  - `recipes` 컬럼 22개 (`description`, `instructions`, `servings`, `difficulty` CHECK, `tags`/`tips` DEFAULT `'[]'`, `is_active`, `author_type` CHECK, `embedding vector(1536)` 등 모두 존재)
  - 엔드투엔드 스모크 테스트: `/health` UP → signup → login → `POST /api/recipes` (pendingRecipeId=1, status=PENDING) → `GET /api/recipes/my` (pending row 1건 정상 노출)
- [x] 1.5-d. `docs/db-testing-guide.md` Flyway 기대값 표 갱신 (V1~V3, V4 제거)
- [x] 1.5-e. `README.md` 의 마이그레이션 문구 갱신 (V1~V3)
- [x] 1.5-f. 본 문서 §1 인벤토리 갱신
- [x] 1.5-g. 엔티티·서비스·DTO 갱신 — 위 "코드 변경 요약" 참조
- [x] 1.5-h. ~~`SPEC-20260422-02/03/04` v2 명세 발행~~ — **2026-05-02 완료**. `docs/spec/recipe-{create,read,delete}-v2.md` (`SPEC-20260502-02/03/04`)
- [ ] 1.5-i. AI 서버 팀과 §5 보류 항목 합의 회의
- [x] 1.5-j. ~~Step 6 (Admin 승인) 구현 시 `pending_recipes → recipes` 이동 트랜잭션 설계~~ — **2026-05-04 Step 6 에서 구현 완료** (`AdminRecipeService.approve` 트랜잭션, `PENDING_RECIPE_INCOMPLETE` 가드 포함)
- [ ] 1.5-k. AI 서버가 우리 `recipes.embedding` 을 채우는 메커니즘 합의 (옵션 B 잠정)

---

## 2. 내가(=API 서버 담당자) 해야 할 일 — 단계별

### Phase 0. 사전 결정 (2026-04-22 확정)

다른 파트(AI 서버, 프론트)와 같이 결정해야 결과적으로 재작업이 줄어드는 항목.

- [x] **AI 서버 ↔ API 서버 인증 방식** — 옵션 A (JWT secret 공유) 채택
  - 플로우:
    1. (자체) 프론트가 `/api/auth/login` 호출 → API 서버가 자체 JWT 발급
    2. (소셜) 프론트가 카카오/구글 SDK 로 access token 획득 → API 서버 `/api/auth/social/*` 로 전달 → API 서버가 검증 후 **동일한 형식의 자체 JWT 발급**
    3. 프론트는 로그인 방식과 무관하게 `Authorization: Bearer <API서버 JWT>` 하나만 사용
    4. AI 서버는 같은 secret 으로 JWT 검증 → `sub(=user_id)` 만 꺼내 사용. 사용자 정보가 더 필요하면 API 서버 조회 엔드포인트 호출.
  - **소셜 로그인이든 자체 로그인이든 AI 서버 입장에서는 동일한 JWT** 이므로 AI 서버는 로그인 방식을 알 필요가 없다(단일 검증 경로).
  - secret 은 환경변수로 양 서버가 공유. **회전 시 양 서버 동시 배포 또는 kid(key id) 기반 멀티 키 지원** 을 미리 설계해둘 것.
  - 서비스-투-서비스 호출(사용자 컨텍스트 없이 API 서버 → AI 서버 트리거, 예: 관리자 승인 후 임베딩 요청)은 사용자 JWT 가 없으므로 **별도 내부 토큰 또는 API Key** 헤더로 처리 → 이건 Phase 3 에서 구현 시점에 다시 정한다 (지금은 "존재한다"만 명시).
- [x] **Chat_Rooms / Session_Logs 의 쓰기 권한자** — AI 서버가 primary writer, API 서버는 read-only
  - AI 서버: INSERT / UPDATE (채팅 세션 생성, 메시지 누적, 추천 결과 기록, status 전이)
  - API 서버: SELECT 만. 제공 엔드포인트 — 내 채팅방 목록 / 특정 세션 조회 / 채팅방 숨김(`is_active=false`) 토글 정도
  - **DDL(테이블 생성·컬럼 추가·인덱스)은 API 서버가 Flyway 로 단독 관리** — AI 서버는 읽기/쓰기만, 스키마 변경은 하지 않음
  - AI 서버 팀과 **컬럼 시멘틱 변경이 필요할 때** 는 Flyway 마이그레이션 전 합의 필요 (공유 자원이므로)
- [x] **레시피 임베딩** — `VECTOR(1536)`, 관리자 승인 시점에 생성
  - 스키마: `recipes.embedding VECTOR(1536)` (OpenAI `text-embedding-3-small` 기준 또는 동급)
  - 원안 (2026-04-22 결정):
    1. 사용자 레시피 작성 → `status = 'PENDING'`, `embedding = NULL`
    2. 관리자 승인 API 호출 → API 서버 트랜잭션에서 `status = 'APPROVED'` 변경
    3. 트랜잭션 **커밋 후** AI 서버 `/internal/embed` 호출 (내부 토큰 사용) → vector 반환
    4. API 서버가 `UPDATE recipes SET embedding = ? WHERE recipe_id = ?`
  - 3번 실패 시 승인 자체는 유지 (사용자 체감 우선). 실패 건은 재시도 큐 or cron 으로 `embedding IS NULL AND status = 'APPROVED'` 조회 후 재생성.
  - **대안 논의**: AI 서버가 DB 에 직접 UPDATE 하는 것도 가능하지만, `recipes` 테이블 쓰기 권한자를 API 서버로 한정하기 위해 이 방식을 택함(책임 경계 명확화).
  - 🟡 **2026-05-02 갱신 — 재합의 필요**: AI 서버 OpenAPI(`docs/api-1.json`) 에 `/internal/embed` endpoint 가 부재. 현 시점에서 위 3~4 단계는 동작 불가. 두 갈래로 진행:
    - 옵션 (A) AI 서버에 `/internal/embed` 신설 요청 → 원안 그대로 진행
    - 옵션 (B) AI 서버가 자체 cron 으로 `WHERE embedding IS NULL AND status='APPROVED'` 조회 후 채움 — 즉 `embedding` 의 책임을 AI 서버에 완전 이양 (`§0 테이블별 책임` 표가 이미 그렇게 되어 있어 일관성 있음). API 서버 코드는 임베딩에 손대지 않음.
    - **잠정 채택**: 옵션 (B). AI 서버 팀 회신 시 변경 가능. Step 7 본 구현 시 재확인.
- [x] **이미지 업로드 — AWS S3 + presigned URL 방식**
  - 전제: 인프라를 AWS 로 올릴 예정 → S3 기본 사용. API 서버 인스턴스가 대용량 바이너리를 중계하는 구조는 처음부터 피한다.
  - 플로우 (레시피 사진):
    1. 프론트가 API 서버 `POST /api/uploads/presigned-url?type=recipe` 호출 → API 서버가 S3 presigned PUT URL + 최종 접근 URL 생성해 반환
    2. 프론트가 S3 에 직접 PUT 업로드
    3. 프론트가 레시피 생성 API 호출 시 최종 S3 URL 을 `image_url` 로 전달
    4. API 서버는 URL 프리픽스가 우리 버킷인지 검증만 하고 저장
  - 플로우 (재료 분석 사진):
    - 1안: 프론트 → AI 서버에 직접 업로드 (AI 서버가 분석 후 즉시 버림) → 이 흐름은 **AI 서버 책임이라 API 서버는 관여 안 함**
    - 2안: S3 임시 버킷 경유 (lifecycle rule 로 N시간 후 자동 삭제) → AI 서버가 키만 받아 분석
    - 선택은 AI 서버 팀이 결정하되, API 서버는 **재료 분석 이미지 저장을 하지 않는다** 는 것만 확정
  - S3 관련 설정(bucket, region, IAM role, CORS): `application.yml` 에 환경변수로 외부화. 로컬 개발에서는 LocalStack 또는 실 버킷(개발용) 사용.
  - 제한: MIME 화이트리스트(jpeg/png/webp), 최대 크기(예: 5MB), presigned URL 유효 시간(5분).
- [x] **마이그레이션 도구** — Flyway 확정
  - 이미 `V2__add_social_login_fields.sql` 이 존재 → Flyway 네이밍을 그대로 따라감
  - Phase 1 에서 `V1__init.sql` 을 작성(현재 운영 스키마 포함)하고 `mainFields.sql` 은 개발 레퍼런스로만 유지
- [x] **DB 공유** — AWS RDS PostgreSQL (pgvector 확장) 공유 DB
  - 같은 DB, 같은 스키마. AI 서버와 API 서버가 같은 데이터베이스에 접속.
  - 테이블별 책임:
    | 테이블 | Write | Read | DDL |
    |---|---|---|---|
    | `users` | API | API, AI | API |
    | `recipes` (embedding 컬럼 제외) | API | API, AI | API |
    | `recipes.embedding` | **AI** | AI (RAG 검색) | API |
    | `recipe_stats`, `scraps`, `likes` | API | API | API |
    | `chat_rooms`, `session_logs` | **AI** | API, AI | API |
    | ~~`fridge`~~ | ~~API~~ | ~~API, AI~~ | ~~API~~ — **2026-05-02 폐기** |
  - MVP 에서는 양 서버가 **같은 DB role** 공유. 운영 안정화 후 별도 role 로 권한 최소화 검토(예: AI 서버 role 은 `users` UPDATE 불가).

### Phase 1. 기반 정리 — **완료 (Step 1, 2026-04-22)**

§6 Step 1 에서 세부 실행. 요약:

- [x] `mainFields.sql` 정리 (후에 Step 1 에서 **제거**. V1~V3 이 진실원본)
- [x] Flyway 의존성 추가 + `spring.flyway.locations: classpath:db/migration`
- [x] `V1__init.sql` 작성 (소셜 로그인 **이전** 스키마, BIGINT 통일, 인덱스 포함)
- [x] `V2__add_social_login_fields.sql` `db/migration/` 으로 이동 (V1 → V2 체인)
- [x] `V3__add_user_deleted_at.sql` (익명화용 컬럼)
- [x] `application.properties` 제거, `application.yml` 단일화
- [x] `application-local.yml` / `application-prod.yml` 분리
- [x] `GET /health` 엔드포인트 + SecurityConfig `permitAll`

### Phase 2. 도메인 구현 (의존성 순서)

순서대로 가는 게 안전. 위→아래로 의존.

1. [ ] **Recipe** (entity / repo / service / controller)
   - 등록(작성) → 상태 PENDING, `embedding = NULL`, `image_url` 은 S3 URL 문자열만 저장 (Phase 0-4)
   - 단건 조회 (APPROVED 만 일반 사용자에게)
   - 목록 조회 (페이징 + 정렬: 최신순 / 인기순)
   - 본인 작성 레시피 목록 (PENDING 포함)
   - 본인 작성 레시피 삭제
   - **수정 API 없음** (정책 확정: 영구 불가. 오탈자도 삭제 후 재작성)
   - 응답 DTO 에서 작성자 닉네임은 `users.nickname` 을 그대로 쓰되, 탈퇴 사용자(`deleted_at IS NOT NULL`)는 `"탈퇴한 사용자"` 로 치환
2. [x] **Recipe_Stats** — 2026-05-02 V4 통합 시 DB 트리거로 자동 관리. 애플리케이션 직접 증감 없음
3. [x] **Like** — 2026-05-03 완료. 토글식 `POST /api/recipes/{id}/like`. UNIQUE + `DataIntegrityViolationException` 흡수
4. [x] **Scrap** — 2026-05-03 완료. 토글식 + `GET /api/scraps/my`
5. [x] **User 마이페이지** — 2026-05-03 완료
   - [x] 내 정보 조회 / 닉네임 수정 (`UserMeService.getMe/updateMe`)
   - [x] **선호도(`user_profiles`) 조회 / 수정 — 2026-05-04 완료**. `GET/PUT /api/users/me/profile`. `UserProfile` 엔티티·`UserProfileRepository` 신설. PUT 은 직접 입력 4필드 (`userInput`/`cookingSkill`/`preferredCookingTime`/`servingSize`) 만 허용, AI 분석 영역(`allergies` 등) 은 보호 (DTO 부재로 자동 거부). row 없으면 upsert (`UserProfile.empty(userId)` → save). 응답에 AI 분석 결과도 함께 노출 (read-only)
   - [x] 비밀번호 변경 (LOCAL provider 한정, 소셜은 `SOCIAL_PASSWORD_NOT_ALLOWED`)
   - [x] **회원 탈퇴 (익명화)**
     * `DELETE /api/users/me` — 단일 트랜잭션
     * PII nullify (`email`/`password_hash`/`provider_id` → NULL) + `nickname = '탈퇴한 사용자_<user_id>'` + `is_blocked = true` + `is_active = false` + `deleted_at = NOW()`
     * 본인의 `scraps`/`likes`/`pending_recipes`/`user_profiles` 삭제. `recipe_stats` 카운터는 V1 의 likes/scraps DELETE 트리거가 자동 감소 (애플리케이션 보정 불필요)
     * `chat_rooms`/`chat_messages` 삭제는 **AI 서버와 합의 후 결정** (보류)
     * 작성 레시피(`recipes.author_id`)는 유지, 표시 시점에 닉네임 치환
6. [x] **Chat (read-only)** — 2026-05-03 완료. V4 통합 후 `chat_rooms` + `chat_messages` (구 `session_logs` 폐기, AI per-message 모델 정합)
   - [x] 내 채팅방 목록 (`GET /api/chat/rooms`, `is_active=true`, `updated_at DESC`)
   - [x] 특정 채팅방 메시지 시간순 (`GET /api/chat/rooms/{roomId}/messages`)
     · `recipe_ids` → 활성 `RecipeListItemResponse` 일괄 매핑 (N+1 방지). 비활성 레시피는 응답에서 제외
   - [x] **쓰기는 AI 서버가 담당** (Phase 0 합의 그대로)
7. ~~**Fridge**~~ — **2026-05-02 폐기**. 도메인 자체 미실현. AI 서버가 채팅 컨텍스트로 재료를 직접 받아 처리하므로 별도 영속 저장 불필요.
8. [x] **Admin** — 2026-05-04 완료
   - [x] 관리자 로그인 — `User.role=ADMIN` 권한 가드 (SecurityConfig 의 `hasRole("ADMIN")` 사용; 별도 로그인 화면은 프론트 책임)
   - [x] 레시피 검토 — `GET /api/admin/pending-recipes` (status 필터, default PENDING), `GET .../{id}` 단건
   - [x] 레시피 승인 — `POST /api/admin/pending-recipes/{id}/approve` → `recipes` INSERT (트리거가 `recipe_stats(0,0)` 생성) + `pending_recipes` status=APPROVED. **V4 통합 후 변경**: 구 plan 의 `recipes.status='APPROVED'` UPDATE 가 아니라 **테이블 간 이동**. 누락 필드 검증(`PENDING_RECIPE_INCOMPLETE`) 포함
   - [x] 레시피 반려 — `POST .../{id}/reject` (사유 필수)
   - [x] 사용자 차단/해제 — `POST /api/admin/users/{userId}/block` / `unblock`. 멱등 동작
   - 레시피 승인 시 임베딩 호출은 **AI 서버 옵션 B 잠정 채택** 으로 본 PR 에서 미연동 (Phase 0-3 메모). 옵션 A 합의 시 Step 7 에서 추가
9. [ ] **Upload (S3 presigned URL)**
   - `POST /api/uploads/presigned-url` → type(recipe/...) 을 받아 presigned PUT URL + 최종 접근 URL 반환
   - MIME 화이트리스트(jpeg/png/webp), 최대 크기, 유효 시간(5분)
   - 인증 필수 (USER)

### Phase 3. AI 서버 연동 모듈

- [ ] `global/client/ai/` 패키지 신설
- [ ] AI 서버 베이스 URL, 타임아웃, 재시도 설정 → `application.yml`
- [ ] WebClient 또는 RestClient (Spring Boot 3.2+) 채택
- [ ] 호출별 클라이언트:
  - `EmbeddingClient.requestEmbedding(recipeId, fullContent)` → `float[1536]` — **내부 토큰** 사용 (사용자 컨텍스트 없음, 관리자 승인 트리거)
  - `RagClient.recommend(userId, fridge, preferences, feedback)` — *필요 시*. 현재 설계상 프론트가 AI 서버로 직접 호출하고 결과만 세션 로그로 AI 서버가 DB 에 기록하므로 API 서버 경유 불필요할 수 있음 → AI 서버 팀과 최종 합의
  - 재료 이미지 분석은 **API 서버 경유하지 않음** (Phase 0-4 확정)
- [ ] 헤더 전략:
  - 사용자 요청 대리 호출: `Authorization: Bearer <user JWT 그대로 전달>`
  - 서비스-투-서비스 트리거(임베딩 요청 등): `X-Internal-Token: <공유 내부 토큰>` 혹은 서비스 전용 JWT (Phase 3 구현 시점에 최종 결정)
- [ ] 실패 시 fallback / 사용자에게 보일 에러코드 정의 (`ErrorCode`에 `AI_SERVER_UNAVAILABLE`, `EMBEDDING_FAILED` 등 추가)

### Phase 4. 운영 준비

- [ ] 통합 테스트 (`@SpringBootTest`) — 로그인/레시피 작성/스크랩 happy path 1개씩만이라도
- [ ] CORS 설정 (프론트 도메인 화이트리스트)
- [ ] 운영 환경 변수 분리 (`application-prod.yml`, secret 외부화)
- [ ] 로깅 정책 (요청 ID, 사용자 ID 마스킹)

---

## 3. 내가 **하지 않는** 일 (경계선 명확화)

오버리치를 막기 위해 명시.

- ❌ 재료 사진 → 재료 추출 모델 호출 자체 (= AI 서버)
- ❌ RAG 인덱스 빌드 / 벡터 검색 알고리즘 (= AI 서버)
- ❌ LLM 프롬프트 엔지니어링 (= AI 서버)
- ❌ 챗봇 대화 상태 머신 (= AI 서버, API 서버는 결과만 저장)
- ❌ 프론트엔드 화면, 소셜 로그인 SDK 호출 (= 프론트)

> **2026-05-02 갱신**: 기존 "❌ 카카오/구글에서 access token 받기까지" 항목 삭제.
> 코드 상으로 `KakaoTokenClient` (인가 코드 → 액세스 토큰 교환), `KakaoOAuthClient` / `GoogleOAuthClient` (액세스 토큰 → 사용자 정보) 가 이미 API 서버에 존재하므로 경계선 기술이 잘못됐음. 합의된 흐름:
> 1. 프론트가 카카오/구글 SDK 또는 redirect 로 **인가 코드** 또는 **액세스 토큰** 을 획득
> 2. API 서버가 (필요 시) 인가 코드 → 액세스 토큰 교환 (`KakaoTokenClient`)
> 3. API 서버가 제공자 API 호출로 사용자 정보 조회 (`*OAuthClient`)
> 4. 자체 JWT 발급 → **HttpOnly 쿠키로 프론트와 주고받음** (Phase 0-1 갱신, 아래 §"인증 흐름 갱신" 참조)

내가 하는 건: **그 결과물을 받아 검증·저장·재가공하고, 다시 정형화된 응답으로 내보내는 것**.

### 인증 흐름 갱신 (2026-05-02 합의 → **2026-05-07 구현 완료**)

기존 Phase 0-1 의 `Authorization: Bearer <JWT>` 만 사용하던 방안이, **JWT 를 HttpOnly Cookie 로 주고받기** 로 합의 변경됨. 본 합의는 [`docs/spec/auth-cookie.md`](spec/auth-cookie.md) (`SPEC-20260507-01`) 으로 구현 완료:

- ✅ 자체 로그인 / 소셜 로그인 / 회원가입 응답에서 `Set-Cookie: NAENGO_AT=<JWT>; HttpOnly; SameSite=Lax; Path=/; Max-Age=86400` (+ prod: `Secure`) 발급. body 의 `accessToken` 도 그대로 노출 (모바일 호환)
- ✅ `JwtAuthenticationFilter` 가 `Authorization` 헤더 우선, 없으면 쿠키 fallback
- ✅ `POST /api/auth/logout` 신설 — 쿠키 만료 (Max-Age=0). 인증 없이도 호출 가능 (멱등). stateless JWT 라 토큰 자체는 만료 시각까지 유효
- ✅ `DELETE /api/users/me` (탈퇴) 도 쿠키 만료 동봉
- ✅ 모바일은 기존 Authorization 헤더 그대로 사용 (응답 body 의 accessToken)
- AI 서버와의 secret 공유 정책은 그대로 유지 (양쪽 다 같은 secret 으로 JWT 검증)

---

## 4. 작업 절차 (코드를 짜기 전에 매번 거치는 루틴)

0. **AI 서버 / 외부 인터페이스 확인** — 본 작업이 AI 서버 / S3 / OAuth 등 외부와 닿는다면 먼저 [`docs/api-1.json`](api-1.json) (= AI 서버 OpenAPI 로컬 스냅샷, 원본은 <http://43.201.62.254:8000/docs>) 을 열어 **현재 시점의 contract** 를 확인한다. 이 단계 없이 명세를 쓰면 재작업이 거의 확실.
1. **무엇을 만들지** `docs/spec/` 아래에 `spec-template.md` 형식으로 명세서 작성. AI 서버 호출이 포함되는 명세는 §6 "외부 의존성" 에 호출하는 AI 서버 endpoint 경로·요청 모델·응답 모델을 docs URL 기반으로 명시한다.
2. 명세서를 LLM(또는 본인)에게 전달 → 코드 생성
3. 받은 코드 검토, 수정 사항이 생기면 `docs/changes/` 아래에 `change-log-template.md` 형식으로 변경 이력 기록
4. 테스트 → 커밋 → 푸시
5. **문서 최신화 (필수 루틴)**: 매 작업 후 아래 항목을 확인·갱신하고 같은 PR/커밋 또는 직후 커밋으로 반영한다.
   - `api-server-tasks.md §1 인벤토리` — 새 파일·새 영역이 생기면 한 줄 추가
   - `api-server-tasks.md §6 작업 순서` — 해당 Step/체크박스 상태 반영
   - `api-server-tasks.md §5 보류 항목` — 새로 파생된 결정 보류 사항이 생기면 추가
   - `README.md` — 새 환경변수·새 엔드포인트·새 전제 조건이 생기면 반영
   - 명세를 벗어난 구현 디테일은 `docs/changes/` 에 기록
   - **AI 서버 docs 와 어긋난 가정을 발견했다면** §1.5 V4 후보 항목에 추가하거나 별도 마이그레이션 / 명세 발행

이렇게 하면 "왜 이렇게 짰지?" 가 사라지고, 다음 사람(또는 미래의 본인)이 명세서만 봐도 의도를 복원할 수 있다.

---

## 5. 의사결정 보류 항목 (TODO: 팀 논의)

- [x] ~~위 Phase 0 항목 전부~~ — 2026-04-22 확정
- [ ] **서비스-투-서비스 인증 구체안** (Phase 0-1 에서 "존재한다"까지만 결정. 내부 토큰 포맷·회전 주기는 Phase 3 구현 시 확정)
- [ ] **JWT secret 회전 정책** (kid 지원 여부 / 동시 배포 방식)
- [ ] **재료 분석 이미지 흐름 최종안** (프론트 → AI 직통 vs S3 임시 경유) — AI 서버 팀 결정 대기
- [x] ~~통계(인기 레시피 / 스크랩 수 / 검색 실패율) 집계~~ — **우선순위 최하**. MVP 에서 제외하고, 도입 필요성이 확인되면 그때 별도 명세로 처리. `recipe_stats` 는 좋아요·스크랩 수 캐시 목적으로만 유지.
- [x] ~~회원 탈퇴 시 작성 레시피·스크랩 처리~~ — **익명화** 확정
  - User 테이블에 `deleted_at TIMESTAMPTZ NULL` 컬럼 추가 (마이그레이션 필요)
  - 탈퇴 API 호출 시:
    * `email`, `password_hash`, `provider_id`, `preferences` → `NULL` 로 초기화 (PII 제거)
    * `nickname` → `"탈퇴한 사용자"` 같은 고정 문자열. `UNIQUE` 제약 때문에 내부적으로는 `"탈퇴한 사용자_<user_id>"` 형태로 저장하고 응답 DTO 에서 꼬리표 제거하여 반환
    * `is_blocked = true` (재로그인 방지)
    * `deleted_at = now()`
  - 기존 `recipes.author_id` (`ON DELETE SET NULL`), `scraps`/`likes` (`CASCADE`) 는 익명화 모델과 맞지 않음 → **탈퇴 시 유저 row 는 삭제하지 않으므로 FK 트리거는 작동하지 않음**. 즉 author_id 는 그대로 유지되고, 조회 시 닉네임만 `"탈퇴한 사용자"` 로 보인다.
  - 탈퇴한 사용자의 스크랩/좋아요는 어떻게 할지: **삭제**(의미 없는 기록). 탈퇴 트랜잭션 안에서 `DELETE FROM scraps WHERE user_id = ?` / `DELETE FROM likes WHERE user_id = ?` + `recipe_stats` 카운터 보정.
  - 탈퇴한 사용자의 채팅 기록(`chat_rooms`, `session_logs`): PII 포함 가능성 높음 → **삭제**. AI 서버 팀과 "탈퇴 이벤트 발생 시 AI 서버도 이 사용자 관련 데이터 파기" 합의 필요 (정책 보류 항목).
- [x] ~~레시피 수정 불가 정책~~ — **영구 불가** 확정. 작성 후 어떤 경우에도 수정 API 를 제공하지 않는다. 오탈자 등도 삭제 후 재작성으로만 정정.
- [ ] 임베딩 재시도 메커니즘 (DB 폴링 vs 메시지 큐 SQS)
- [ ] AWS 운영 세부: RDS 인스턴스 타입, S3 버킷 분리(prod/dev), CloudFront 사용 여부
- [ ] **탈퇴 시 AI 서버 데이터 파기 정책** (위 익명화 결정에서 파생됨)
- [x] ~~**AI 서버 docs 와의 정합 점검 결과**~~ — 2026-05-02 완료. `docs/spec/ai-server-contract.md` 발행. 합의 필요한 항목은 아래로 분리.
- [ ] **AI 서버 contract 미합의 항목 (V4 범위 외, V5/V6 후보)** — `docs/spec/ai-server-contract.md §4` 발췌:
  - `chat_rooms.room_id` 타입 (현재 `VARCHAR(100)` vs AI 응답 `integer`) 정합
  - `session_logs` 폐기 + AI per-message 모델(`chat_messages`)로 수렴 여부
  - `recipes.status` (PENDING/APPROVED/REJECTED) 와 AI RAG 검색의 정합 (RAG 가 PENDING 까지 노출하는지)
  - `recipes.source` ('STANDARD'/'USER') ↔ AI `author_type` ('ADMIN'/'USER') 매핑 (현재 잠정: 응답 매퍼에서 `STANDARD → ADMIN`)
  - AI 서버에 `/internal/embed` 신설 여부 (Phase 0-3 옵션 B 잠정 채택)
  - AI 서버가 우리 DB 와 동일한 Postgres 인스턴스를 쓰는지 / 별도 DB 인지 (Phase 0-5 가 "공유" 였으나 contract 상 모델 차이가 커서 재확인 필요)
  - AI 서버 인증 도입 시점 — 현재 `user_id=1` 고정. Phase 0-1 JWT secret 공유는 그 이후
- [ ] **AI 서버 docs URL 의 운영/스테이지 분리** — 현재 `43.201.62.254:8000` 단일 호스트. 운영 분리 시점에 `application-prod.yml` 의 `ai.server.base-url` 갱신 필요

---

## 6. 작업 순서 (즉시 착수 가능한 순)

모든 Phase 0 결정이 끝난 현재, 다음 순서대로 진행한다. **각 스텝은 "AI 서버 docs 확인 → 명세서 작성 → LLM 코드 생성 → 검토·수정 → 커밋"** 루틴을 그대로 따른다 (§4).
의존성이 있는 스텝은 앞 스텝 완료 전에는 착수하지 않는다.

> ### 🎯 재조정된 우선순위 (2026-05-02)
> AI 서버 docs (`http://43.201.62.254:8000/docs`, 로컬 스냅샷 [`docs/api-1.json`](api-1.json)) 가 실제로 떠 있고 계약이 가시화됨에 따라 다음과 같이 우선순위를 재조정한다.
>
> | 순위 | 작업 | 비고 |
> |---|---|---|
> | **즉시** | **Step 1.5 — V4 마이그레이션** (V1 보정본) | AI 서버 docs 와 스키마 정합부터 맞춰야 이후 Step 5/6/7 의 계약이 흔들리지 않는다. 본 PR / 본 브랜치(`claude/update-migrations-api-docs-7Y4fU`) 의 1차 산출물. |
> | 1 | Step 7 일부 — **AI 서버 contract 검토 산출물** | docs URL 의 endpoint 표를 `docs/spec/ai-server-contract.md` 등으로 정리. 코드 작성보다 먼저. **Step 6 / Step 7 본 구현의 전제** 가 됨. |
> | ~~2~~ | ~~Step 3 — Like / Scrap~~ | **2026-05-03 완료**. |
> | ~~3~~ | ~~Step 4 — User 마이페이지 / 탈퇴~~ | **2026-05-03 완료** (선호도 후속 PR). |
> | ~~4~~ | ~~Step 5 — Chat read-only~~ | **2026-05-03 완료**. 5-2 (채팅방 숨김 토글) 은 AI 서버 합의 후 별도 PR. |
> | ~~5~~ | ~~Step 6 — Admin (승인 흐름)~~ | **2026-05-04 완료**. 임베딩 호출은 옵션 B 채택으로 미연동. |
> | 6 | Step 7 본 구현 — AI 서버 클라이언트 | Step 1.5 와 "AI 서버 contract 검토 산출물" 완료 후. |
> | 7 | Step 2-4b — Upload 실 구현 | AWS S3 버킷 준비 시점에. 변동 없음. |
> | 마지막 | Step 8 — 운영 준비 / 통합 테스트 | 변동 없음. |
>
> **즉시 착수 항목 = Step 1.5**. 이후 일정은 위 표에 따른다.

### Step 1. 인프라 기반 정리 (선행, 다른 모든 스텝의 전제) — **완료 (실 기동 검증 포함)**
- [x] 1-1. `build.gradle` 에 Flyway 의존성 추가 (`spring-boot-starter-flyway` + `flyway-database-postgresql`. Spring Boot 4 는 `flyway-core` 만으로는 오토컨피그가 걸리지 않음)
- [x] 1-2. `application.properties` 제거 → `application.yml` 로 단일화
- [x] 1-3. 프로파일 분리: `application-local.yml`(기본), `application-prod.yml`(env 주입)
- [x] 1-4. `src/main/resources/db/migration/V1__init.sql` 작성 — 소셜 로그인 **이전** 스키마 (BIGINT 통일, 인덱스 포함, `embedding VECTOR(1536)`)
- [x] 1-5. 기존 V2 는 `db/migration/` 으로 이동하여 V1 → V2 체인 유지 (V2 는 그대로 소셜 로그인 필드 추가)
- [x] 1-6. `V3__add_user_deleted_at.sql` 작성
- [x] 1-7. `GET /health` 엔드포인트 추가 + SecurityConfig 에서 `permitAll`
- [x] 1-8. `./gradlew build` 통과 (테스트 제외 — 통합 테스트 DB 연동은 Step 8)
- [x] 1-9. **실 Postgres(pgvector/pg16, host 5434) 기동 검증 완료 (2026-04-29)**
  - Flyway V1→V2→V3 자동 적용
  - Hibernate `validate` 통과 (엔티티-스키마 정합)
  - `GET /health` → `{"status":"UP"}`

**산출물**: `build.gradle`, `application{,-local,-prod}.yml`, `db/migration/V1~V3.sql`, `HealthController`, `SecurityConfig` 업데이트

---

### Step 1.5. V4 통합 — **완료 (2026-05-03)**
의존: Step 1 완료. **이후 모든 Step 의 전제**.

> **2026-05-04 점검 결과**: 본 §6 Step 1.5 의 옛 [ ] 체크박스가 §1.5 의 실제 진행 상황 (전부 [x]) 과 일관되지 않아 정정. 결정·갭분석·실 작업 모두 끝남. 현 status:

- [x] 1.5-1. AI 서버 OpenAPI 정독 — 갭분석 산출 [`docs/spec/ai-server-contract.md`](spec/ai-server-contract.md) 발행
- [x] 1.5-2. 점검 결과 spec 화 — 위 contract spec 의 §3 / §4 표가 정합 매핑
- [x] 1.5-3. V4 작성 → 통합 이슈 12건 발견 → 사용자 지시로 **옵션 (b) 변형** 채택: V1 폐기 + 구 V4 가 V1 자리 흡수. V2/V3 보존. 자세한 결정 / 변경 내용은 [`docs/changes/V4-integration-resolved.md`](changes/V4-integration-resolved.md)
  - 알려졌던 항목 (`session_logs.selected_recipe_id ON DELETE SET NULL`) 은 V4 통합 시 `session_logs` 테이블 자체가 폐기되어 자동 해소됨 (per-message `chat_messages` 모델로 전환)
- [x] 1.5-4. `docker compose down -v && docker compose up -d && ./gradlew bootRun` 으로 V1→V2→V3 자동 적용 검증 — Flyway 3행 모두 success
- [x] 1.5-5. Hibernate `validate` 통과 확인 — 엔티티 (User / Recipe / RecipeStats / PendingRecipe / Ingredient + RecipeAuthorType enum) 동시 갱신
- [x] 1.5-6. `docs/db-testing-guide.md` Flyway 기대값 표 갱신 (V1~V3) + V4 신설 테이블·트리거 검증 항목 추가
- [x] 1.5-7. `README.md` 의 "V1~V3" 문구 갱신, 본 문서 §1 인벤토리 갱신
- [x] 1.5-8. SPEC 후속 발행 — `recipe-create-v2`, `recipe-read-v2`, `recipe-delete-v2` (`SPEC-20260502-02/03/04`)

**산출물 (실제)**:
- `db/migration/V1__init.sql` (= 구 V4 흡수, BIGSERIAL/`fridge` 폐기/`is_active`/triggers 등)
- `docs/spec/ai-server-contract.md`, `docs/spec/recipe-{create,read,delete}-v2.md`
- `docs/changes/V4-integration-issues.md`, `docs/changes/V4-integration-resolved.md`
- 갱신된 `db-testing-guide.md` / `README.md` / 본 문서
- 엔티티·DTO·서비스·컨트롤러 V4 정합 (Recipe / PendingRecipe / RecipeAuthorType / Ingredient 5필드 / User.is_active 등)

**§1.5 와의 일관성**:
§1.5 의 1.5-a~h 와 본 §6 의 1.5-1~8 은 동일 작업의 두 시각. 잔여(미완) 후속은 다음 항목들로 §1.5 에 등록 (Step 6/7 / AI 팀 합의 영역):
- [ ] 1.5-i. AI 서버 팀과 §5 보류 항목 합의 회의
- [x] 1.5-j. ~~Step 6 (Admin 승인) 구현 시 `pending_recipes → recipes` 이동 트랜잭션 설계~~ — **2026-05-04 Step 6 에서 구현 완료** (`AdminRecipeService.approve` 트랜잭션)
- [ ] 1.5-k. AI 서버가 우리 `recipes.embedding` 을 채우는 메커니즘 합의 (옵션 B 잠정)

---

### Step 2. Recipe 도메인 + S3 업로드 — **부분 완료 (2026-04-22)**
의존: Step 1 완료.

- [x] 2-1. `docs/spec/recipe-create.md` (SPEC-20260422-02) 작성 + Recipe 작성 엔드포인트 구현
- [x] 2-2. `docs/spec/recipe-read.md` (SPEC-20260422-03) 작성 + 목록/단건/내 레시피 조회 구현
- [x] 2-3. `docs/spec/recipe-delete.md` (SPEC-20260422-04) 작성 + 본인 삭제 구현 (session_logs 참조 선행 NULL 처리 포함)
- [x] 2-4a. `docs/spec/upload-presigned-url.md` (SPEC-20260422-05) 작성
- [ ] 2-4b. **Upload 엔드포인트 실제 구현 — AWS S3 버킷 생성 후로 이연** (§0 인프라 메모)
- [x] 2-5. Recipe 작성 트랜잭션에서 `recipe_stats` INSERT (`RecipeService.create`)
- [x] 2-6. 작성자 닉네임 치환: `AuthorDisplayName` 공통 유틸 (`"탈퇴한 사용자_<id>"` → `"탈퇴한 사용자"`)

**구현 산출물**:
- `domain/recipe/entity/*` — Recipe, RecipeStats, RecipeSource, RecipeStatus, Ingredient
- `domain/recipe/repository/*` — RecipeRepository(JPQL 페치조인), RecipeStatsRepository
- `domain/recipe/dto/*` — Create/List/Detail DTO
- `domain/recipe/service/RecipeService`
- `domain/recipe/controller/RecipeController`
- `domain/user/support/AuthorDisplayName`
- `global/auth/SecurityUtil` — currentUserIdOrNull / hasRole
- `global/config/SecurityConfig` — `/api/recipes/my` 를 permitAll 규칙보다 먼저 `authenticated()` 로 보강
- `application.yml` — `aws.s3.*` 설정 키 (버킷 미설정 시 프리픽스 검증 스킵)

**검증**: `./gradlew build -x test` 통과. 실 DB 기동 테스트는 팀원의 AWS RDS 준비 후.

---

### Step 3. Engagement (Like, Scrap) — **완료 (2026-05-03)**
의존: Step 2 완료.

- [x] 3-1. 명세서 `docs/spec/like-toggle.md` (`SPEC-20260503-01`) → 구현 (`POST /api/recipes/{id}/like` 토글)
- [x] 3-2. 명세서 `docs/spec/scrap-toggle.md` (`SPEC-20260503-02`) + `docs/spec/scrap-list.md` (`SPEC-20260503-03`) → 구현
- [x] ~~3-3. Like/Scrap 트랜잭션 안에서 `recipe_stats.likes_count` / `scrap_count` 증감~~ — **폐기**: V1 의 DB 트리거(`trigger_likes_count`, `trigger_scrap_count`) 가 단독 책임. 애플리케이션은 `entityManager.flush()` 후 재조회로 카운트만 읽음
- [x] ~~3-4. 동시성: `recipe_stats` UPDATE 에 낙관적 락 또는 `SELECT ... FOR UPDATE` 중 택 1~~ — **폐기**: 트리거가 atomic UPDATE 로 처리. race condition 안전망은 `(user_id, recipe_id)` UNIQUE 제약 + `DataIntegrityViolationException` 흡수

**산출물 (실제)**:
- 명세서 3건 (`like-toggle`, `scrap-toggle`, `scrap-list`)
- `domain/like/{entity,repository,service,controller,dto}/*` (Like, LikeRepository, LikeService, LikeController, LikeToggleResponse)
- `domain/scrap/{entity,repository,service,controller,dto}/*` (Scrap, ScrapRepository, ScrapService, ScrapController, ScrapToggleResponse)
- `domain/recipe/support/RecipeListMapper` 신규 — `Page<Recipe>` → `RecipeListResponse` 매핑을 RecipeService 와 ScrapService 가 공유 (N+1 방지 + 작성자 닉네임 일괄 조회 패턴)
- 검증: 빌드 + 로컬 docker 부팅 + 토글 3회 왕복(0→1→0→1) + `/api/scraps/my` 정상 노출 + 401/404 에러 케이스 확인

**알려진 미흡**:
- ~~인증 없이 보호 endpoint 호출 시 응답이 401 이 아닌 **403** 으로 떨어짐~~ — **2026-05-04 해결** (`JwtAuthenticationEntryPoint` 추가). 미인증 → 401 + ApiResponse 로 일관됨.

---

### Step 4. User 마이페이지 + 회원 탈퇴(익명화) — **완료 (2026-05-03)**
의존: Step 1 (deleted_at 마이그레이션) + Step 3 (스크랩/좋아요 삭제 대상).

- [x] 4-1. `docs/spec/user-me-get.md` (`SPEC-20260503-04`), `docs/spec/user-me-update.md` (`SPEC-20260503-05`) → 구현 (닉네임만)
- [x] 4-2. `docs/spec/user-password-change.md` (`SPEC-20260503-06`) → 구현 (LOCAL 한정, 소셜은 `SOCIAL_PASSWORD_NOT_ALLOWED`)
- [x] 4-3. `docs/spec/user-withdraw.md` (`SPEC-20260503-07`) → 구현 (`DELETE /api/users/me`, 단일 트랜잭션)
  - PII nullify (`email` / `password_hash` / `provider_id` → NULL) + `nickname = '탈퇴한 사용자_<user_id>'` + `is_blocked = true` + `is_active = false` + `deleted_at = NOW()`
  - `scraps` / `likes` / `pending_recipes` / `user_profiles` 삭제 — `recipe_stats` 카운터는 트리거 자동 감소
  - `chat_rooms` / `chat_messages` 삭제는 AI 서버 합의 전까지 **보류**

**V1 후속 변경**:
- `users.email` `NOT NULL` → nullable 로 완화 (익명화 시 NULL 처리하기 위해). UNIQUE 제약은 다중 NULL 허용으로 영향 없음
- `User.email` 엔티티도 `nullable = false` 제거 (스키마와 정합)

**산출물 (실제)**:
- 명세서 4건 (`user-me-get`, `user-me-update`, `user-password-change`, `user-withdraw`)
- `domain/user/dto/{UserMeResponse, UserUpdateRequest, PasswordChangeRequest}.java`
- `domain/user/service/UserMeService.java`
- `domain/user/controller/UserMeController.java`
- `domain/user/entity/User.java` 확장 (`changeNickname`, `changePasswordHash`, `anonymize` 메서드)
- `domain/like/repository/LikeRepository`, `domain/scrap/repository/ScrapRepository`, `domain/recipe/repository/PendingRecipeRepository` 에 `deleteAllByUserId` 추가
- `ErrorCode` 에 `SOCIAL_PASSWORD_NOT_ALLOWED`, `ALREADY_WITHDRAWN` 추가

**후속 (2026-05-04 완료)**:
- ~~`user_profiles` 선호도 endpoint~~ — **완료**. `docs/spec/user-preferences-{get,update}.md` (`SPEC-20260504-04/05`). `UserProfile` 엔티티 + `UserProfileRepository` 신설. `UserMeService.getPreferences/updatePreferences` 추가, `UserMeController` 에 `GET/PUT /api/users/me/profile` 노출. 직접 입력 4필드만 갱신 가능, AI 분석 영역은 보호. 탈퇴 트랜잭션도 `userProfileRepository.deleteAllByUserId` 호출하도록 변경 (구 native query 폐기)
- ~~알려진 미흡: 미인증 → 401 응답 일관~~ — **2026-05-04 완료**. `JwtAuthenticationEntryPoint` (401) + `JwtAccessDeniedHandler` (403) 신설. 모든 미인증 / 미인가 응답이 ApiResponse 일관 형식 (`{success: false, message}`) 으로 정상화

**범위 밖 (남은 후속)**:
- 비밀번호 변경 시 기존 토큰 강제 무효화 (stateless JWT 한계)
- 탈퇴 시 AI 서버 측 채팅 데이터 파기 동기화 (AI 팀 합의 보류)
- `recent_recipe_ids` 풀 RecipeListItemResponse 변환 — 본 spec 범위 밖, 클라이언트가 별도 호출

**알려진 동작**:
- 탈퇴된 사용자가 살아있는 토큰으로 재호출 시: 명세상 409 `ALREADY_WITHDRAWN` 이지만 실제로는 `CustomUserDetailsService` 의 `is_blocked` 체크가 먼저 발화하여 **403** 이 떨어짐. 방어 in depth 로 수용 (사용자는 동일하게 차단됨)

---

### Step 5. Chat (read-only) — **완료 (2026-05-03)**
의존: Step 4 완료. **Fridge 폐기 (2026-05-02)** 로 본 Step 의 범위는 Chat 만.

- [x] 5-1. `docs/spec/chat-room-list.md` (`SPEC-20260503-08`), `docs/spec/chat-message-list.md` (`SPEC-20260503-09`) → 구현 (read-only)
- [ ] 5-2. 채팅방 숨김 토글 (`is_active=false`) — **보류**: AI 서버의 `DELETE /api/v1/chat/rooms/{room_id}` 와 권한 책임 합의 후 별도 PR

**산출물 (실제)**:
- 명세서 2건 (`chat-room-list`, `chat-message-list`)
- `domain/chat/{entity,repository,service,controller,dto}/*`
  - `ChatRoom`, `ChatMessage` 엔티티 (read-only — INSERT/UPDATE/DELETE 코드 없음)
  - `ChatRoomRepository.findActiveByUserOrderByLatestUpdated`
  - `ChatMessageRepository.findByRoomIdOrderByCreatedAt`
  - `ChatService.listMyRooms`, `listMessages` (권한 검증 + recipe lookup 일괄 처리)
  - `ChatController` — `GET /api/chat/rooms`, `GET /api/chat/rooms/{roomId}/messages`
  - DTO: `ChatRoomListItemResponse` (AI `ChatRoomResponse` 정합), `ChatRoomListResponse`, `ChatMessageResponse`, `ChatMessageListResponse`
- `RecipeRepository.findActiveByIds(Collection<Long>)` 추가 (메시지의 recipe_ids 일괄 조회 + JOIN FETCH stats)
- `RecipeListMapper.toItems(List<Recipe>)` 추가 (페이지 래퍼 없는 단순 리스트 매핑 — 채팅 메시지의 recipes 필드용)
- `ErrorCode` 에 `CHAT_ROOM_NOT_FOUND` 추가

**검증**:
- 빌드 PASS, docker + bootRun 부팅 OK
- 스모크 테스트:
  · 본인 활성 채팅방 2건 정렬 (`updated_at DESC`) ✓
  · 메시지 목록 — `recipes` 필드 정확 매핑 (활성 1건만, 비활성 1건 제외) ✓
  · 추천 없는 메시지 → `recipes: null` ✓
  · 다른 사용자 채팅방 접근 → 403 FORBIDDEN ✓
  · 본인 비활성 채팅방 → 404 CHAT_ROOM_NOT_FOUND ✓
  · 존재하지 않는 roomId → 404 ✓

**알려진 동작 / 후속**:
- 본 PR 은 read-only. 5-2 (`PATCH /api/chat/rooms/{id}/visibility` 같은 형태) 은 AI 서버 권한 / DELETE endpoint 책임 합의 후
- 메시지 페이징 미적용 (한 채팅방 메시지 < 100 가정). 큰 채팅방 케이스가 생기면 cursor 페이징 도입
- 비활성 레시피 placeholder 표시 (예: "삭제된 레시피") 미적용 — UX 결정 시 추가

---

### Step 6. Admin — **완료 (2026-05-04)**
의존: Step 2 완료, Step 1.5 (V4 통합).

- [x] 6-1. 명세서 + 구현 — pending 검토 / 승인 / 반려:
  - `docs/spec/admin-pending-recipe-list.md` (`SPEC-20260504-01`) — 목록 / 단건 (status 필터, ADMIN 검토용)
  - `docs/spec/admin-recipe-review.md` (`SPEC-20260504-02`) — 승인 / 반려
  - 승인 흐름 (V4 통합 후 갱신): `pending_recipes (PENDING)` → 검증(`PENDING_RECIPE_INCOMPLETE`) → `recipes` INSERT (`is_active=true`, `author_type='USER'`) + 트리거가 `recipe_stats(0,0)` 자동 생성 → `pending_recipes` UPDATE (`status='APPROVED'`, `admin_note`, `reviewed_at`). pending row 보존 (사용자가 `/my` 에서 결과 확인)
  - AI 서버 임베딩 호출은 옵션 B (AI 자체 cron) 잠정 채택으로 **미연동**. 옵션 A 변경 시 Step 7 에서 추가
- [x] 6-2. `docs/spec/admin-user-block.md` (`SPEC-20260504-03`) → 차단 / 해제. 멱등 동작. 탈퇴 사용자(`ALREADY_WITHDRAWN`) 방어
- [x] 6-3. 관리자 권한 가드 — SecurityConfig 기존 `.requestMatchers("/api/admin/**").hasRole("ADMIN")` 활용. 추가 코드 불필요

**산출물 (실제)**:
- 명세서 3건 (`admin-pending-recipe-list`, `admin-recipe-review`, `admin-user-block`)
- `domain/admin/{service,controller,dto}/*` (DTO 8개, Service 2개, Controller 2개)
- `domain/recipe/repository/PendingRecipeRepository.findByStatusOrderByLatest` 추가
- `PendingRecipe` 엔티티에 `markApproved(adminNote)` / `markRejected(reason)` 메서드 추가
- `ErrorCode.PENDING_RECIPE_NOT_REVIEWABLE` (409), `PENDING_RECIPE_INCOMPLETE` (422) 추가

**검증**:
- 빌드 PASS, docker + bootRun OK
- 스모크 테스트 (alice=USER + admin=ADMIN, alice 가 미완성/완성 pending 1건씩 제출):
  · USER 토큰으로 admin endpoint → 403 ✓
  · ADMIN: PENDING 목록 → 2건 ✓
  · 미완성 승인 → 422 PENDING_RECIPE_INCOMPLETE ✓
  · 완성 승인 → 200, recipeId 반환, recipes 에 row 생성 ✓
  · 같은 pending 재승인 → 409 PENDING_RECIPE_NOT_REVIEWABLE ✓
  · 반려 사유 누락 → 400 ✓
  · 반려 정상 → 200, status=REJECTED + admin_note ✓
  · 승인 후 `/api/recipes` 공개 목록에 등장 ✓
  · 승인 후 alice `/my` 에 status=APPROVED + REJECTED 둘 다 노출 ✓
  · 사용자 차단 → 200, alice 의 다음 endpoint 호출에서 403 ✓
  · 차단 해제 → 200 ✓
  · 없는 사용자 차단 → 404 USER_NOT_FOUND ✓

---

### Step 7. AI 서버 연동 모듈
의존: **Step 1.5 (V4)** 완료 + AI 서버 팀과 §0 phase 0-3 옵션 결정.

> **2026-05-02 갱신**: AI 서버 OpenAPI 스냅샷 (`docs/spec/ai-server-contract.md`) 작성 완료. **현 시점 AI 서버에 `/internal/embed` 가 부재** → Phase 0-3 옵션 (B) (AI 자체 cron) 잠정 채택. 옵션 (B) 가 유지되면 Step 7 에서 API 서버가 호출할 AI endpoint 가 사실상 없으므로 본 Step 의 산출물도 가벼워진다.

- [x] 7-0. **AI 서버 contract 문서화** — 완료. `docs/spec/ai-server-contract.md` 참조
- [ ] 7-1. (옵션 B 유지 시) **본 Step 보류** — API 서버가 AI 서버를 호출할 일이 없음. Step 6 승인 워크플로는 `embedding` 에 손대지 않고 `status` 만 변경
- [ ] 7-1'. (옵션 A 채택 시) `global/client/ai/` 패키지 신설, WebClient(또는 RestClient) Bean 등록
- [ ] 7-2. `application.yml` 에 AI 서버 base URL / 타임아웃 (호출 endpoint 가 생긴 시점에)
  - `ai.server.base-url: ${AI_SERVER_BASE_URL:http://43.201.62.254:8000}` (현재 dev 인스턴스 호스트)
  - 실제 path 는 `/api/v1/...` prefix (OpenAPI 기준) — `/internal/*` 가 신설되면 그 path 를 따른다
- [ ] 7-3. (옵션 A) `EmbeddingClient.requestEmbedding(recipeId, fullContent)` 구현. 실제 endpoint·요청·응답 schema 는 AI 측 신설 결과 따라감
- [ ] 7-4. (옵션 A) Admin 레시피 승인 트랜잭션 커밋 후 `EmbeddingClient` 호출 → `recipes.embedding` UPDATE (Step 6-1 과 연결)
- [ ] 7-5. (옵션 A·B 공통) `status='APPROVED' AND embedding IS NULL` 모니터링 (옵션 B 면 단순 메트릭, 옵션 A 면 재시도 잡)
- [ ] 7-6. `ErrorCode` 에 `AI_SERVER_UNAVAILABLE`, `EMBEDDING_FAILED` 추가 (옵션 A 시점에)

**산출물**: `global/client/ai/*`, 승인 흐름 완결, 재시도 잡

---

### Step 8. 운영 준비 (배포 직전) — **부분 완료 (8-4 통합 테스트 2026-05-07 완료)**
의존: Step 1~7 완료.

- [x] 8-1. **CORS 설정 — 2026-05-07 완료**. `cors.allowed-origins` (기본 `localhost:3000,5173` / 운영 env 주입), allowed-methods / allowed-headers / exposed-headers (`Set-Cookie` 노출) / max-age=3600. `allowCredentials=true` 고정 (쿠키 인증 호환). 통합 테스트 4건 (`CorsIntegrationTest`)
- [x] 8-2. **운영 secret 외부화 — 2026-05-08 점검 완료**.
  - `application-prod.yml` 에 default 없는 env 자리표시자: `DB_URL/USERNAME/PASSWORD`, `JWT_SECRET`, `KAKAO_REST_API_KEY/REDIRECT_URI`, `CORS_ALLOWED_ORIGINS` (누락 시 부팅 실패). `AUTH_COOKIE_SECURE: true` 명시 override.
  - `application.yml` 의 default (`localhost:3000` 등) 가 prod 에 새지 않게 강제 — 로컬에만 적용
  - AI 서버 base URL / 내부 토큰은 Step 7 진행 시 추가 (옵션 A 선택 시)
  - 구글 OAuth 는 미실현이므로 prod env 미요구 (`docs/changes/oauth-google-status.md`)
  - README 의 환경변수 표 갱신 — prod 필수 ✅ 표시 + 그룹별 (DB / JWT / CORS / OAuth / AWS) 구조
- [x] 8-3. **로깅 정책 — 2026-05-08 완료**. `RequestIdFilter` 가 `X-Request-Id` 자동 부여 + MDC put + 응답 헤더 echo. `JwtAuthenticationFilter` 가 인증 성공 시 MDC `userId` put. `logback-spring.xml` 패턴에 `[%X{requestId}] [user=%X{userId}]`. PII 로그 금지 정책은 [`docs/changes/logging-policy.md`](changes/logging-policy.md). 통합 테스트 4건 (`RequestIdIntegrationTest`)
- [x] 8-4. **통합 테스트** — 2026-05-07 완료
  - 인프라: Testcontainers (`pgvector/pgvector:pg16` singleton, JVM 단위 공유) + Spring Boot 4 `@SpringBootTest(RANDOM_PORT)` + Spring 6 `RestClient`
  - 베이스: `IntegrationTestSupport` — Postgres testcontainer + Flyway 자동 적용 + 각 테스트 후 9개 테이블 TRUNCATE 격리
  - `AuthCookieIntegrationTest` (8건) — `SPEC-20260507-01` 검증: signup/login Set-Cookie + 헤더/쿠키/둘다/없음 분기 + logout / withdraw 만료
  - `RecipeFlowIntegrationTest` (3건) — E2E: alice signup → recipe submit → admin approve (recipe_stats 트리거 검증) → bob like/scrap (counter 트리거) → /scraps/my → /recipes/my APPROVED 갱신 → alice 탈퇴 → 닉네임 치환 + bob counter 보존. + 422 PENDING_RECIPE_INCOMPLETE / 403 admin guard
  - 폐기: 옛 `src/test/java/com/example/demo/DemoApplicationTests.java` (잘못된 패키지) 제거 + `com.naengo.api_server.integration` 으로 정식 이전
  - build.gradle: `testcontainersVersion=1.21.4` BOM (1.20.4 의 docker-java 가 client API 1.32 → docker daemon 1.40 요구와 충돌하여 업그레이드)
- [ ] 8-5. AWS 배포 파일럿 (RDS + S3 + EC2/ECS 중 택일, 인프라 담당자와 합의)

**산출물**: 운영 가능 상태의 서버 + 통합 테스트 11건 (2026-05-07 시점)

---

### 병렬 처리 가능한 잡일 (언제든 가능)
스텝 의존성과 무관하게 시간 생기면 소화.

- [x] ~~README 에 로컬 개발 환경 구축 가이드 작성~~ — `README.md` + `docker-compose.yml` (pgvector/pg16) 커밋
- [x] ~~`.gitignore` 점검~~ — 공백 구분 버그 수정, `.env*` / `*.log` / `.DS_Store` 추가. `application-{local,prod}.yml` 은 **버전 관리에 포함** (env 자리표시자만 보유, 실 secret 은 환경변수)
- [x] ~~DB 관련 수동 검증 절차 문서화~~ — `docs/db-testing-guide.md` (Step 8 에서 JUnit 자동화 전까지 사용)
- [x] ~~`ErrorCode` 선언만 있고 쓰이지 않는 Recipe/Chat 코드 정리~~ — `RECIPE_ALREADY_LIKED/SCRAPPED`(토글 설계상 불필요), `CHAT_ROOM_NOT_FOUND/SESSION_NOT_FOUND`(Step 5 에서 재도입) 4건 제거. 정책: "사용되는 시점에 추가"
- [ ] 예외 메시지 i18n 필요 여부 검토
- [ ] pre-commit 훅 또는 Spotless 같은 포맷터 도입 검토
- [x] ~~기동 시 표시되는 사소한 경고 2건 정리~~ — **2026-05-07 완료**
  - [x] `HHH90000025: PostgreSQLDialect ...` → `application.yml` 의 `spring.jpa.properties.hibernate.dialect` 라인 제거 (Hibernate 7 자동 선택)
  - [x] `spring.jpa.open-in-view is enabled by default` → `application.yml` 에 `spring.jpa.open-in-view: false` 명시 (REST API 서버 정석)
  - 검증: `bootRun` 부팅 로그에 두 경고 모두 미발생. 잔존 WARN 은 V2 의 `IF NOT EXISTS` 가 V1 컬럼을 skip 하는 정상 동작 2건만
