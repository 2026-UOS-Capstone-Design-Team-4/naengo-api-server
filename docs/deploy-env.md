# 운영(prod) 환경변수 주입 체크리스트

> 대상: 배포/인프라 담당. `user-domain-todo.md §4-1`(운영 카카오 env) + `api-server-tasks.md §8-2`(secret 외부화) 실행 가이드.
> 전제: 코드/설정 변경 없음. `application-prod.yml` 은 placeholder 만 보유 — 값은 **배포 시 env 주입**.
> 템플릿: 루트 [`.env.example`](../.env.example) (커밋됨, secret 없음).

---

## 1. prod 프로파일 활성화

```
SPRING_PROFILES_ACTIVE=prod
```
(미설정 시 `local` 기본 — 운영 배포 시 반드시 prod)

## 2. 필수 env (미주입 시 **부팅 실패** = placeholder 미해결)

| env | 출처/형식 | 비고 |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://<rds-host>:5432/naengo` | AWS RDS(공유 DB). AI 팀과 동일 인스턴스 |
| `DB_USERNAME` / `DB_PASSWORD` | RDS 자격증명 | Secret Manager 권장 |
| `JWT_SECRET` | `openssl rand -base64 48` (≥32바이트) | **AI 서버와 동일 값 공유** (Phase 0-1 / D-2). 회전 시 양 서버 동시 배포 |
| `KAKAO_REST_API_KEY` | 카카오 콘솔 REST API 키 | `KakaoTokenClient` `@Value` 가 부팅 시 요구 → 없으면 기동 실패. 흐름 B(클라이언트 SDK) 자체는 미사용이나 부팅 전제 |
| `KAKAO_REDIRECT_URI` | 콘솔 등록값과 완전 일치 | 운영에서 dev 콜백 미사용이어도 부팅 전제로 필요(형식상 1개 등록) |
| `CORS_ALLOWED_ORIGINS` | `https://front…,https://admin…` 콤마 구분 | 미주입 시 부팅 실패. localhost 기본값이 prod 로 새지 않게 강제 |

## 3. 선택 env (default 존재 — 미주입 시 기본 동작)

| env | default | 영향 |
|---|---|---|
| `AUTH_COOKIE_SAME_SITE` | `Lax` | 크로스도메인 front 배포면 `None` (HTTPS 필수, prod 는 `secure=true` 고정) |
| `AUTH_COOKIE_DOMAIN` | 비움(호스트 한정) | 멀티 서브도메인이면 `.example.com` |
| `AWS_REGION` | `ap-northeast-2` | |
| `AWS_S3_BUCKET` / `AWS_S3_PUBLIC_URL_PREFIX` | 비움 | 비우면 업로드 endpoint 503(의도). S3 준비 후 주입 |

## 4. 부팅 검증 절차 (배포 후 1회)

1. 기동 로그: `Started ApiServerApplication` + `Flyway ... Successfully applied N migrations` (V1~V5 — V4 는 레시피 정규화, V5 는 users/social_accounts 분리, 모두 2026-05-19) 확인.
   - ⚠️ V4/V5 첫 적용 전 RDS 현 스키마 상태 점검 필수. 이미 다른 DDL(예: naengo-ai schema.sql / 변형된 users 컬럼) 이 적용돼 있으면 `DROP TABLE`/`DROP COLUMN` 가 충돌 — 사전 백업/조율 후 진행.
   - `Could not resolve placeholder 'XXX'` → §2 필수 env 누락. 주입 후 재기동.
   - `password authentication failed` → DB 자격증명/RDS 보안그룹 확인.
2. 헬스: `curl https://<운영도메인>/health` → `{"status":"UP"}`.
3. 인증 가드: `curl -i https://<운영도메인>/api/v1/users/me` → `401` + `{"error":{"code":"UNAUTHENTICATED"}}`.
4. 자체 회원가입 스모크: `POST /api/v1/auth/signup` → `201` + `Set-Cookie: NAENGO_AT=...; Secure; HttpOnly` + body `access_token`(snake_case).
   - 쿠키에 `Secure` 있는데 HTTP 로 호출하면 미적용처럼 보임 → **HTTPS 로 검증**.
