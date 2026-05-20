# syntax=docker/dockerfile:1.6
# ============================================================
# naengo-api-server — 운영 컨테이너 이미지
#
# 사용법
#   build:  docker build -t naengo-api-server:$(git rev-parse --short HEAD) .
#   run:    docker run --rm -p 8080:8080 \
#             -e SPRING_PROFILES_ACTIVE=prod \
#             -e DB_URL=jdbc:postgresql://<host>:5432/naengo_db \
#             -e DB_USERNAME=naengo -e DB_PASSWORD=*** \
#             -e JWT_SECRET=*** \
#             -e KAKAO_REST_API_KEY=*** -e KAKAO_REDIRECT_URI=https://api.naengo.kr/oauth/kakao/test-callback \
#             -e CORS_ALLOWED_ORIGINS='*' \
#             naengo-api-server:<tag>
#
# 환경변수 전체 목록: docs/deploy-env.md
# .env 는 절대 이미지에 포함하지 않음 (.dockerignore 로 차단). 운영은 ECS task / k8s secret / Secrets Manager 로 주입.
# ============================================================

# ── Stage 1: build ───────────────────────────────────────────
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /workspace

# 의존성 캐시 우선: 빌드 스크립트만 먼저 복사해서 layer cache 활용
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --no-daemon --version >/dev/null 2>&1 || true

# 소스 복사 후 bootJar (테스트는 Testcontainers 의존 → 이미지 빌드에선 제외)
COPY src ./src
RUN ./gradlew --no-daemon bootJar -x test \
    && cp build/libs/*.jar app.jar

# RDS CA 번들 다운로드 — DB_URL 의 sslmode=verify-full + sslrootcert=/app/global-bundle.pem 용.
# 모든 리전 통합 번들 (https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/UsingWithRDS.SSL.html).
# builder 단계에서만 curl 사용 — runtime 이미지엔 curl 안 들어감.
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl ca-certificates \
 && curl -fsSL -o /workspace/global-bundle.pem \
      https://truststore.pki.rds.amazonaws.com/global/global-bundle.pem \
 && rm -rf /var/lib/apt/lists/*

# ── Stage 2: runtime ─────────────────────────────────────────
FROM eclipse-temurin:21-jre AS runtime

# 비루트 실행 (보안 기본)
RUN groupadd --system --gid 1000 naengo \
 && useradd --system --uid 1000 --gid 1000 --no-create-home --shell /usr/sbin/nologin naengo

WORKDIR /app
COPY --from=builder /workspace/app.jar /app/app.jar
COPY --from=builder /workspace/global-bundle.pem /app/global-bundle.pem
RUN chown -R naengo:naengo /app
USER naengo:naengo

# 기본 prod profile (배포 시 -e SPRING_PROFILES_ACTIVE=prod 로 명시 권장)
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080

# 헬스체크는 컨테이너 오케스트레이터(LB target group / k8s probe) 가 GET / 로 수행 — 본 이미지에 별도 HEALTHCHECK 두지 않음
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
