# 배포 진척 추적 (2026-05-21 시작)

> 채팅 컨텍스트가 사라져도 다음 세션에서 즉시 이어갈 수 있도록, 본인 수행
> 액션 / 입력값 / 의존성 / 막힌 지점을 한 페이지에 모은다. 절차 자체는
> [`deploy-env.md`](deploy-env.md) 가 정본, 본 문서는 "어디까지 했나" 만.

상태 표기: ✅ 완료 / 🟡 진행 중 / ⏸ 대기 / ❌ 막힘 / 🚫 N/A

---

## 0. 입력값 / 참조 (계속 채워 나감)

| 키 | 값 |
|---|---|
| AWS Account ID | `518056141724` |
| Region | `ap-northeast-2` |
| GitHub repo | `2026-UOS-Capstone-Design-Team-4/naengo-api-server` |
| ECR URI | `518056141724.dkr.ecr.ap-northeast-2.amazonaws.com/naengo-api-server` |
| IAM role (CI) | `arn:aws:iam::518056141724:role/github-actions-ecr-push` |
| RDS host | `naengo-db-001.cz6su4g68byy.ap-northeast-2.rds.amazonaws.com` |
| RDS DB / user | `naengo_db` / `naengo` |
| DB 운영 주체 | 팀원 (V5 의 users 컬럼 변경 반영 받음) |
| SSL 옵션 결정 | A — Dockerfile 에 RDS CA 번들 포함 + `sslmode=verify-full` |
| ECS Cluster | `arn:aws:ecs:ap-northeast-2:518056141724:cluster/naengo` (name: `naengo`) |
| ECS Task Definition | `arn:aws:ecs:ap-northeast-2:518056141724:task-definition/naengo-api-server:1` (family `naengo-api-server`, rev 1) |
| ECS Task Execution Role | `arn:aws:iam::518056141724:role/ecsTaskExecutionRole` (managed `AmazonECSTaskExecutionRolePolicy` + inline `naengo-secrets-fetch`) |
| ECS Service 이름 | (미정 — B4-f) |
| CloudWatch Log Group | `/ecs/naengo-api-server` (30일 보존) |
| Default VPC | `vpc-038c8eb56e23d5bb5` (172.31.0.0/16) — RDS 와 동일 VPC |
| Public subnets (4 AZ) | `subnet-0609c0a1180692f1d` (a) / `subnet-0417a2a8b7cb1c616` (b) / `subnet-0752c9e933903abf3` (c) / `subnet-057d3291db1e11d00` (d) |
| ALB SG | `sg-0f143ba6a8d9997d2` (`naengo-api-server-alb-sg`, inbound 80 from 0.0.0.0/0) |
| ECS task SG | `sg-0001862a6ba20c4cf` (`naengo-api-server-ecs-sg`, inbound 8080 from ALB SG) |
| RDS SG | `sg-0898460971d5b8d04` (inbound 5432 from `ec2-rds-1` + 우리 ECS SG) |
| ALB ARN | `arn:aws:elasticloadbalancing:ap-northeast-2:518056141724:loadbalancer/app/naengo-api-server-alb/159ba31da2dc086e` |
| **ALB DNS** | **`naengo-api-server-alb-176175450.ap-northeast-2.elb.amazonaws.com`** (도메인 부착 전 검증용 — `http://<이 호스트>/` 로 접근) |
| Target Group ARN | `arn:aws:elasticloadbalancing:ap-northeast-2:518056141724:targetgroup/naengo-api-server-tg/1303d640c7a0ed98` (HTTP 8080, target-type ip, health check `/`) |
| Listener:80 ARN | `arn:aws:elasticloadbalancing:ap-northeast-2:518056141724:listener/app/naengo-api-server-alb/159ba31da2dc086e/bebdb0686756e3b2` |
| 운영 도메인 (`api.???`) | (미정) |
| 카카오 운영 redirect URI | (미정) — 운영 도메인 결정 후 |
| Secrets Manager ARN | `naengo/prod/db` = `arn:aws:secretsmanager:ap-northeast-2:518056141724:secret:naengo/prod/db-iUa2In` |
| | `naengo/prod/jwt` = `arn:aws:secretsmanager:ap-northeast-2:518056141724:secret:naengo/prod/jwt-9m02fH` |
| | `naengo/prod/kakao` = `arn:aws:secretsmanager:ap-northeast-2:518056141724:secret:naengo/prod/kakao-8yZdEV` |

