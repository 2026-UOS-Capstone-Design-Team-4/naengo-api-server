#!/bin/bash
# ============================================================
# naengo api-server EC2 부트스트랩 (Amazon Linux 2023 ARM / t4g.small)
# EC2 launch 시 user-data 로 주입 — 첫 부팅에 root 로 1회 실행.
# 역할: Docker 설치 → ECR 로그인 → Secrets Manager 에서 env 구성 →
#       app 컨테이너 + Caddy(자동 Let's Encrypt) 기동.
# 멱등성: 재실행 시 기존 컨테이너 제거 후 재기동.
# ============================================================
set -euxo pipefail

ACCOUNT=518056141724
REGION=ap-northeast-2
REGISTRY=$ACCOUNT.dkr.ecr.$REGION.amazonaws.com
IMAGE=$REGISTRY/naengo-api-server:latest
DOMAIN=api.naengo.com

# 1) Docker 설치 + 기동
dnf update -y
dnf install -y docker
systemctl enable --now docker

# 2) ECR 로그인 (instance role 사용)
aws ecr get-login-password --region $REGION | docker login --username AWS --password-stdin $REGISTRY

# 3) Secrets Manager 3종 → /opt/naengo/app.env (python3 stdlib, boto3 불요)
mkdir -p /opt/naengo
get_secret() { aws secretsmanager get-secret-value --secret-id "$1" --region $REGION --query SecretString --output text; }
python3 - "$(get_secret naengo/prod/db)" "$(get_secret naengo/prod/jwt)" "$(get_secret naengo/prod/kakao)" << 'PY'
import sys, json
out = {}
for raw in sys.argv[1:]:
    out.update(json.loads(raw))
# prod 부팅 필수 추가 env
out['SPRING_PROFILES_ACTIVE'] = 'prod'
out['CORS_ALLOWED_ORIGINS'] = '*'   # admin 은 vercel proxy 라 CORS 무영향. 추후 좁힘 가능
with open('/opt/naengo/app.env', 'w') as f:
    for k, v in out.items():
        f.write(f"{k}={v}\n")
PY
chmod 600 /opt/naengo/app.env

# 4) docker network
docker network create naengo 2>/dev/null || true

# 5) app 컨테이너 (arm64 이미지, 자동 재시작, CloudWatch logs)
docker rm -f naengo-app 2>/dev/null || true
docker pull $IMAGE
docker run -d --name naengo-app --restart unless-stopped \
  --network naengo \
  --env-file /opt/naengo/app.env \
  --log-driver awslogs \
  --log-opt awslogs-region=$REGION \
  --log-opt awslogs-group=/ec2/naengo-api-server \
  --log-opt awslogs-create-group=true \
  $IMAGE

# 6) Caddy — 80/443 리버스 프록시 + api.naengo.com 자동 Let's Encrypt
#    DNS 가 이 인스턴스를 가리켜야 cert 발급됨 (HTTP-01). DNS 컷오버 전엔 retry 상태.
cat > /opt/naengo/Caddyfile << CADDY
$DOMAIN {
    reverse_proxy naengo-app:8080
}
CADDY

docker rm -f caddy 2>/dev/null || true
docker run -d --name caddy --restart unless-stopped \
  --network naengo \
  -p 80:80 -p 443:443 \
  -v /opt/naengo/Caddyfile:/etc/caddy/Caddyfile \
  -v caddy_data:/data \
  -v caddy_config:/config \
  caddy:2

echo "naengo bootstrap complete"
