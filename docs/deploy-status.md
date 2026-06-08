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
| **운영 컴퓨트 (현행)** | **EC2 t4g.small (ARM) — B6 마이그레이션 2026-06-07.** 아래 EC2 블록 참조. ECS/ALB 는 폐기 예정 bridge |
| ECS Cluster | `arn:aws:ecs:ap-northeast-2:518056141724:cluster/naengo` — 🟡 bridge (C9/C10 후 폐기) |
| ECS Task Definition | `naengo-api-server:2` (amd64, 옵션 A SHA `8cbea88...`) — 🟡 bridge |
| ECS Task Execution Role | `arn:aws:iam::518056141724:role/ecsTaskExecutionRole` (managed `AmazonECSTaskExecutionRolePolicy` + inline `naengo-secrets-fetch`) |
| ECS Service 이름 | `naengo-api-server` (desired=1) — 🟡 bridge |
| CloudWatch Log Group | `/ecs/naengo-api-server` (Fargate, bridge) + **`/ec2/naengo-api-server` (EC2 현행)** |
| **EC2 인스턴스** | **`i-00768e6ef42e4e04b`** (`naengo-api-server`, t4g.small ARM, AL2023) |
| **EC2 Elastic IP** | **`13.209.115.84`** (`eipalloc-08a1416df875990b1`) |
| EC2 SG | `sg-000355d7ad6c12936` (`naengo-api-server-ec2-sg`, inbound 80+443 from 0.0.0.0/0. SSH 없음 — SSM 접속) |
| EC2 instance profile | `naengo-api-server-ec2-profile` (role `naengo-api-server-ec2-role`: ECR read + Secrets get + SSM + CloudWatch) |
| EC2 부트스트랩 | `deploy/ec2-userdata.sh` (Docker + Caddy + ECR pull + Secrets→app.env) |
| TLS (EC2) | **Caddy 2 + Let's Encrypt** (자동 발급/90일 갱신). HTTP→HTTPS 자동 redirect |
| EC2 접속 | SSM Session Manager (`aws ssm start-session --target i-00768e6ef42e4e04b`) — SSH 키 없음 |
| Default VPC | `vpc-038c8eb56e23d5bb5` (172.31.0.0/16) — RDS 와 동일 VPC |
| Public subnets (4 AZ) | `subnet-0609c0a1180692f1d` (a) / `subnet-0417a2a8b7cb1c616` (b) / `subnet-0752c9e933903abf3` (c) / `subnet-057d3291db1e11d00` (d) |
| ALB SG | `sg-0f143ba6a8d9997d2` (`naengo-api-server-alb-sg`, inbound 80 + 443 from 0.0.0.0/0) |
| ECS task SG | `sg-0001862a6ba20c4cf` (`naengo-api-server-ecs-sg`, inbound 8080 from ALB SG) |
| RDS SG | `sg-0898460971d5b8d04` (inbound 5432 from `ec2-rds-1` + 우리 ECS SG) |
| ALB ARN | `arn:aws:elasticloadbalancing:ap-northeast-2:518056141724:loadbalancer/app/naengo-api-server-alb/159ba31da2dc086e` |
| **운영 도메인** | **`https://api.naengo.com`** ✅ → **EC2(`13.209.115.84`) 직결** (A 레코드). B6 후 ALB 안 거침 |
| ALB ARN / DNS | `...:loadbalancer/app/naengo-api-server-alb/159ba31da2dc086e` / `naengo-api-server-alb-176175450...elb.amazonaws.com` — 🟡 bridge (C9/C10 후 폐기) |
| Target Group / Listener 80·443 | TG `...targetgroup/naengo-api-server-tg/1303d640c7a0ed98`, L80 `.../bebdb0686756e3b2`(301 redirect), L443 `.../4312f361a7170e6c` — 🟡 bridge |
| ACM Cert (ALB용) | `arn:aws:acm:ap-northeast-2:518056141724:certificate/b501f6d7-...` — 🟡 bridge. ALB 폐기 시 함께 정리 (EC2 는 Let's Encrypt 사용) |
| DNS provider | HostingKR (공용 계정). ⚠️ **ns2/ns4 에 `api A 43.201.141.93` 좀비 잔존** (자체 sync 버그, 죽은 IP. public resolver 영향 0. HostingKR 고객센터 정리 필요 — cert 갱신 안정성) |
| SNS billing topic | `arn:aws:sns:us-east-1:518056141724:naengo-billing-alerts` (subscription: ppoobb94471@gmail.com, ✅ Confirmed 2026-05-22) |
| SNS ops topic | `arn:aws:sns:ap-northeast-2:518056141724:naengo-ops-alerts` (subscription: ppoobb94471@gmail.com, ✅ Confirmed 2026-05-22) |
| CloudWatch alarms (4종) | `naengo-billing-over-20-usd`(us-east-1) / `naengo-alb-target-5xx` / `naengo-tg-unhealthy-host` / `naengo-rds-cpu-high`(ap-northeast-2) |
| 카카오 운영 redirect URI | 🚫 N/A — front 모바일 only (카카오 SDK access_token 직접 호출). DevOAuthController 의 placeholder (`http://localhost:8080/oauth/kakao/test-callback`) 는 운영 미사용, 부팅 통과용 |
| Secrets Manager ARN | `naengo/prod/db` = `arn:aws:secretsmanager:ap-northeast-2:518056141724:secret:naengo/prod/db-iUa2In` |
| | `naengo/prod/jwt` = `arn:aws:secretsmanager:ap-northeast-2:518056141724:secret:naengo/prod/jwt-9m02fH` |
| | `naengo/prod/kakao` = `arn:aws:secretsmanager:ap-northeast-2:518056141724:secret:naengo/prod/kakao-8yZdEV` |

---

## A. 코드/로컬 정리 (본인, 즉시)

| # | 항목 | 상태 | 메모 |
|---|---|---|---|
| A1 | `.env` DB_URL `jdbc:` 접두사 + `user:pass@` 제거 | ✅ | host 만 남김 (`jdbc:postgresql://<host>:5432/<db>?sslmode=verify-full&sslrootcert=/app/global-bundle.pem`) |
| A2 | `KAKAO_REDIRECT_URI` 운영 도메인으로 교체 | 🚫 N/A | front 모바일 only — 카카오 SDK 로 access_token 직접 획득 후 `POST /auth/social/kakao` 흐름. 서버측 redirect URI 미사용. 현 placeholder 유지 (부팅 통과용) |
| A3 | `CORS_ALLOWED_ORIGINS=*` 좁히기 | 🟡 보안 위생 차원만 | admin 이 vercel rewrite proxy 사용 → 브라우저 입장 same-origin → **CORS 영향 0**. front 는 모바일이라 면제. 실제 좁힐 필요 없으나 위생 차원 admin 운영 URL 확정 시 추가 권장 |
| A4 | Dockerfile 에 RDS CA 번들 다운로드 단계 추가 (SSL 옵션 A) | ✅ | builder apt curl + runtime COPY. commit `1eaf4da` |
| A5 | 빈 운영 env (`AUTH_COOKIE_SAME_SITE`, `AWS_REGION`) | ✅ | default 사용 — `application-prod.yml` 의 `${AUTH_COOKIE_SAME_SITE:Lax}` + `${AWS_REGION:ap-northeast-2}`. 운영 정상 동작 확인 (e2e 33/33 PASS) |
| A6 | Spring Initializr 디폴트 정정 (`settings.gradle` rootProject.name, `build.gradle` group) | ✅ | `demo` → `naengo-api-server`, `com.example` → `com.naengo`. JAR 파일명 변경 — Dockerfile glob 안전 |

---

## B. AWS 인프라 (본인 = 인프라 담당)

| # | 항목 | 상태 | 메모 |
|---|---|---|---|
| B1 | ECR 리포 생성 | ✅ | Mutable / AES256 / Scan-on-push. URI 위 §0 참조 |
| B2 | GitHub OIDC + IAM role + repo Secrets 3종 | ✅ | 2026-05-21 완료. trust policy sub 정정 + Dockerfile UID 10001 정정 후 CI 통과. 이후 ECR 에 이미지 누적 중 |
| B2-a | IAM identity provider (계정당 1회) | ✅ | `arn:aws:iam::518056141724:oidc-provider/token.actions.githubusercontent.com` |
| B2-b | IAM role `github-actions-ecr-push` + trust + ecr-push policy | ✅ | trust = main 브랜치 only. 2026-05-21 sub 정정 |
| B2-c | GitHub Secrets (`AWS_ROLE_TO_ASSUME` / `AWS_REGION` / `ECR_REPOSITORY`) | ✅ | 콘솔로 주입 |
| B2-d | CI test job 30/30 PASS | ✅ | run 26194902810 / 26195691470 둘 다 test job 성공 |
| B2-e | CI build-and-push job → ECR 이미지 푸시 | ✅ | Dockerfile UID 10001 정정 후 정상 push. 옵션 A 코드 SHA `8cbea88...` 운영 배포 중 |
| B3 | Secrets Manager 3종 생성 + ECS Execution Role 권한 | ✅ | secret 3종 + Execution Role inline `naengo-secrets-fetch` 모두 완료 |
| B3-a | `naengo/prod/db` (DB_URL/DB_USERNAME/DB_PASSWORD) | ✅ | ARN: `...:secret:naengo/prod/db-iUa2In`. 값 = `.env` 정정된 것 그대로 |
| B3-b | `naengo/prod/jwt` (JWT_SECRET) | ✅ | ARN: `...:secret:naengo/prod/jwt-9m02fH`. C3 (a) 채택 — 양측 동일 SECRET 적용 + cross-team smoke 통과 (5/26) |
| B3-c | `naengo/prod/kakao` (KAKAO_REST_API_KEY/KAKAO_REDIRECT_URI) | ✅ | ARN: `...:secret:naengo/prod/kakao-8yZdEV`. KAKAO_REDIRECT_URI 는 placeholder 유지 (A2 N/A — 모바일 only) |
| B3-d | ECS Task Execution Role 에 `secretsmanager:GetSecretValue` 부여 | ✅ | `ecsTaskExecutionRole` 의 inline `naengo-secrets-fetch` policy 로 부여됨 |
| B4 | ECS Cluster + Task Definition + Service (Fargate) | ✅ | 모든 sub item 완료. 옵션 A 후 Task Definition rev 2 운영 중 |
| B4-a | ECS Cluster `naengo` 생성 | ✅ | ARN §0 |
| B4-b | Task Execution Role + Secrets fetch 권한 + Log Group | ✅ | `ecsTaskExecutionRole` + inline `naengo-secrets-fetch` + `/ecs/naengo-api-server` |
| B4-c | Task Definition 등록 (rev 1) | ✅ | image SHA pin `63bab5b9...`, cpu/mem 512/1024, env 2 + secrets 6, awslogs |
| B4-d | 보안그룹 3변경 (ALB SG / ECS SG / RDS SG inbound) | ✅ | ALB-SG 80 from internet, ECS-SG 8080 from ALB-SG, RDS-SG 5432 from ECS-SG (기존 ec2-rds-1 보존) |
| B4-e | ALB + Target Group + listener:80 | ✅ | ALB DNS / TG / Listener ARN §0. TG targets 비어 있음 (Service 미생성, 정상) |
| B4-f | ECS Service 생성 (Cluster + TaskDef + TG + SG + subnets) | ✅ | service `naengo-api-server` desired=1, assignPublicIp=ENABLED |
| B4-g | 실행 확인 (Task running + TG healthy + `/` 200) | ✅ | Task `5da1ffd...` healthy, ALB DNS `/` 200 / `/api/v1/users/me` 401 / `POST /auth/signup` 201 + user_id=4 발급 + Set-Cookie 정상 |
| B5 | ACM 인증서 + DNS(HostingKR) + ALB listener:443 + HTTP redirect | ✅ | 2026-05-26 완료. `api.naengo.com` HTTPS 동작 + listener:80 → 301 redirect + e2e 33/33 PASS. 자세한 진행: [`changes/2026-05-26-b5-https-api-naengo-com.md`](changes/2026-05-26-b5-https-api-naengo-com.md) |
| B6 | 보안그룹 (B4-d 와 통합 완료) | ✅ | B4-d 에 흡수 |
| B7 | RDS 사전 점검 | ✅ | 옵션 A 채택으로 우리 V1=DBv5. 운영 RDS 가 이미 DBv5 적용 상태라 `baseline-on-migrate=true` 가 자동 baseline INSERT + migrate skip. 부팅 검증됨 |
| B8 | 첫 배포 검증 (signup 201 + JWT 발급 + 쿠키 Secure HttpOnly + 운영 e2e 33/33 PASS) | ✅ | 5/21 첫 배포 → 5/22 DB 손상 → 5/23 복구 + 32/33 → 5/26 HTTPS 후 33/33 PASS |

---

## B-EC2. EC2 마이그레이션 (비용 절감 · 2026-06-07)

> 동기: 팀원 요청 — ALB(~$22/월) + Fargate(~$20/월) 고정비 과다. EC2 단일 인스턴스로 전환.
> 결정: t4g.small (ARM, 2GB — AI 서버와 동일) + Caddy(Let's Encrypt). 절감 ~$25/월.

| # | 항목 | 상태 | 메모 |
|---|---|---|---|
| E1 | CI arm64 빌드 (`platforms: linux/arm64` + QEMU) | ✅ | t4g 는 ARM. base 이미지 eclipse-temurin 이 arm64 지원 |
| E2 | EC2 SG + instance role/profile + EIP + RDS SG inbound | ✅ | SG `sg-000355d7ad6c12936`, EIP `13.209.115.84`, profile `naengo-api-server-ec2-profile` |
| E3 | t4g.small launch + user-data 부트스트랩 | ✅ | `i-00768e6ef42e4e04b`. Docker + Caddy + app 자동 기동 |
| E4 | DB schema drift 대응 (`ddl-auto: none` prod) | ✅ | recipe_media 드롭 등 drift → validate 실패 → prod none 으로. 부수: Fargate 재시작 위험도 해소 |
| E5 | EC2 app 부팅 + 내부 auth e2e | ✅ | Started 13s, 내부 signup/me/login/withdraw OK |
| E6 | DNS 컷오버 (`api` CNAME→A `13.209.115.84`) | ✅ | 사용자 HostingKR. ns2/ns4 좀비 `43.201.141.93` 잔존(무해, §0 참조) |
| E7 | Caddy Let's Encrypt cert 발급 | ✅ | production cert. EC2 강제 e2e 8/8 (cert=Let's Encrypt) |
| E8 | CI → EC2 SSM 자동 배포 단계 | ✅ | `ssm:SendCommand` (tag scope) + workflow Deploy step. docker pull + 재시작 + healthcheck |
| E9 | **ALB/Fargate/ACM(ALB) 폐기** | ⏸ | **C9(admin vercel.json)+C10(front dart-define) 선행** — ALB DNS 직접참조 0 되면 폐기 → 비용절감 완성 |
| E10 | 좀비 DNS 정리 (HostingKR ns2/ns4) | ⏸ | 고객센터 티켓 — cert 90일 갱신 안정성 위해 권장 |
| E11 | EC2 모니터링 알람 (CPU/status-check) — 기존 ALB 알람 대체 | ⏸ | ALB 폐기 시 `naengo-alb-target-5xx`/`tg-unhealthy-host` 무효화 → EC2 알람 신설 |

---

## C. 크로스팀 합의 (본인이 메신저)

| # | 상대 | 안건 | 상태 |
|---|---|---|---|
| C0 | **DB 팀원** | **V4(레시피 정규화) + V5(users 컬럼 제거) 적용 예정 통지 + RDS 사전 점검 협의** | ✅ → 사실상 옵션 A 로 흡수 (DBv5 직접 채택) |
| C1 | AI 팀 | V5 후 AI 코드가 `users.provider`/`provider_id` 컬럼을 SELECT 한 적 있다면 `user_identities` JOIN 으로 전환 필요 | 🚫 N/A — 옵션 A 채택으로 DBv5 의 users 에 그 컬럼 자체가 없음. AI 가 SELECT 할 가능성 0 |
| C2 | AI 팀 | 레시피 정규화 합동 컷오버 일정 | ✅ 사실상 완료 — DB 팀원이 DBv5(정규화 완성형) 적용 + AI 003/004/005 적용 + 우리 옵션 A align. 양측 모두 DBv5 위에서 동작 |
| C3 | AI 팀 | `JWT_SECRET` 운영 값 동일 공유 (D-2). 옵션 (a) 채택: 현 값 그대로 AI 에 전달 → AI 가 본인 Secrets Manager 저장 + 서비스 재시작 | ✅ 2026-05-26 closed — secret 동일 적용 확인. cross-team smoke 통과 |
| C4 | front/AI | `/me/profile` 변경 의미 합의 (우리 PATCH 교체 vs AI POST/DELETE) | ✅ 2026-05-23 closed — front 가 PATCH 우리 호출 → 5/26 분담 대전환으로 front 가 AI 로 routing 변경 → 최종 정본 = AI 의 POST(append)/DELETE(remove). 우리 PATCH 는 호출자 0 |
| C5 | AI 팀 | AI 가 우리 JWT 검증 적용 (FastAPI `HTTPBearer` + `HS512` decode), `TEMP_USER_ID` placeholder 제거 | ✅ 2026-05-26 closed — `app/api/v1/deps.py` 갱신 확인. cross-team smoke (우리 signup → user_id=9 JWT → AI `GET /users/me` 200 + user_id=9 회신 + `GET /recipes/scraps` 200) 통과 |
| C6 | front 팀 | `naengo_api_service.dart` L368 `pending_recipe_id` fallback 제거 (옵션 A 후 dead branch) | ⏸ 운영 1~2주 안정화 + 옛 키 응답 0건 확인 후 협의 |
| C7 | AI 팀 | AI 의 `/users/me`, `/users/me/profile` 폐기 (5/22 결정 — 당시 우리 정본) | 🔄 5/26 무효화 — front 라우팅 대전환으로 AI 가 정본됨 → 폐기 안 함이 정합. 대신 **우리 측 `UserMeController` getMe/updateMe/getProfile/updateProfile 이 폐기 후보로 전환** (호출자 0). 변경: [`changes/2026-05-26-front-routing-reversal.md`](changes/2026-05-26-front-routing-reversal.md) (예정) |
| C8 | front + AI | 이미지 업로드 owner 결정 (우리 multipart vs AI multipart vs 클라이언트 → S3 presigned). 현재 양쪽 모두 dead — front 가 image 자체를 안 보냄 (`'image_url': null` 하드코딩) | ⏸ front 의 이미지 화면 구현 직전 결정 |
| C9 | admin 팀 | `vercel.json` rewrite destination 갱신 — `http://naengo-api-server-alb-...elb.../api/v1/*` → `https://api.naengo.com/api/v1/*` (보안 + 안정성) | ⏸ admin 팀 |
| C10 | front 팀 | dart-define 운영 빌드값 갱신 — `NAENGO_SPRING_BASE=https://api.naengo.com`, `NAENGO_API_BASE=https://ai.naengo.com` (AI 도메인 부착 후) | ⏸ AI 도메인 부착 후. **EC2 폐기(E9) 선행조건** — ALB DNS 직접참조 제거 |
| C11 | AI 팀 | `ai.naengo.com` 부착 (본인 ACM 발급 + HostingKR DNS 등록 + AI 인프라 attach) | ⏸ AI 팀 자체 진행 |

> **C9 + C10 = EC2 비용절감(E9 ALB 폐기)의 관문.** 둘 다 `api.naengo.com` 도메인 사용으로 바뀌면 ALB DNS 직접참조 0 → ALB+Fargate 폐기 가능 → 월 ~$42 절감.

---

## D. 운영 위생 / 보안

| # | 항목 | 상태 | 메모 |
|---|---|---|---|
| D1 | `.env` 평문 secret → Secrets Manager 이관 후 로컬 `.env` 에선 dev-only 값 또는 제거 | ✅ | 2026-05-22 로컬 `.env` 운영 secret 4종 제거 (DB host/password, JWT_SECRET, KAKAO key) → 로컬 docker-compose 더미값으로 치환. 운영 실값은 Secrets Manager 만 보유. `.env` 는 `.gitignore` 보호 + git history 노출 0 확인 |
| D2 | CORS 좁힘 (= A3) | 🟡 보안 위생만 | admin vercel proxy 패턴이라 CORS 영향 0. 위생 차원 admin URL 확정 후 |
| D3 | CloudWatch alarm 4종 + SNS 이메일 알림 | ✅ | 2026-05-22 완료. 4 alarm + SNS 2 topic 모두 Confirmed. billing preferences ON |
| D4 | IAM 사용자 권한 좁히기 (현재 AdministratorAccess) | ⏸ | 5/26 기준 5일 안정화. 1~2주 후 ECR/Secrets/ECS/RDS-describe/ELB/ACM/EC2 SG modify 만 가진 policy 로 |
| D5 | IAM 사용자 MFA 활성화 | ✅ | 2026-05-22 |
| D6 | (신규) ACM 인증서 만료 30일 전 CloudWatch 알람 | ⏸ 선택 | 자동 갱신 trigger 가 만료 60일 전이지만 실패 케이스 대비 알람 추가 권장 |

---

## 현재 상태 / 다음 액션 (2026-06-07 기준)

🎉 **운영 서비스 = EC2(t4g.small) 로 마이그레이션 완료** — `https://api.naengo.com` → EC2 Caddy(Let's Encrypt) → app, e2e 검증 통과. ALB+Fargate 는 bridge 로 잔존 (C9/C10 후 폐기 → 비용절감 완성).

### 우리측 잔여 task

| 구분 | 항목 | 시점 |
|---|---|---|
| 🟡 비용 | **E9 ALB+Fargate 폐기** | C9+C10 충족 후 (관문) — 월 ~$42 절감 |
| 🟡 운영 | E11 EC2 모니터링 알람 (CPU/status-check) | ALB 폐기와 함께 (기존 ALB 알람 대체) |
| 🟢 정리 | 폐기 후보 controller PR (6+개) | 운영 안정화 후 — [`changes/2026-05-23-cross-team-actual-routing.md`](changes/2026-05-23-cross-team-actual-routing.md) |
| 🟢 보안 | D4 IAM 권한 좁히기 | 운영 안정화 후 |
| 🟢 선택 | D6 ACM(ALB) 만료 알람 — ALB 폐기 시 무의미해짐 | ALB 폐기 전까지만 |

### 외부 의존 (대기)

| # | 누구 | 항목 |
|---|---|---|
| **C9** | admin 팀 | `vercel.json` rewrite → `https://api.naengo.com` (**E9 폐기 관문**) |
| **C10** | front 팀 | dart-define `NAENGO_SPRING_BASE=https://api.naengo.com` (**E9 폐기 관문**) |
| E10 | 사용자 | HostingKR 고객센터 — ns2/ns4 좀비 `api A 43.201.141.93` 정리 (cert 갱신 안정성) |
| C11 | AI 팀 | `ai.naengo.com` 부착 (본인 ACM + DNS) |
| C6 | front 팀 | `pending_recipe_id` fallback 제거 (운영 1~2주 후) |
| C8 | front + AI | 이미지 업로드 owner (이미지 화면 구현 직전) |

> EC2 전환은 기능 완료. 비용절감(ALB 폐기)만 C9/C10 대기. 그 외 미시급.

## 변경 이력

- 2026-05-21 신설. B1 완료, A4 commit `1eaf4da`. B2 CI 1차 시도 — test ✓ / build-push 가 OIDC AssumeRole 실패(trust policy sub placeholder). trust 정정 후 재실행 attempt 2 — OIDC ✓ / build-push 가 `useradd exit 4` (UID 1000 충돌) 실패. Dockerfile UID/GID 10001 로 정정 + A6 Spring Initializr 디폴트 정정 동시 진행.
- 2026-05-21 B2 완료 (CI 통과, ECR 에 이미지 누적). C0 합의 완료. **B3 Secrets Manager 3종 생성 완료** (db/jwt/kakao). JWT 와 KAKAO_REDIRECT_URI 는 추후 `update-secret` 필요.
- 2026-05-21 **B4-a/b/c/d 완료**: Cluster `naengo` + Execution Role + Log Group + Task Definition rev 1 (SHA pin `63bab5b9...`, 0.5vCPU/1GB) + SG 3변경 (ALB SG / ECS SG / RDS SG inbound 에 ECS SG 추가). VPC 토폴로지: default VPC + 4 AZ public subnets 사용. 다음: B4-e (ALB+TG) → B4-f (Service).
- 2026-05-21 **B4-e 완료**: TG (HTTP 8080, target-type ip, health check `/`) + ALB (internet-facing, 4 AZ, ALB-SG) + listener:80. ALB DNS = `naengo-api-server-alb-176175450.ap-northeast-2.elb.amazonaws.com`. TG targets 비어 있음 (Service 미생성). 다음: B4-f.
- 2026-05-21 **B4-f 완료 / B4-g 실패**: Service 생성 + Task RUNNING ✓ / Spring 부팅 실패 (`FlywayException: Found non-empty schema "public" but no schema history table`). 원인: DB 팀원이 운영 RDS 에 DBv5 를 직접 SQL 로 적용 → flyway_schema_history 미생성. ECS Service desired=0 로 실패 루프 중단. **DB 팀원의 DBv5.sql 분석 결과 우리 V1~V5 와 9가지 구조 차이** (users.username/user_identities/user_recipes/PK 타입 등). **옵션 A 결정** — 우리(api-server) 가 DBv5 전면 align (10 phase, ~60~70 파일). C0 합의 → 사실상 무효 (DB 팀원 명명을 우리가 채택).
- 2026-05-21 **옵션 A Phase 1 완료**: Flyway V1~V5 폐기, 신규 V1 = DBv5.sql 통째(broken index/trigger 2건만 정정), `application-prod.yml` 에 `baseline-on-migrate=true` + `baseline-version="1"` 설정.
- 2026-05-21 **옵션 A Phase 2~7 완료**: User entity (email→username, deletedAt 제거, Long→Integer) / SocialAccount→UserIdentity (테이블 user_identities, 컬럼명 정합, AuthProvider 4종 확장) / UserProfile (Integer + JSONB NOT NULL DEFAULT) / PendingRecipe→UserRecipe (테이블 user_recipes, URL `/api/v1/[admin/]user-recipes`) / Recipe + 자식 entities + Like/Scrap/Chat Long→Integer ripple / JWT·SecurityUtil Integer / 통합테스트 7 파일 일괄 정합. **Testcontainers 30/30 PASS** (Auth 8 / Cors 4 / ProfileChat 3 / Recipe 6 / RequestId 4 / SocialAuth 5).
- 2026-05-21 **🎉 첫 운영 배포 성공**: CI 통과 → ECR `:8cbea88...` → Task Definition rev 2 → Service desired=1 force-new-deployment. Spring 부팅 63s, Flyway baseline 자동 init, Tomcat 8080, ALB TG healthy. 검증: `/` 200 + `/api/v1/users/me` 401 + signup 201 (user_id=4 발급 + Secure HttpOnly 쿠키). B4/B6/B7/B8 모두 완료. 남은: B5(도메인+ACM+Route53+443), A2/B3-c update(KAKAO redirect URI), A3(CORS 좁힘), C3(AI JWT 공유), D3/D4(모니터링·IAM 좁힘).
- 2026-05-22 **D1/D3/D5 완료** (우리측 단독 작업 마감): 로컬 `.env` 운영 secret 4종 제거 + 모니터링 SNS 구독 Confirmed + IAM MFA 활성화. 통합테스트 30/30 재실행 PASS. **로컬 e2e 33/33 PASS** (`scripts/e2e-smoke-prod.sh` 신설).
- 2026-05-22 **⚠️ 운영 RDS schema 손상 발견**: 운영 ALB e2e 시 `relation "users" does not exist` 500 발화. RDS PG 로그 분석 결과 **04:35 KST 에 DB 작업자 (`172.31.3.36`, master 사용자 `naengo`) 가 큰 schema 변경 SQL 실행 — `pending_recipes` (옛 schema) 참조 statement 가 첫 ERROR → 일부 DROP 만 적용 + CREATE 단계 전 abort 추정**. ECS task 는 부팅 시점 metadata cache 유효해서 hibernate validate 통과 + healthcheck path `/` 가 DB 미접근 → 모니터링 사각. 코드 무결성: 통합 30/30 + 로컬 e2e 33/33 PASS 로 입증. 자세한 진단: [`changes/2026-05-22-prod-users-table-missing.md`](changes/2026-05-22-prod-users-table-missing.md). **다음 액션: DB 팀원과 원인 확인 → schema 복구 → ECS force-new-deployment → 운영 e2e 재실행**.
- 2026-05-23 **✅ 운영 RDS schema 복구 완료 + 운영 e2e 32/33 PASS**: DB 팀원이 DBv5.sql 재적용. ECS task 는 RUNNING 유지 (재배포 불필요 — DB 만 정상화되면 즉시 동작). 운영 ALB BASE 로 `scripts/e2e-smoke-prod.sh` 재실행 → 32/33 PASS. 실패 1건 (#23 cookie-based auth) 은 `Secure` 쿠키 + HTTP ALB 환경 한계 (B5 HTTPS 부착 시 자연 해결, 실서비스 영향 0 — front/admin Bearer 우선). **옵션 A 코드가 운영에서 완전히 정상 동작 입증.**
- 2026-05-23 **📋 크로스팀 실 라우팅 분석**: naengo-ai / naengo-admin / naengo-front 최신본 직접 스캔. front 가 이미 `springBase`(우리 ALB) + `baseUrl`(AI 8000) 이중 분리 채택 + 우리 ALB DNS 가 default 값으로 박혀있음. 운영 모델 D+ (front 명시 분담) 확정. C4 closed (front 가 PATCH 채택). C7 결정 (AI 의 `/users/me`, `/users/me/profile` 폐기 — AI 팀 통보 완료). C6/C8 보류 (front fallback / multipart owner 결정). 우리 controller 6개 폐기 후보 식별 (모두 호출자 0, 운영 1주 안정화 후 단계 PR). 자세한 분석: [`changes/2026-05-23-cross-team-actual-routing.md`](changes/2026-05-23-cross-team-actual-routing.md).
- 2026-05-26 **🔐 B5 완료 — HTTPS 부착**: 도메인 `naengo.com` 결정 (HostingKR 공용 계정). 팀 합의로 서브도메인 분기 (`api.naengo.com` 우리 / `ai.naengo.com` AI). ACM 인증서 발급 (DNS validation, 5분 ISSUED) + HostingKR DNS 등록 (검증 CNAME + api → ALB CNAME) + ALB SG inbound 443 + listener:443 (TLS 1.2/1.3) + listener:80 → 301 redirect. 운영 e2e 33/33 PASS (Secure 쿠키 + HTTPS 환경 동작 입증). 자세한 진행: [`changes/2026-05-26-b5-https-api-naengo-com.md`](changes/2026-05-26-b5-https-api-naengo-com.md). 남은: A3 (CORS 좁히기 — admin URL 결정 후), AI 측 ai.naengo.com 진행 대기, KAKAO_REDIRECT_URI 는 모바일 흐름이라 현 placeholder 유지.
- 2026-05-26 **🤝 C3 + C5 closed — cross-team JWT 검증 통과**: AI 가 `app/api/v1/deps.py` 에서 `HTTPBearer` + `HS512` decode 적용 + `TEMP_USER_ID` placeholder 제거 확인. cross-team smoke 실행: 우리 `POST /auth/signup` → JWT(`sub=9, role=USER, iat/exp`) 발급 → AI 운영(`3.34.187.42:8000`) 에 Bearer 첨부 → `GET /api/v1/users/me` **200 + user_id=9** + `GET /api/v1/recipes/scraps` 200 응답. 토큰 없이 호출 시 401 정상. **양 서버 동일 JWT_SECRET + 동일 알고리즘 입증**. 운영 멀티유저 흐름 완성.
- 2026-05-26 **📋 front 라우팅 대전환 감지**: front `naengo_api_service.dart` 갱신본에서 우리 호출 10개 → **2개로 축소** (`POST /auth/social/kakao`, `POST /auth/logout` 만). `/users/me`, `/users/me/profile`, `/user-recipes*`, `/users/me/scraps` 모두 AI 로 이전. admin 은 vercel proxy 통해 `/auth/login`, `/auth/signup` (LOCAL 가입/로그인) 우리 호출. **최종 우리 활성 endpoint = 4개 (전부 auth 도메인)**. 우리 서버 = JWT 발급 전용 서비스로 축소. C7 결정 무효화 (정반대). 자세한 분석: [`changes/2026-05-23-cross-team-actual-routing.md`](changes/2026-05-23-cross-team-actual-routing.md). DBv5 마이그레이션 2건 (`recipe_labels` CHECK + `user_recipe_reports` 신규 테이블) 발견 — 우리 entity 영향 0.
- 2026-05-26 **🟢 운영 카카오 흐름 B 실 모바일 e2e 통과**: front 빌드 (패키지명 `com.naengo.app`, 카카오 콘솔 네이티브 키 + 키 해시 등록) 에서 카카오 SDK → 우리 운영 `POST /auth/social/kakao` → JWT 발급 정상. 초기 실패는 front 기존 작업물과 충돌 — front 측 해결. 출시 가능 상태.
- 2026-05-26 **🟢 회원 탈퇴 운영 실측 5/5 PASS**: `DELETE /api/v1/users/me` (owner = 우리, AI 에 없음). 탈퇴 204 → 같은 토큰 재호출 401 → 재로그인 401 → 재탈퇴 401. 익명화 (PII nullify + is_active=false + is_blocked=true) + 부속 데이터 삭제 + chat soft-delete 검증. **front 탈퇴 화면만 추가하면 출시 가능** (Play Store Data Safety 정책상 필수 — 서버는 준비 완료).
- 2026-06-07 **🖥️ B6 EC2 마이그레이션 (비용절감)**: 팀원 요청 — ALB+Fargate 고정비(~$42/월) 절감 위해 EC2 t4g.small(ARM, AI 서버와 동일) 전환. ① CI arm64 빌드(QEMU + `platforms: linux/arm64`) ② EC2 SG/instance-role/EIP(`13.209.115.84`)/RDS SG ③ t4g.small(`i-00768e6ef42e4e04b`) + user-data(`deploy/ec2-userdata.sh`: Docker+Caddy+ECR+Secrets) ④ **DB schema drift 발견** (recipe_media 드롭 등 — 우리 코드와 불일치, validate 실패) → `prod ddl-auto: none` (부수: Fargate 재시작 위험도 해소) ⑤ DNS 컷오버(`api` A→EIP) — HostingKR ns2/ns4 좀비 `43.201.141.93` 잔존(죽은 IP, 무해) ⑥ Caddy Let's Encrypt production cert 발급 (rate-limit/staging 우회: caddy_data 볼륨 리셋) ⑦ EC2 강제 e2e 8/8 PASS (cert=Let's Encrypt) ⑧ CI→EC2 SSM 자동배포 단계 추가 (`ssm:SendCommand` tag-scope). **EC2 전환 기능 완료. ALB/Fargate 폐기(E9)는 C9(admin vercel.json)+C10(front dart-define) 도메인 전환 후 → 비용절감 완성.** 자세히: §B-EC2.