---

## A. 코드/로컬 정리 (본인, 즉시)

| # | 항목 | 상태 | 메모 |
|---|---|---|---|
| A1 | `.env` DB_URL `jdbc:` 접두사 + `user:pass@` 제거 | ✅ | host 만 남김 (`jdbc:postgresql://<host>:5432/<db>?sslmode=verify-full&sslrootcert=/app/global-bundle.pem`) |
| A2 | `KAKAO_REDIRECT_URI` 운영 도메인으로 교체 | ⏸ | 운영 도메인 결정 후. 카카오 콘솔 등록값과 1글자 일치 필수 |
| A3 | `CORS_ALLOWED_ORIGINS=*` 좁히기 | ⏸ | front/admin 운영 도메인 확정 후. 현재 임시 wildcard |
| A4 | Dockerfile 에 RDS CA 번들 다운로드 단계 추가 (SSL 옵션 A) | ✅ | builder apt curl + runtime COPY. commit `1eaf4da` |
| A5 | 빈 운영 env 채우기 (`AUTH_COOKIE_SAME_SITE`, `AWS_REGION`) | ⏸ | Secrets Manager 이관 단계와 함께 |
| A6 | Spring Initializr 디폴트 정정 (`settings.gradle` rootProject.name, `build.gradle` group) | ✅ | `demo` → `naengo-api-server`, `com.example` → `com.naengo`. JAR 파일명 변경 — Dockerfile glob 안전 |

---

## B. AWS 인프라 (본인 = 인프라 담당)

