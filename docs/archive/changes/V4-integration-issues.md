# V4__fixed_schema.sql 통합 이슈 정리

## 0. 메타

| 항목 | 값 |
|---|---|
| 변경 이력 ID | `V4-integration-issues` (마이그레이션 파일에 대한 메모이므로 SPEC 번호 없음) |
| 대상 | `src/main/resources/db/migration/V4__fixed_schema.sql` (commit `48204b4`) |
| 변경 종류 | 정책 결정 보류 / 통합 이슈 보고 |
| 발견 경로 | 본인 검토 (V4 push 후 정합성 점검) |
| 작성자 | API 서버 담당자 |
| 작성일 | 2026-05-02 |
| 관련 문서 | [`docs/spec/ai-server-contract.md §5`](../spec/ai-server-contract.md), [`docs/api-server-tasks.md §1.5`](../api-server-tasks.md) |

---

## 1. 무엇이 문제인가 (What)

`V4__fixed_schema.sql` 이 V1~V3 위에 그대로 적용되지 않고, 적용되더라도 우리 코드(엔티티, 서비스, 명세) 와 충돌한다. 12건의 이슈가 식별됨. 상세는 [`ai-server-contract.md §5.A`](../spec/ai-server-contract.md) 표 참조. 핵심:

- **🔴 Flyway 적용 실패**: `CREATE TABLE Users (...)` 가 `IF NOT EXISTS` 없이 작성됨. V1 의 `users` 와 충돌.
- **🔴 V3 `users.deleted_at` / V1 `fridge` 누락**: 회원 탈퇴 익명화·Fridge 도메인 전제가 무너짐.
- **🔴 `BIGSERIAL → SERIAL`**: JPA `Long` 매핑과 타입 불일치.
- **🔴 `Recipe` 엔티티 전면 충돌**: `full_content`/`source`/`status` (현 엔티티) ↔ `description`/`content`/`author_type`/`is_active` (V4). Hibernate validate 실패 + Recipe 도메인 코드 전면 재작성 필요.
- **🟠 트리거 vs 애플리케이션 카운터 증감 충돌**: V4 의 `trigger_likes_count`/`trigger_scrap_count` 가 `Recipe_Stats` 자동 갱신. `api-server-tasks.md §6 Step 3-3` 의 애플리케이션 직접 증감 정책과 동시 작동 시 카운트 두 배.

---

## 2. 왜 그렇게 됐나 (Why)

V4 는 **AI 서버 OpenAPI(`docs/api-1.json`) contract 와 정합** 시키려는 합당한 의도로 작성됐다. AI 의 `RecipeResponse` 와 `ChatMessageResponse` 모델을 거의 1:1 매핑한 것은 적절. 다만:
- Flyway 의 적용 모델 (V1 부터 순차 누적) 을 의식하지 않고 fresh-DB 의 단일 스키마 dump 처럼 작성됨
- V3 의 `deleted_at` / V1 의 `fridge` 같은 V4 의도와 무관한 **이전 결정**이 V4 에서 누락
- 기존 엔티티 / 서비스 / 명세는 손대지 않은 상태라 코드 ↔ 스키마 드리프트 다수

---

## 3. 어떻게 풀 것인가 (How) — 사용자 결정 필요

[`ai-server-contract.md §5.B`](../spec/ai-server-contract.md) 의 옵션 (a) / (b) / (c) 중 1개 채택:

### 옵션 (a) — V4 를 ALTER 기반으로 재작성 **(권장)**
- 현 `V4__fixed_schema.sql` 폐기 (또는 `git rm`) → 새 `V4__align_with_ai_contract.sql` 작성
  - `ALTER TABLE recipes ADD COLUMN description TEXT NOT NULL DEFAULT '' ...` 형태로 11개 컬럼 추가
  - `ALTER TABLE chat_rooms ALTER COLUMN room_id ...` (타입 변경은 데이터 wipe 필요할 수 있음)
  - `CREATE TABLE user_profiles (...)`, `CREATE TABLE pending_recipes (...)`, `CREATE TABLE chat_messages (...)` 신설
  - `DROP TABLE session_logs` (혹은 보존, 정책 결정)
  - Likes/Scraps 카운터 트리거 추가 시 애플리케이션 측 카운터 증감 코드 제거 결정 필요
