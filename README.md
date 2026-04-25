# naengo-api-server

앱의 **API 서버**. DB I/O 관리, 인증/인가, AI 서버와의 통신을 담당한다.

> 이 저장소만 읽고도 다른 팀원 도움 없이 로컬에서 기동할 수 있게 작성되어 있다.
> 아키텍처·책임 분담·진행 상황은 **[`docs/api-server-tasks.md`](docs/api-server-tasks.md)** 를 우선 참고.
> 기능별 상세 계약은 **[`docs/spec/`](docs/spec/)**, DB 동작 검증 방법은 **[`docs/db-testing-guide.md`](docs/db-testing-guide.md)**.

---

## 전제

- **Java 21** (프로젝트 `build.gradle` 의 `toolchain` 으로 강제)
- **Docker Desktop** 또는 동등한 Docker 엔진 (로컬 DB 용)
- IDE: IntelliJ IDEA 권장 (Lombok 플러그인 + Annotation Processor 활성화)

Gradle 은 `./gradlew` (wrapper) 를 쓰므로 별도 설치 불필요.

---

## 최초 1회 설정

### 1. 저장소 클론 후 DB 컨테이너 기동

```bash
docker compose up -d
```

`pgvector/pgvector:pg16` 이미지를 사용한다. `jdbc:postgresql://localhost:5432/naengo` 로 접속되도록 기본값이 잡혀 있다.

### 2. 스키마 적용

Flyway 가 **서버 기동 시 자동으로** `src/main/resources/db/migration/V*.sql` 를 순서대로 실행한다. 수동 적용은 필요 없다.

### 3. 서버 기동

```bash
./gradlew bootRun
```

기본 프로파일은 `local` (application.yml `spring.profiles.active: local`). 필요 시 override:

```bash
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

### 4. 헬스체크

```bash
curl -s http://localhost:8080/health
# {"status":"UP"}
```

---

## 환경변수 (선택)

모든 값에 로컬 기본값이 있어 env 없이도 기동된다. 운영·개인 환경을 덮어쓰려면 아래를 export:

| 키 | 기본(local) | 설명 |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `local` | `local` / `prod` |
| `DB_URL` | `jdbc:postgresql://localhost:5434/naengo` | JDBC URL. 호스트 포트가 **5434** 인 이유는 `docker-compose.yml` 주석 참조 |
| `DB_USERNAME` | `naengo` | |
| `DB_PASSWORD` | `naengo` | |
| `JWT_SECRET` | 32자 더미 | **운영에서는 반드시 교체**. 32자 이상 |
| `KAKAO_REST_API_KEY` | `""` | 카카오 로그인 테스트용 |
| `KAKAO_REDIRECT_URI` | `http://localhost:8080/oauth/kakao/test-callback` | |
| `AWS_REGION` | `ap-northeast-2` | |
| `AWS_S3_BUCKET` | `""` | S3 업로드 엔드포인트가 구현될 때 필요. 비어있으면 업로드 관련 기능은 비활성 |
| `AWS_S3_PUBLIC_URL_PREFIX` | `""` | 레시피 `imageUrl` 프리픽스 검증용. 비어있으면 검증 스킵 |

로컬에서 개인 override 를 쓰고 싶다면 `.env.local` 을 만들어 IDE Run Config 또는 쉘에서 로드한다. (`.gitignore` 에 `.env*` 포함)

---

## 자주 하는 작업

### DB 접속

```bash
docker compose exec postgres psql -U naengo -d naengo
```

### 스키마 완전 리셋

```bash
docker compose down -v    # 볼륨까지 삭제 → 다음 기동 시 V1~V3 재적용
docker compose up -d
```

### 빌드 / 테스트

```bash
./gradlew build            # 테스트 포함
./gradlew build -x test    # 테스트 제외 (현재 통합 테스트는 Step 8 에서 구성 예정)
```

### 기동 중 엔드포인트 확인