| # | 항목 | 상태 | 메모 |
|---|---|---|---|
| B1 | ECR 리포 생성 | ✅ | Mutable / AES256 / Scan-on-push. URI 위 §0 참조 |
| B2 | GitHub OIDC + IAM role + repo Secrets 3종 | 🟡 | 1차 CI test 30/30 PASS ✓ / build-and-push 가 OIDC AssumeRole 에서 실패 → **trust policy `sub` 가 placeholder `OWNER/REPO` 그대로였음**. 2026-05-21 `update-assume-role-policy` 로 실제 repo 경로(`repo:2026-UOS-Capstone-Design-Team-4/naengo-api-server:ref:refs/heads/main`) 로 정정. 워크플로 재실행 대기 |
| B2-a | IAM identity provider (계정당 1회) | ✅ | `arn:aws:iam::518056141724:oidc-provider/token.actions.githubusercontent.com` |
| B2-b | IAM role `github-actions-ecr-push` + trust + ecr-push policy | ✅ | trust = main 브랜치 only. 2026-05-21 sub 정정 |
| B2-c | GitHub Secrets (`AWS_ROLE_TO_ASSUME` / `AWS_REGION` / `ECR_REPOSITORY`) | ✅ | 콘솔로 주입 |
| B2-d | CI test job 30/30 PASS | ✅ | run 26194902810 / 26195691470 둘 다 test job 성공 |
| B2-e | CI build-and-push job → ECR 이미지 푸시 | 🟡 | trust 정정 후 재실행(attempt 2): OIDC ✓ / ECR login ✓ / Buildx ✓ / **Build and push 가 `useradd exit 4` 로 실패** — runtime base 가 UID 1000 점유. UID/GID 10001 로 정정 (Dockerfile 수정). 다음 push 로 재트리거 |
| B3 | Secrets Manager 3종 생성 + ECS Execution Role 권한 | 🟡 | secret 생성 ✅. Execution Role 권한은 B4 와 함께 |
| B3-a | `naengo/prod/db` (DB_URL/DB_USERNAME/DB_PASSWORD) | ✅ | ARN: `...:secret:naengo/prod/db-iUa2In`. 값 = `.env` 정정된 것 그대로 |
| B3-b | `naengo/prod/jwt` (JWT_SECRET) | ✅ | ARN: `...:secret:naengo/prod/jwt-9m02fH`. C3 (a) 채택: 현 값을 그대로 AI 팀에 전달 → 양측 동일 SECRET. 이후 rotate 시 양측 동시 update + ECS restart |
| B3-c | `naengo/prod/kakao` (KAKAO_REST_API_KEY/KAKAO_REDIRECT_URI) | ✅ | ARN: `...:secret:naengo/prod/kakao-8yZdEV`. **KAKAO_REDIRECT_URI 는 localhost placeholder** — A2(운영 도메인 결정) 후 update 필요 |
| B3-d | ECS Task Execution Role 에 `secretsmanager:GetSecretValue` 부여 | ⏸ | B4 (Task Definition) 와 함께 처리. role 자체가 계정에 없으면 ECS 첫 클러스터 생성 시 자동 생성됨 |
| B4 | ECS Cluster + Task Definition + Service (Fargate) | 🟡 | 진행 중 — sub items 아래 |
| B4-a | ECS Cluster `naengo` 생성 | ✅ | ARN §0 |
| B4-b | Task Execution Role + Secrets fetch 권한 + Log Group | ✅ | `ecsTaskExecutionRole` + inline `naengo-secrets-fetch` + `/ecs/naengo-api-server` |
| B4-c | Task Definition 등록 (rev 1) | ✅ | image SHA pin `63bab5b9...`, cpu/mem 512/1024, env 2 + secrets 6, awslogs |
| B4-d | 보안그룹 3변경 (ALB SG / ECS SG / RDS SG inbound) | ✅ | ALB-SG 80 from internet, ECS-SG 8080 from ALB-SG, RDS-SG 5432 from ECS-SG (기존 ec2-rds-1 보존) |
| B4-e | ALB + Target Group + listener:80 | ✅ | ALB DNS / TG / Listener ARN §0. TG targets 비어 있음 (Service 미생성, 정상) |
| B4-f | ECS Service 생성 (Cluster + TaskDef + TG + SG + subnets) | ✅ | service `naengo-api-server` desired=1, assignPublicIp=ENABLED |
| B4-g | 실행 확인 (Task running + TG healthy + `/` 200) | ✅ | Task `5da1ffd...` healthy, ALB DNS `/` 200 / `/api/v1/users/me` 401 / `POST /auth/signup` 201 + user_id=4 발급 + Set-Cookie 정상 |
| B5 | ACM 인증서 + Route53 + ALB listener:443 | ⏸ | 운영 도메인 결정 필요. ALB DNS (`naengo-api-server-alb-176175450...`) 로 80 검증 완료 |
| B6 | 보안그룹 (B4-d 와 통합 완료) | ✅ | B4-d 에 흡수 |
| B7 | RDS 사전 점검 | ✅ | DBv5 적용 상태 / Flyway baseline 자동 init / Hibernate validate 통과 |
| B8 | 첫 배포 검증 | ✅ | signup 201, JWT 발급, 쿠키 Secure+HttpOnly+SameSite=Lax |
| B7 | **RDS 사전 점검** — V1~V5 적용 가능 상태인지 확인 | ⏸ | **DB 운영 팀원에게 협의 필요** — 우리가 V4(레시피 정규화) + V5(social_accounts 분리) 적용 예정임을 알림. 다른 DDL 충돌 없는지 |
| B8 | 첫 배포 검증 (Flyway V1~V5 적용 / `/` 200 / signup 201 / 카카오 흐름 B) | ⏸ | [`deploy-env.md §4, §6`](deploy-env.md) |

---

## C. 크로스팀 합의 (본인이 메신저)

