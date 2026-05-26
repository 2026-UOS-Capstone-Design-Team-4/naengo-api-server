# B5 — `api.naengo.com` HTTPS 부착 + ALB listener:443 + HTTP→HTTPS redirect

## 0. 메타

| 항목 | 값 |
|---|---|
| 변경 이력 ID | `2026-05-26-b5-https-api-naengo-com` |
| 대상 | ALB / ACM / DNS (HostingKR) |
| 트리거 | 도메인 결정 (`naengo.com`, HostingKR 공용 계정 구매) + 팀 합의 (서브도메인 분기: `api.naengo.com` 우리, `ai.naengo.com` AI) |
| 작성자 | API 서버 담당자 |
| 작성일 | 2026-05-26 |

---

## 1. 결정 사항

### 1.1 서브도메인 분담

| 서브도메인 | 정본 | 비고 |
|---|---|---|
| `api.naengo.com` | 우리 (Spring API) | 본 PR 부착 완료 |
| `ai.naengo.com` | AI (FastAPI) | AI 팀 독립 진행 — 같은 방식으로 ACM 발급 + HostingKR DNS 등록 |
| `naengo.com` (apex) / `www.naengo.com` | (미정) | 랜딩 페이지 / 또는 admin redirect — 추후 합의 |
| `admin.naengo.com` 또는 Vercel 기본 도메인 | admin (React) | admin 팀 결정 대기 |

### 1.2 우리 측 HTTP/HTTPS 처리 결정 (안건 1)

**(가) HTTP → HTTPS 301 redirect** 채택. 옛 HTTP 호출 / 외부 링크도 자동 HTTPS 전환.

### 1.3 CORS 범위 (안건 2)

- **front (Flutter mobile app)**: CORS 면제 — 브라우저가 아니라 OS HTTP client → Origin 검증 미적용
- **admin (React web)**: CORS 필요. 운영 URL 결정 대기 (admin 팀)
- 따라서 `CORS_ALLOWED_ORIGINS` 최종값 = admin 운영 URL 1개만 (front 미포함)

---

## 2. 실 작업 (CLI)

### 2.1 ACM 인증서 발급

```bash
aws acm request-certificate \
  --domain-name api.naengo.com \
  --validation-method DNS \
  --key-algorithm RSA_2048 \
  --region ap-northeast-2 \
  --tags Key=Project,Value=naengo Key=Component,Value=api-server
```

→ ARN: `arn:aws:acm:ap-northeast-2:518056141724:certificate/b501f6d7-0245-4efb-8298-fa6e134a261f`

### 2.2 검증 CNAME (HostingKR 사용자 등록)

| 타입 | 호스트 | 값 |
|---|---|---|
| CNAME | `_fa840461427253c1dd06e622fef07d62.api` | `_8c056d96780803770c60cbf393fda575.jkddzztszm.acm-validations.aws.` |

검증 소요: 5분 (요청 13:02 KST → ISSUED 13:07 KST)

### 2.3 `api.naengo.com` → ALB DNS CNAME (HostingKR 사용자 등록)

| 타입 | 호스트 | 값 |
|---|---|---|
| CNAME | `api` | `naengo-api-server-alb-176175450.ap-northeast-2.elb.amazonaws.com` |

### 2.4 ALB SG inbound 443

```bash
aws ec2 authorize-security-group-ingress \
  --group-id sg-0f143ba6a8d9997d2 \
  --protocol tcp --port 443 --cidr 0.0.0.0/0 \
  --region ap-northeast-2
```

→ SG rule ID: `sgr-082149f3d7dfa44ec`

### 2.5 listener:443 생성

```bash
aws elbv2 create-listener \
  --load-balancer-arn arn:aws:elasticloadbalancing:ap-northeast-2:518056141724:loadbalancer/app/naengo-api-server-alb/159ba31da2dc086e \
  --protocol HTTPS --port 443 \
  --certificates CertificateArn=arn:aws:acm:ap-northeast-2:518056141724:certificate/b501f6d7-0245-4efb-8298-fa6e134a261f \
  --default-actions Type=forward,TargetGroupArn=arn:aws:elasticloadbalancing:ap-northeast-2:518056141724:targetgroup/naengo-api-server-tg/1303d640c7a0ed98 \
  --ssl-policy ELBSecurityPolicy-TLS13-1-2-2021-06 \
  --region ap-northeast-2
```

→ ARN: `arn:aws:elasticloadbalancing:ap-northeast-2:518056141724:listener/app/naengo-api-server-alb/159ba31da2dc086e/4312f361a7170e6c`

SSL policy 선택: `ELBSecurityPolicy-TLS13-1-2-2021-06` — TLS 1.2/1.3 최신 보안 표준, 약한 cipher 차단.