```bash
# 회원가입 / 로그인 후 토큰 획득
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/signup \
  -H 'Content-Type: application/json' \
  -d '{"email":"a@b.c","password":"pw123456","nickname":"tester"}' \
  | jq -r '.data.accessToken')

# 레시피 작성
curl -s -X POST http://localhost:8080/api/recipes \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"title":"김치볶음밥","fullContent":"...","ingredients":[{"name":"김치","amount":"200g"}]}'

# 목록
curl -s http://localhost:8080/api/recipes
```

더 많은 시나리오는 `docs/spec/*.md` 참고.

---

## 프로젝트 구조

```
src/main/java/com/naengo/api_server/
├── ApiServerApplication.java
├── domain/
│   ├── recipe/           Recipe 도메인 (Step 2 구현 완료)
│   └── user/             User·소셜 로그인
└── global/
    ├── auth/             JWT + OAuth + SecurityUtil
    ├── config/           SecurityConfig
    ├── controller/       HealthController
    ├── dto/              ApiResponse
    └── exception/        ErrorCode 등

src/main/resources/
├── application.yml           공통
├── application-local.yml     로컬 기본값 (DB / secret 포함, 실 secret 은 env)
├── application-prod.yml      운영(env 필수)
└── db/migration/
    ├── V1__init.sql
    ├── V2__add_social_login_fields.sql
    └── V3__add_user_deleted_at.sql

docs/
├── api-server-tasks.md       담당 범위·진행 현황·Step 순서
├── spec-template.md          기능 명세 템플릿
├── change-log-template.md    수정이력 템플릿
├── db-testing-guide.md       DB 동작 수동 검증 절차
└── spec/                     기능별 명세 (SPEC-*)
```

---

## 기여 흐름

1. `docs/api-server-tasks.md §6 작업 순서` 에서 다음에 할 Step 확인
2. 기능 단위로 `docs/spec-template.md` 를 복사해 `docs/spec/<이름>.md` 로 명세 작성
3. 명세서에 맞춰 코드 작성 → `./gradlew build -x test` 로 컴파일 확인
4. 수정 사항이 생기면 `docs/change-log-template.md` 로 이력 기록 (명세서 자체는 가급적 보존)
5. 커밋 후 `docs/api-server-tasks.md` 해당 Step 체크박스 / 인벤토리 갱신 (**매번**)
6. `git push -u origin <feature-branch>`

---

## 문제 해결

| 증상 | 원인·해결 |
|---|---|
| `Connection to localhost:5432 refused` | 예전 설정이 남아있어 기본 포트로 접속 시도 중. 이 저장소는 **5434** 사용. `DB_URL` env 확인 (`Get-ChildItem Env:DB_URL`). 설정돼 있으면 제거하거나 `jdbc:postgresql://localhost:5434/naengo` 로 맞춤 |
| `Connection to localhost:5434 refused` | 컨테이너 미기동 or 다운. `docker compose ps` 로 확인, `docker compose up -d` 재기동 |
| `docker compose up` 시 `bind: address already in use` on 5434 | 5434 가 이미 점유됨. `docker-compose.override.yml` 을 만들어 다른 포트로 매핑(예: `5435:5432`) + `DB_URL=jdbc:postgresql://localhost:5435/naengo` env |
| `./gradlew bootRun` 시 `FATAL: password authentication failed` | Docker 컨테이너가 아직 기동 중. `docker compose ps` 로 healthy 확인 |
| `Validation failed for .../User` 등 Hibernate 오류 | 엔티티와 DB 스키마가 어긋남. `docker compose down -v && docker compose up -d` 로 리셋 후 재기동. 의도적 스키마 변경이면 새 `Vn__*.sql` 작성 |
| `FlywayException: Detected applied migration not resolved locally` | 브랜치 전환 후 마이그레이션 파일이 누락됨. `docker compose down -v` 로 DB 초기화 |
| `/health` 는 200 인데 다른 엔드포인트는 401 | 정상. JWT 없는 상태에서 보호 엔드포인트 접근 → 먼저 `/api/auth/login` |

더 자세한 DB 문제 진단은 `docs/db-testing-guide.md`.