- V1~V3 의 `deleted_at` / `fridge` 보존
- 가장 안전. 운영 데이터 살아 있는 환경에서도 진행 가능

### 옵션 (b) — V1~V3 폐기 + V4 단일화
- V1, V2, V3 파일 삭제. V4 파일을 `V1__init.sql` 로 rename
- `flyway_schema_history` 가 V1~V3 적용 기록을 들고 있는 환경은 모두 wipe 필요 (`docker compose down -v`)
- 협업자별 로컬 DB 도 모두 wipe 필요 — 안내 필수
- 운영 미배포 단계 한정. dev DB 만 있으면 가능
- 옵션 (a) 보다 깔끔하지만 협업자에게 부담

### 옵션 (c) — 현 V4 에 `DROP ... IF EXISTS CASCADE` 프리픽스 추가
- 모든 `CREATE TABLE X` 위에 `DROP TABLE IF EXISTS X CASCADE;` 한 줄씩 prepend
- 추가로 V3 `deleted_at`, V1 `fridge` 를 V4 에서 직접 정의해야 함
- DB 데이터 wipe — dev/MVP 한정
- 빠른 우회책이지만 부채 누적

---

## 4. 명세서에 미치는 영향

옵션 (a/b/c) 어느 것이든 다음 명세 / 코드는 갱신 필요:

- 엔티티: `User`, `Recipe`, (신설) `UserProfile`, `PendingRecipe`, `ChatRoom`, `ChatMessage`
- 서비스: `RecipeService.create()` 타깃 변경 (recipes → pending_recipes), 승인 흐름 INSERT-DELETE 패턴으로 변경
- enum: `RecipeStatus`, `RecipeSource` 폐기 → `RecipeAuthorType` 신설
- 명세 v2: `SPEC-20260422-02-CL01` 가 이미 메모. v2 발행 시점 결정 필요
- `api-server-tasks.md §6 Step 3-3`: Like/Scrap 트랜잭션의 카운터 증감 — V4 트리거와 충돌 → 정책 통일

---

## 5. 회귀 테스트 / 검증

옵션 결정 후:
- [ ] `docker compose down -v && docker compose up -d && ./gradlew bootRun` → Flyway 1~4행 모두 `success = true`
- [ ] Hibernate `validate` 통과 (엔티티 동시 갱신 후)
- [ ] `db-testing-guide.md` 의 §1, §2 통과
- [ ] Like/Scrap 1회 토글 후 `recipe_stats.likes_count` / `scrap_count` 가 정확히 1만 증가 (이중 증가 검증)
- [ ] 기존 `SPEC-20260422-02/03/04` 테스트 케이스가 v2 명세 + V4 후 코드에서 통과

---

## 6. 후속 작업 (Follow-up)

- [ ] **사용자 결정**: 옵션 (a) / (b) / (c) 채택
- [ ] V4 통합 작업 PR (코드 동시 갱신 — 범위가 커서 별도 브랜치 권장)
- [ ] `recipe_stats` 카운터 정책 통일 (트리거 단독 vs 애플리케이션 단독)
- [ ] AI 서버 팀과 미합의 항목 회의 (`api-server-tasks.md §5` 참조)
- [ ] `docs/api-1.json` 의 OpenAPI 버전이 올라가면 본 갭분석 갱신

---

## 7. 학습 / 회고

- Flyway 마이그레이션은 **누적 적용** 모델이라는 사실을 V4 작성 가이드 (e.g. `api-server-tasks.md §1.5 "왜 V1 을 직접 고치지 않고 V4 로 가는가"`) 에 더 강조해야 함. 본 케이스에서는 그 가이드가 V4 작성 시점에 충분히 강하지 않았음.
- 외부 contract 정합 보정은 (i) 스키마 ALTER 와 (ii) 엔티티/서비스/명세 갱신을 **항상 같이** 묶어야 한다. 둘 중 하나만 선행되면 빌드/기동 단계에서 즉시 깨진다.
