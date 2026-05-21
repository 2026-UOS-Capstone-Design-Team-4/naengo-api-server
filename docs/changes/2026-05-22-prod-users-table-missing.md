# 운영 RDS `public.users` 테이블 부재 — 옵션 A 후 첫 e2e 시 발견

## 0. 메타

| 항목 | 값 |
|---|---|
| 변경 이력 ID | `2026-05-22-prod-users-table-missing` |
| 발견 시각 | 2026-05-22 05:19 KST (운영 ALB e2e 재검증 중) |
| 영향 범위 | 운영 — 모든 DB-touching endpoint 500 |
| 로컬 / 통합테스트 | 영향 없음 (30/30 + 33/33 PASS) |
| 작성자 | API 서버 담당자 |

---

## 1. 무엇이 일어났나

- 2026-05-21 19:31 KST: ECS Task `5da1ffd...` 시작 — Spring 부팅, Flyway baseline, Hibernate validate 모두 성공. `POST /api/v1/auth/signup` 으로 `user_id=4` 정상 발급 (deploy-status §B4-g 검증 완료).
- 2026-05-22 05:19 KST: 동일 task 가 여전히 RUNNING 상태인데 `POST /auth/signup` 이 500. CloudWatch 로그:
  ```
  Caused by: org.postgresql.util.PSQLException:
    ERROR: relation "users" does not exist
  ```

## 2. 왜 ECS / ALB 가 이를 못 잡았나

| 레이어 | 왜 못 잡았나 |
|---|---|
| Hibernate `ddl-auto: validate` | 부팅 시점엔 테이블이 있었음 → validate 통과 → metadata 캐시됨. 런타임 DROP 은 감지 불가 |
| ALB Target Group healthcheck `GET /` | Spring 기본 페이지 → DB 미접근 → 항상 200 → TG healthy |
| CloudWatch `naengo-tg-unhealthy-host` alarm | TG 가 healthy 로 보이므로 발화 안 함 |
| CloudWatch `naengo-alb-target-5xx` alarm | 임계 5건/5분 — 실제 사용자가 없어서 5xx 누적 안 됨 (오늘 e2e 가 첫 사용자) |

**모니터링 사각 인정.** 후속 작업으로 healthcheck 보강 (D-후속).

## 3. 코드 정합성은 무결

| 검증 | 결과 |
|---|---|
| Testcontainers 통합테스트 30/30 | ✅ 2026-05-22 |
| 로컬 bootRun + docker-compose Postgres + curl 33/33 (`scripts/e2e-smoke-prod.sh`) | ✅ 2026-05-22 |
| 운영 코드 SHA | `8cbea88...` (Task Def rev 2 — 옵션 A 코드) |

→ 옵션 A 적용 코드는 정확히 동작. 운영 장애는 **DB 측 변경**.

## 4. 원인 — RDS PostgreSQL 로그 분석 (2026-05-22 05:35 KST 조사)

### 4.1 AWS 레벨 (read-only 조회)

| 도구 | 결과 |
|---|---|
| `aws rds describe-events --source-identifier naengo-db-001 --duration 1440` | backup 2건 (정상) 만 — 다른 modify/reboot 없음 |
| `aws cloudtrail lookup-events --lookup-attributes ResourceName=naengo-db-001` | 가장 최근 RDS API call 이 2026-04-23 ModifyDBInstance — 24h 내 RDS API 활동 0 |

→ **AWS 콘솔/API 레벨에서는 정상.** 사고는 PostgreSQL 내부 (SQL 레이어) 에서 발생.

### 4.2 PostgreSQL 로그 (`error/postgresql.log.2026-05-21-19/20`) — 결정적 증거

| 시각 (UTC) | 시각 (KST) | 클라이언트 IP | 이벤트 |
|---|---|---|---|
| `2026-05-21 19:35:32` | **2026-05-22 04:35:32** | `172.31.3.36` (DB 작업자 머신, 우리 VPC 내부) | `ERROR: relation "pending_recipes" does not exist` + 매우 큰 multi-statement (CREATE EXTENSION + CREATE TABLE users/user_identities/user_profiles/recipe_*/user_recipes/...) — DBv5 schema 를 다시 적용 시도하는 SQL 이지만 그 안에 **이미 존재 안 하는 `pending_recipes`** 를 참조하는 statement (`DROP TABLE pending_recipes` 추정) 가 포함되어 ERROR → transaction abort |
| `2026-05-21 20:19:18` | **2026-05-22 05:19:18** | `172.31.10.98` (우리 ECS task IP) | `relation "users" does not exist` — e2e 시작. ECS task 가 SELECT users 시 발화. 이후 동일 에러 6번 연속 (e2e 의 7개 DB-touching 요청) |

### 4.3 해석

1. **04:35 KST DB 작업자 (`naengo` master 사용자) 가 큰 schema 변경 SQL 을 실행.**
   - 그 SQL 은 옛날 schema(`pending_recipes`) 와 신 schema(`user_recipes`) 가 섞인 형태
   - `DROP TABLE pending_recipes` 등이 IF EXISTS 없이 포함 → 첫 ERROR
   - PostgreSQL 가 멀티 statement 어떻게 처리했는지에 따라 schema 일부만 적용되고 abort
2. **결과적으로 `users` 테이블이 사라짐** — DROP 은 성공, CREATE 단계 전 fail 추정
3. ECS task 는 이미 RUNNING 이므로 Hibernate metadata cache 유지 → 재시작 전까지 모름

### 4.4 책임 분기

| 사항 | 책임 |
|---|---|
| 옵션 A 코드 정합성 | ✅ 무결 (통합 30/30 + 로컬 e2e 33/33) |
| 운영 RDS schema 손상 | DB 작업자 — `pending_recipes` 참조 SQL 실행 |
| ECS 가 즉시 감지 못 함 | 우리 — healthcheck path `/` 가 DB 미접근 (재발 방지 §6) |
| Alarm 발화 안 됨 | 우리 (사용자 트래픽 부재로 5xx 누적 미달) + healthcheck 사각 |

## 5. 복구 계획

| Step | 액션 | 위험 |
|---|---|---|
| 1 | DB 팀원과 원인 확인 + AI 팀 적재 데이터 손실 가능성 통보 | - |
| 2 | (필요 시) RDS 스냅샷 시점 복원 OR DBv5.sql 다시 적용 | 🟡 AI 데이터 손실 |
| 3 | ECS force-new-deployment → Flyway 재baseline (clean schema 면 V1=DBv5 자동 적용) | 🟢 |
| 4 | `scripts/e2e-smoke-prod.sh` 운영 BASE 로 재실행 → 33/33 확인 | 🟢 |

## 6. 재발 방지

- [ ] ALB Target Group healthcheck path 를 DB 핑 포함하는 `/actuator/health` 로 교체 (Spring Actuator 의존성 추가 + `application-prod.yml` endpoint expose)
- [ ] DB 팀원과 schema 변경 시 사전 통지 SOP 합의
- [ ] RDS 파라미터 `log_statement=ddl` ON (DDL 추적 — 추가 비용 미미)
- [ ] (검토) RDS Performance Insights 활성화