### 2.6 listener:80 → HTTPS redirect

```bash
aws elbv2 modify-listener \
  --listener-arn arn:aws:elasticloadbalancing:ap-northeast-2:518056141724:listener/app/naengo-api-server-alb/159ba31da2dc086e/bebdb0686756e3b2 \
  --default-actions 'Type=redirect,RedirectConfig={Protocol=HTTPS,Port=443,StatusCode=HTTP_301}' \
  --region ap-northeast-2
```

→ Redirect rule: `http://{host}/{path}?{query}` → `https://{host}/{path}?{query}` (HTTP 301)

---

## 3. 검증

### 3.1 단건 smoke

| 호출 | 응답 | 의미 |
|---|---|---|
| `curl https://api.naengo.com/` | 200 | HTTPS direct OK, 인증서 chain 정상 (-k 불요) |
| `curl -I http://api.naengo.com/` | 301 Moved Permanently | HTTP → HTTPS redirect 동작 |
| `curl -X POST https://api.naengo.com/api/v1/auth/login {bad}` | 401 | 서버 정상 응답 |

### 3.2 운영 e2e 재실행 (`scripts/e2e-smoke-prod.sh`, BASE=https://api.naengo.com)

**33/33 PASS** 🎉

| 어제 (HTTP ALB DNS) | 지금 (HTTPS api.naengo.com) |
|---|---|
| 32/33 PASS | **33/33 PASS** |
| `#23 cookie-based auth` Secure+HTTP 충돌 401 | **#23 ✅ 200 정상** (HTTPS 환경에서 Secure 쿠키 동봉) |

→ 옵션 A 코드가 운영 HTTPS 환경에서 완전 동작 입증.

---

## 4. KAKAO_REDIRECT_URI 처리

| 사실 | 결론 |
|---|---|
| 모바일 front 흐름 (`POST /auth/social/kakao`) | redirect URI **안 씀** |
| DevOAuthController (`/oauth/kakao/test-callback`) | dev/test 용. 운영 미사용 |
| `application-prod.yml` 부팅 시 필수값 | 값 자체는 있어야 함 — placeholder OK |

→ **현 상태 유지** (`http://localhost:8080/oauth/kakao/test-callback`). 운영에서 안 쓰는 dev callback. 추후 web OAuth 필요 시 (예: admin 카카오 로그인) 갱신.

---

## 5. 인증서 자동 갱신

ACM 의 **DNS validation 인증서는 자동 갱신**. 검증 CNAME 이 HostingKR 에 살아있는 동안 ACM 이 매년 자동 재발급 + ALB 에 자동 attach.

| 항목 | 동작 |
|---|---|
| 갱신 시점 | 만료 60일 전 자동 트리거 |
| 사용자 액션 | 없음 (CNAME 만 유지) |
| 알림 | ACM 콘솔 + Personal Health Dashboard |

> HostingKR 에서 검증 CNAME 을 실수로 삭제하면 갱신 실패. 변경 시 주의.

---

## 6. 후속 작업 (다음 PR 단위)

| # | 작업 | 의존 | 시점 |
|---|---|---|---|
| A2/A3 | CORS_ALLOWED_ORIGINS 좁히기 | admin 운영 URL 확정 | admin 팀 결정 후 |
| AI 측 | `ai.naengo.com` 같은 방식 진행 | AI 팀 작업 | AI 팀 일정 |
| C5 | AI 가 JWT 검증 적용 | AI 팀 작업 | 진행 중 |
| docs | `auth-user-api.md` Base URL = `https://api.naengo.com` 명시 | — | 본 PR 와 함께 |
| docs | `deploy-status.md` B5 ✅ + 변경이력 | — | 본 PR 와 함께 |

---

## 7. 참조

- ACM Cert: `arn:aws:acm:ap-northeast-2:518056141724:certificate/b501f6d7-0245-4efb-8298-fa6e134a261f`
- ALB: `arn:aws:elasticloadbalancing:ap-northeast-2:518056141724:loadbalancer/app/naengo-api-server-alb/159ba31da2dc086e`
- Listener:443: `arn:aws:elasticloadbalancing:ap-northeast-2:518056141724:listener/app/naengo-api-server-alb/159ba31da2dc086e/4312f361a7170e6c`
- Listener:80 (redirect): `arn:aws:elasticloadbalancing:ap-northeast-2:518056141724:listener/app/naengo-api-server-alb/159ba31da2dc086e/bebdb0686756e3b2`
- DNS provider: HostingKR (공용 계정 — 양 팀 작업 시 충돌 주의)
- e2e script: `scripts/e2e-smoke-prod.sh` (BASE 환경변수로 local/prod 전환)