5. 카카오 흐름 B: 클라이언트(SDK) 통합 후 `POST /api/v1/auth/social/kakao {"access_token":"<카카오 토큰>"}` → 200. (절차: [`kakao-oauth-runbook.md §4`](kakao-oauth-runbook.md))

## 5. 주입 방법 (택1)

- **컨테이너/ECS Fargate**: 태스크 정의의 `environment` (비밀 아님) + `secrets` (Secrets Manager / SSM 참조). → [§5.1 절차](#51-aws-ecs-fargate--secrets-manager-구체-절차)
- **systemd/EC2**: `EnvironmentFile=` 또는 `Environment=`.
- **수동 점검용**: `.env.example` 복사 → 값 채움 → `set -a; source .env; set +a` (LF 필수).

> secret 은 코드/이미지/로그에 남기지 말 것. `application-prod.yml` 은 placeholder 만 — 커밋 OK, 값은 런타임 주입.

### 5.1 AWS ECS Fargate + Secrets Manager 구체 절차

> 컴퓨트 = ECS Fargate, 이미지 레지스트리 = ECR, 비밀 = Secrets Manager, 외부 노출 = ALB + ACM + Route53 가정. 리전 `ap-northeast-2`.

**1) ECR 리포지토리 생성** (1회)
```bash
aws ecr create-repository --repository-name naengo-api-server --region ap-northeast-2
```
→ URI: `<ACCOUNT_ID>.dkr.ecr.ap-northeast-2.amazonaws.com/naengo-api-server`

**2) Secrets Manager 시크릿 생성** (env 그룹별 3개 권장)
```bash
# DB (USERNAME/PASSWORD 분리해 저장)
aws secretsmanager create-secret --name naengo/prod/db \
  --secret-string '{"DB_URL":"jdbc:postgresql://<rds-host>:5432/naengo_db","DB_USERNAME":"naengo","DB_PASSWORD":"<강력비밀>"}'
# JWT (AI 서버와 동일 값 — D-2 합의)
aws secretsmanager create-secret --name naengo/prod/jwt \
  --secret-string '{"JWT_SECRET":"<openssl rand -base64 48>"}'
# 카카오
aws secretsmanager create-secret --name naengo/prod/kakao \
  --secret-string '{"KAKAO_REST_API_KEY":"<운영 REST API key>","KAKAO_REDIRECT_URI":"https://api.naengo.kr/oauth/kakao/test-callback"}'
```
`secretsmanager:GetSecretValue` 권한 = **ECS Task Execution Role** 에 부여 (런타임 주입 시 ECS 가 fetch).

**3) ECS 태스크 정의 (요지)**
```jsonc
{
  "family": "naengo-api-server",
  "networkMode": "awsvpc",
  "requiresCompatibilities": ["FARGATE"],
  "cpu": "512", "memory": "1024",
  "executionRoleArn": "arn:aws:iam::<ACCOUNT_ID>:role/ecsTaskExecutionRole",
  "taskRoleArn":      "arn:aws:iam::<ACCOUNT_ID>:role/naengo-api-server-task",
  "containerDefinitions": [{
    "name": "api",
    "image": "<ACCOUNT_ID>.dkr.ecr.ap-northeast-2.amazonaws.com/naengo-api-server:<tag>",
    "portMappings": [{"containerPort": 8080, "protocol": "tcp"}],
    "environment": [
      {"name": "SPRING_PROFILES_ACTIVE", "value": "prod"},
      {"name": "CORS_ALLOWED_ORIGINS",   "value": "https://admin.naengo.kr,https://app.naengo.kr"},
      {"name": "AUTH_COOKIE_SAME_SITE",  "value": "Lax"},
      {"name": "AUTH_COOKIE_DOMAIN",     "value": ".naengo.kr"}
    ],
    "secrets": [
      {"name": "DB_URL",             "valueFrom": "arn:aws:secretsmanager:ap-northeast-2:<ACCOUNT_ID>:secret:naengo/prod/db:DB_URL::"},
      {"name": "DB_USERNAME",        "valueFrom": "arn:aws:secretsmanager:ap-northeast-2:<ACCOUNT_ID>:secret:naengo/prod/db:DB_USERNAME::"},
      {"name": "DB_PASSWORD",        "valueFrom": "arn:aws:secretsmanager:ap-northeast-2:<ACCOUNT_ID>:secret:naengo/prod/db:DB_PASSWORD::"},
      {"name": "JWT_SECRET",         "valueFrom": "arn:aws:secretsmanager:ap-northeast-2:<ACCOUNT_ID>:secret:naengo/prod/jwt:JWT_SECRET::"},
      {"name": "KAKAO_REST_API_KEY", "valueFrom": "arn:aws:secretsmanager:ap-northeast-2:<ACCOUNT_ID>:secret:naengo/prod/kakao:KAKAO_REST_API_KEY::"},
      {"name": "KAKAO_REDIRECT_URI", "valueFrom": "arn:aws:secretsmanager:ap-northeast-2:<ACCOUNT_ID>:secret:naengo/prod/kakao:KAKAO_REDIRECT_URI::"}
    ],
    "logConfiguration": {
      "logDriver": "awslogs",
      "options": {
        "awslogs-group": "/ecs/naengo-api-server",
        "awslogs-region": "ap-northeast-2",
        "awslogs-stream-prefix": "api"
      }
    }
  }]
}
```