| # | 상대 | 안건 | 상태 |
|---|---|---|---|
| C0 | **DB 팀원** | **V4(레시피 정규화) + V5(users 컬럼 제거) 적용 예정 통지 + RDS 사전 점검 협의** | ✅ |
| C1 | AI 팀 | V5 후 AI 코드가 `users.provider`/`provider_id` 컬럼을 SELECT 한 적 있다면 `user_identities` JOIN 으로 전환 필요 | 🚫 N/A — 옵션 A 채택으로 DBv5 의 users 에 그 컬럼 자체가 없음. AI 가 SELECT 할 가능성 0 |
| C2 | AI 팀 | 레시피 정규화 합동 컷오버 일정 | ✅ 사실상 완료 — DB 팀원이 DBv5(정규화 완성형) 적용 + AI 003/004/005 적용 + 우리 옵션 A align. 양측 모두 DBv5 위에서 동작 |
| C3 | AI 팀 | `JWT_SECRET` 운영 값 동일 공유 (D-2). 옵션 (a) 채택: 현 값 그대로 AI 에 전달 (64자 base64). AI 가 받은 후 자기 Secrets Manager 저장 + 서비스 재시작 → login 토큰을 AI endpoint 호출에 전달 검증 | 🟡 전달 대기 |
| C4 | front/AI | `/me/profile` 변경 의미 합의 (우리 PATCH 교체 vs AI POST/DELETE) | 🟢 우리 측 작업 0 — front 가 우리 endpoint 호출 시 PATCH 정합. AI 의 POST/DELETE 와 둘 다 살아 있으므로 front 가 어느 쪽 채택할지만 자기 결정 |

---

## D. 운영 위생 / 보안

| # | 항목 | 상태 | 메모 |
|---|---|---|---|
| D1 | `.env` 평문 secret → Secrets Manager 이관 후 로컬 `.env` 에선 dev-only 값 또는 제거 | ⏸ | B3 와 함께 |
| D2 | CORS 좁힘 (= A3) | ⏸ | |
| D3 | CloudWatch billing alarm + 5xx 율 + 인증실패 율 모니터링 | ⏸ | 첫 배포 후 |
| D4 | IAM 사용자 권한 좁히기 (현재 AdministratorAccess) | ⏸ | 첫 배포 안정화 후 ECR/Secrets/ECS/RDS-describe 만 가진 좁은 policy 로 |
| D5 | IAM 사용자 MFA 활성화 | ⏸ | |

---

## 현재 막힌 지점 / 다음 액션 (우선순위)

🎉 **첫 배포 완료 (2026-05-21)** — ALB DNS `http://naengo-api-server-alb-176175450.ap-northeast-2.elb.amazonaws.com/` 정상 응답. signup → user_id=4 발급 + 쿠키 정상.

남은 작업:
1. **B5 / A2 / A3** — 운영 도메인 결정 후 ACM + Route53 + listener:443 부착 → 그 시점에 `update-secret` 으로 KAKAO_REDIRECT_URI prod 값 교체 + CORS 좁힘
2. **C3** — 옵션 (a) 채택. 우리 현 `JWT_SECRET` 그대로 AI 에 안전 채널 전달 → AI 측 적용 후 토큰 cross-verify (login 토큰 → AI endpoint 호출)
3. **D3** — CloudWatch billing alarm + 5xx 율 + 인증실패 율 monitoring
4. **D4** — IAM 사용자 권한 좁히기 (현 AdministratorAccess → ECR/Secrets/ECS/RDS-describe 만)

> 운영 도메인이 정해지지 않은 상태에서도 B3/B4 까진 placeholder 로 진행 가능 (KAKAO_REDIRECT_URI 만 임시값). B5(ALB+Route53)는 도메인 필요.

## 변경 이력

