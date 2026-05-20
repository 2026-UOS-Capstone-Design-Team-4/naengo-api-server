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
| ECS cluster / service 이름 | (미정) |
| 운영 도메인 (`api.???`) | (미정) |
| 카카오 운영 redirect URI | (미정) — 운영 도메인 결정 후 |
| Secrets Manager ARN 들 | (미생성) |

---

## A. 코드/로컬 정리 (본인, 즉시)

| # | 항목 | 상태 | 메모 |
|---|---|---|---|
| A1 | `.env` DB_URL `jdbc:` 접두사 + `user:pass@` 제거 | ✅ | host 만 남김 (`jdbc:postgresql://<host>:5432/<db>?sslmode=verify-full&sslrootcert=/app/global-bundle.pem`) |
| A2 | `KAKAO_REDIRECT_URI` 운영 도메인으로 교체 | ⏸ | 운영 도메인 결정 후. 카카오 콘솔 등록값과 1글자 일치 필수 |
| A3 | `CORS_ALLOWED_ORIGINS=*` 좁히기 | ⏸ | front/admin 운영 도메인 확정 후. 현재 임시 wildcard |
| A4 | Dockerfile 에 RDS CA 번들 다운로드 단계 추가 (SSL 옵션 A) | 🟡 | 진행 중 — builder apt curl + runtime COPY |
| A5 | 빈 운영 env 채우기 (`AUTH_COOKIE_SAME_SITE`, `AWS_REGION`) | ⏸ | Secrets Manager 이관 단계와 함께 |

---

## B. AWS 인프라 (본인 = 인프라 담당)

| # | 항목 | 상태 | 메모 |
|---|---|---|---|
| B1 | ECR 리포 생성 | ✅ | Mutable / AES256 / Scan-on-push. URI 위 §0 참조 |
| B2 | GitHub OIDC + IAM role + repo Secrets 3종 | 🟡 | CLI 명령 실행 완료. **1차 CI 실행 결과 확인 중** (https://github.com/2026-UOS-Capstone-Design-Team-4/naengo-api-server/actions) |
| B2-a | IAM identity provider (계정당 1회) | ✅ | `arn:aws:iam::518056141724:oidc-provider/token.actions.githubusercontent.com` |
| B2-b | IAM role `github-actions-ecr-push` + trust + ecr-push policy | ✅ | trust = main 브랜치 only |
| B2-c | GitHub Secrets (`AWS_ROLE_TO_ASSUME` / `AWS_REGION` / `ECR_REPOSITORY`) | ✅ | 콘솔로 주입 |
| B3 | Secrets Manager 3종 생성 + ECS Execution Role 권한 | ⏸ | A4 Dockerfile 변경 + CI 정상 확인 후 |
| B3-a | `naengo/prod/db` (DB_URL/DB_USERNAME/DB_PASSWORD) | ⏸ | DB_URL 은 `.env` 의 정정된 값 그대로 |
| B3-b | `naengo/prod/jwt` (JWT_SECRET — AI 와 동일 값) | ⏸ | C3 합의 후 동일 값 |
| B3-c | `naengo/prod/kakao` (KAKAO_REST_API_KEY/KAKAO_REDIRECT_URI) | ⏸ | A2 결정 후 |
| B4 | ECS Cluster + Task Definition + Service (Fargate) | ⏸ | Task Definition JSON 예시: [`deploy-env.md §5.1-3`](deploy-env.md) |
| B5 | ALB + Target Group + ACM 인증서 + Route53 | ⏸ | 운영 도메인 결정 필요 |
| B6 | 보안그룹 4종 (인터넷→ALB / ALB→ECS / ECS→RDS / ECS→kakao) | ⏸ | B4/B5 와 병행 가능 |
| B7 | **RDS 사전 점검** — V1~V5 적용 가능 상태인지 확인 | ⏸ | **DB 운영 팀원에게 협의 필요** — 우리가 V4(레시피 정규화) + V5(social_accounts 분리) 적용 예정임을 알림. 다른 DDL 충돌 없는지 |
| B8 | 첫 배포 검증 (Flyway V1~V5 적용 / `/` 200 / signup 201 / 카카오 흐름 B) | ⏸ | [`deploy-env.md §4, §6`](deploy-env.md) |

---

## C. 크로스팀 합의 (본인이 메신저)

| # | 상대 | 안건 | 상태 |
|---|---|---|---|
| C0 | **DB 팀원** | **V4(레시피 정규화) + V5(users 컬럼 제거) 적용 예정 통지 + RDS 사전 점검 협의** | ⏸ |
| C1 | AI 팀 | V5 후 AI 코드가 `users.provider`/`provider_id` 컬럼을 SELECT 한 적 있다면 `social_accounts` JOIN 으로 전환 필요 | ⏸ |
| C2 | AI 팀 | 레시피 정규화(B) 합동 컷오버 일정 — 단독 배포 시 양 서버 장애 | ⏸ |
| C3 | AI 팀 | `JWT_SECRET` 운영 값 동일 공유 (D-2) | ⏸ |
| C4 | front/AI | `/me/profile` 변경 의미 합의 (PATCH 교체 vs POST/DELETE) | ⏸ |

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

1. **A4** Dockerfile 에 RDS CA 번들 추가 → commit + push (지금)
2. **B2 검증** — 직전 push 의 CI 결과 (test 30/30 + ECR push) GitHub Actions 탭에서 확인
3. **B7 / C0** — DB 운영 팀원과 V4/V5 적용 통지 + RDS 사전 점검 협의 (B3 이후 ECS 띄우기 전에 끝나야 함)
4. **B3** — A4 + CI 정상 + C0 합의 후 Secrets Manager 3종 생성
5. **B4~B8** — 그 후 순차

> 운영 도메인이 정해지지 않은 상태에서도 B3/B4 까진 placeholder 로 진행 가능 (KAKAO_REDIRECT_URI 만 임시값). B5(ALB+Route53)는 도메인 필요.

## 변경 이력

- 2026-05-21 신설. B1/B2 완료, A4 진행 중.