**4) ALB target group + 헬스체크**
- Target type = `ip` (Fargate), port `8080`, protocol `HTTP`.
- **Health check path = `/`** (`HealthController` 가 `GET /` `GET /health` 둘 다 200). 정상 임계 2, 비정상 임계 3, interval 15s.
- ALB listener `:443` (ACM 인증서) → forward → target group. `:80` → `:443` redirect.

**5) 보안그룹 / 네트워크**
| from | to | port | 용도 |
|---|---|---|---|
| 인터넷 | ALB | 443 | HTTPS 외부 호출 |
| ALB | ECS task | 8080 | API |
| ECS task | RDS | 5432 | DB |
| ECS task | 0.0.0.0/0 | 443 | 카카오(`kauth/kapi.kakao.com`) outbound |

**6) Route53 + ACM**
- `api.naengo.kr` A 레코드(Alias) → ALB. ACM 인증서 발급(DNS validation).

**7) 부팅 검증** → §4 그대로 (HTTPS 로 호출, `/`/`/health` 200, signup 201 + `Set-Cookie ... Secure`).

### 5.2 GitHub Actions OIDC 권한 (`.github/workflows/build-and-push.yml` 용)

CI 가 long-lived AccessKey 없이 ECR push 하려면 OIDC IAM 역할 1회 생성:

**1) IAM identity provider** (1회, 계정당)
- Provider URL: `https://token.actions.githubusercontent.com`
- Audience: `sts.amazonaws.com`

**2) IAM role** `github-actions-ecr-push`
- Trust policy: GitHub OIDC, `sub` 제한 `repo:<org-or-user>/naengo-api-server:ref:refs/heads/main` (main 푸시만)
- Permissions: `AmazonEC2ContainerRegistryPowerUser` (또는 ECR push 최소권한)

**3) GitHub repo Secrets**
| 키 | 값 |
|---|---|
| `AWS_ROLE_TO_ASSUME` | `arn:aws:iam::<ACCOUNT_ID>:role/github-actions-ecr-push` |
| `AWS_REGION` | `ap-northeast-2` |
| `ECR_REPOSITORY` | `naengo-api-server` |

## 6. 완료 체크 (→ `user-domain-todo.md §4-1`)

- [ ] ECR 리포지토리 생성 + GitHub OIDC role 생성 + repo secrets 주입 (§5.2)
- [ ] Secrets Manager 3종(`naengo/prod/{db,jwt,kakao}`) 생성, ECS Execution Role 에 `GetSecretValue` 부여
- [ ] ECS Task Definition 등록(§5.1) + Service / ALB target group(`/` 헬스체크) / ACM / Route53
- [ ] RDS 사전 점검: V1~V4 적용 가능한 상태인지(다른 DDL 없음) 확인
- [ ] prod env(§2 필수 6종) 주입, `SPRING_PROFILES_ACTIVE=prod` 기동 성공
- [ ] `/` 또는 `/health` 200, 인증 가드 401(`UNAUTHENTICATED`), signup 201+Secure 쿠키
- [ ] `JWT_SECRET` AI 서버와 동일 값 공유 합의·반영 (D-2)
- [ ] 카카오 흐름 B 200 (클라이언트 SDK 통합 후)