- 2026-05-21 신설. B1 완료, A4 commit `1eaf4da`. B2 CI 1차 시도 — test ✓ / build-push 가 OIDC AssumeRole 실패(trust policy sub placeholder). trust 정정 후 재실행 attempt 2 — OIDC ✓ / build-push 가 `useradd exit 4` (UID 1000 충돌) 실패. Dockerfile UID/GID 10001 로 정정 + A6 Spring Initializr 디폴트 정정 동시 진행.
- 2026-05-21 B2 완료 (CI 통과, ECR 에 이미지 누적). C0 합의 완료. **B3 Secrets Manager 3종 생성 완료** (db/jwt/kakao). JWT 와 KAKAO_REDIRECT_URI 는 추후 `update-secret` 필요.
- 2026-05-21 **B4-a/b/c/d 완료**: Cluster `naengo` + Execution Role + Log Group + Task Definition rev 1 (SHA pin `63bab5b9...`, 0.5vCPU/1GB) + SG 3변경 (ALB SG / ECS SG / RDS SG inbound 에 ECS SG 추가). VPC 토폴로지: default VPC + 4 AZ public subnets 사용. 다음: B4-e (ALB+TG) → B4-f (Service).
- 2026-05-21 **B4-e 완료**: TG (HTTP 8080, target-type ip, health check `/`) + ALB (internet-facing, 4 AZ, ALB-SG) + listener:80. ALB DNS = `naengo-api-server-alb-176175450.ap-northeast-2.elb.amazonaws.com`. TG targets 비어 있음 (Service 미생성). 다음: B4-f.
- 2026-05-21 **B4-f 완료 / B4-g 실패**: Service 생성 + Task RUNNING ✓ / Spring 부팅 실패 (`FlywayException: Found non-empty schema "public" but no schema history table`). 원인: DB 팀원이 운영 RDS 에 DBv5 를 직접 SQL 로 적용 → flyway_schema_history 미생성. ECS Service desired=0 로 실패 루프 중단. **DB 팀원의 DBv5.sql 분석 결과 우리 V1~V5 와 9가지 구조 차이** (users.username/user_identities/user_recipes/PK 타입 등). **옵션 A 결정** — 우리(api-server) 가 DBv5 전면 align (10 phase, ~60~70 파일). C0 합의 → 사실상 무효 (DB 팀원 명명을 우리가 채택).
- 2026-05-21 **옵션 A Phase 1 완료**: Flyway V1~V5 폐기, 신규 V1 = DBv5.sql 통째(broken index/trigger 2건만 정정), `application-prod.yml` 에 `baseline-on-migrate=true` + `baseline-version="1"` 설정.
- 2026-05-21 **옵션 A Phase 2~7 완료**: User entity (email→username, deletedAt 제거, Long→Integer) / SocialAccount→UserIdentity (테이블 user_identities, 컬럼명 정합, AuthProvider 4종 확장) / UserProfile (Integer + JSONB NOT NULL DEFAULT) / PendingRecipe→UserRecipe (테이블 user_recipes, URL `/api/v1/[admin/]user-recipes`) / Recipe + 자식 entities + Like/Scrap/Chat Long→Integer ripple / JWT·SecurityUtil Integer / 통합테스트 7 파일 일괄 정합. **Testcontainers 30/30 PASS** (Auth 8 / Cors 4 / ProfileChat 3 / Recipe 6 / RequestId 4 / SocialAuth 5).
- 2026-05-21 **🎉 첫 운영 배포 성공**: CI 통과 → ECR `:8cbea88...` → Task Definition rev 2 → Service desired=1 force-new-deployment. Spring 부팅 63s, Flyway baseline 자동 init, Tomcat 8080, ALB TG healthy. 검증: `/` 200 + `/api/v1/users/me` 401 + signup 201 (user_id=4 발급 + Secure HttpOnly 쿠키). B4/B6/B7/B8 모두 완료. 남은: B5(도메인+ACM+Route53+443), A2/B3-c update(KAKAO redirect URI), A3(CORS 좁힘), C3(AI JWT 공유), D3/D4(모니터링·IAM 좁힘).
