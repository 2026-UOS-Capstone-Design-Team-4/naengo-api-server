# DB 동작 수동 검증 가이드

> **위치**: Step 8 에서 JUnit 통합 테스트로 자동화하기 전, **수동으로** 돌려 확신을 얻기 위한 체크리스트.
> **선결**: `docker compose up -d` 로 로컬 Postgres(pgvector) 가 기동 중이어야 한다.
> **AI 서버 contract 가 닿는 테이블** (`recipes.embedding`, `chat_rooms`, `chat_messages`) 의 검증 항목은 [AI 서버 OpenAPI](http://43.201.62.254:8000/docs) (로컬 스냅샷: [`api-1.json`](api-1.json)) 와 대조하며 본다. 본 가이드의 기대값과 docs 가 어긋나면 docs 가 우선.

모든 커맨드는 저장소 루트에서 실행한다고 가정한다.

---

## 0. 공통 셋업

```bash
# DB 리셋 후 기동 (깨끗한 상태에서 검증)
docker compose down -v
docker compose up -d
docker compose exec postgres pg_isready -U naengo -d naengo   # accepting 확인

# 서버 기동 (마이그레이션 자동 적용됨)
./gradlew bootRun
```

다른 터미널에서 psql 접속:
```bash
docker compose exec postgres psql -U naengo -d naengo
```

아래부터 각 체크항목의 확인 SQL / cURL 을 나열한다. 체크박스는 검증이 끝나면 표시.

---

## 1. Flyway 마이그레이션이 V1 → V2 → V3 순으로 적용된다

```sql
SELECT installed_rank, version, description, success
FROM flyway_schema_history ORDER BY installed_rank;
```

기대값:
| rank | version | description              | success |
|------|---------|--------------------------|---------|
| 1    | 1       | init                     | t       |
| 2    | 2       | add social login fields  | t       |
| 3    | 3       | add user deleted at      | t       |

> V1 은 2026-05-02 PR 에서 구 V4 (`V4__fixed_schema.sql`) 의 설계를 흡수해 재작성됨. 자세한 변경 내용은 [`api-server-tasks.md §1.5`](api-server-tasks.md). 협업자별 로컬 DB 는 `docker compose down -v` 로 wipe 필수 (이전 V1 의 체크섬과 다르므로).

- [ ] 3건 모두 `success = true`
- [ ] V4 row 가 보이면 stale state — `docker compose down -v` 로 history wipe 후 재기동

---

## 2. 테이블 스키마가 의도대로 생성되었다

```sql
\d users
\d user_profiles
\d recipes
\d pending_recipes
\d recipe_stats
\d scraps
\d likes
\d chat_rooms
\d chat_messages
SELECT trigger_name, event_manipulation, event_object_table
FROM information_schema.triggers
ORDER BY event_object_table, trigger_name;
-- trigger_likes_count / trigger_scrap_count / trigger_recipe_stats_create 가 보여야 함
```

확인 포인트:
- [ ] `users.provider` (VARCHAR(20) NOT NULL DEFAULT 'LOCAL')
- [ ] `users.provider_id` (VARCHAR(255), UNIQUE 복합 제약 `uq_provider_provider_id` — V2 가 추가)
- [ ] `users.deleted_at` (TIMESTAMPTZ NULL) — V3 가 추가
- [ ] `users.is_active` (BOOLEAN NOT NULL DEFAULT TRUE)
- [ ] `users.password_hash` 가 nullable (소셜 로그인용)
- [ ] `recipes.embedding` (`vector(1536)` 타입)
- [ ] `recipes.author_id` 가 `BIGINT REFERENCES users(user_id) ON DELETE SET NULL`
- [ ] `recipes.author_type` (VARCHAR(20) CHECK ADMIN/USER, DEFAULT 'ADMIN')
- [ ] `recipes.is_active` (BOOLEAN NOT NULL DEFAULT TRUE)
- [ ] `recipes` 에 AI contract 정합 컬럼 11개:
  - `description TEXT NOT NULL`
  - `ingredients JSONB NOT NULL` (IngredientItem[])
  - `ingredients_raw TEXT NOT NULL`
  - `instructions JSONB NOT NULL` (string[])
  - `servings NUMERIC(4,1) NOT NULL`
  - `cooking_time INTEGER NOT NULL` (분)
  - `calories INTEGER` (NULL 가능)
  - `difficulty VARCHAR(10)` CHECK (`easy`/`normal`/`hard`)
  - `category JSONB NOT NULL` (string[])
  - `tags JSONB NOT NULL DEFAULT '[]'`
  - `tips JSONB NOT NULL DEFAULT '[]'`
  - `video_url VARCHAR(512)`
- [ ] `recipes(video_url)` 부분 인덱스 (`WHERE video_url IS NOT NULL`)
- [ ] `pending_recipes` 테이블 + `idx_pending_recipes_user_id` / `idx_pending_recipes_status`
- [ ] `chat_messages` 테이블 (`role` CHECK `user`/`model`, `recipe_ids JSONB`)
- [ ] `chat_rooms.room_id` 이 `BIGSERIAL` (구 V1 의 `VARCHAR(100)` 폐기 됨)
- [ ] `user_profiles` 테이블 — `users.preferences` 가 컬럼별로 분리됨
- [ ] `scraps`, `likes` 의 FK 가 `ON DELETE CASCADE`
- [ ] DB 트리거 3종이 존재
  ```sql
  SELECT trigger_name FROM information_schema.triggers
  WHERE trigger_name IN ('trigger_likes_count','trigger_scrap_count','trigger_recipe_stats_create');
  ```

---

## 3. pgvector 확장이 설치되었다

```sql
SELECT extname, extversion FROM pg_extension WHERE extname = 'vector';
```

- [ ] 1 row 반환, `extversion` 이 비어있지 않음

**왜 필요한가**: API 서버는 `embedding` 컬럼에 직접 R/W 하지 않지만, 스키마가 성립해야 JPA 가 `recipes` 를 매핑할 수 있다(엔티티에는 매핑하지 않아 validate 는 통과하지만, 컬럼 자체는 존재해야 한다).

---

## 4. Hibernate `ddl-auto: validate` 가 엔티티-스키마 드리프트를 잡아낸다

드리프트를 **의도적으로 유발**해 실패 여부 확인:

1. `User.java` 에서 `@Column(name = "email")` 을 `@Column(name = "email_x")` 로 임시 변경
2. `./gradlew bootRun`
3. 기대: 기동 실패 로그에 `Schema-validation: missing column [email_x]` 류 메시지

- [ ] 드리프트 유발 시 기동 실패
- [ ] 원복 후 정상 기동

**주의**: DB 에만 존재하고 엔티티에 매핑되지 않은 컬럼(예: `recipes.embedding`)은 validate 가 **문제 삼지 않는다**. 이게 우리 설계의 전제.

---

## 5. 회원가입 → 로그인 → 토큰 플로우

```bash
# 회원가입
curl -s -X POST http://localhost:8080/api/auth/signup \
  -H 'Content-Type: application/json' \
  -d '{"email":"a@b.c","password":"pw123456","nickname":"tester"}' | jq

# DB 에 row 들어갔는지 확인
```

```sql
SELECT user_id, email, nickname, provider, is_blocked, deleted_at FROM users;
```

- [ ] row 1건, `provider = 'LOCAL'`, `deleted_at IS NULL`

로그인 토큰 발급:
```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"a@b.c","password":"pw123456"}' | jq -r '.data.accessToken')
echo $TOKEN
```

- [ ] `$TOKEN` 이 JWT 형태 (3개 파트 `.` 구분)

---

## 6. 사용자 레시피 제출 → `pending_recipes` INSERT

> **2026-05-04 갱신**: V4 통합 후 사용자 작성은 `recipes` 가 아닌 `pending_recipes` 테이블로 들어간다 (`SPEC-20260502-02` recipe-create v2). 승인되면 admin 이 `recipes` 로 옮긴다 (`SPEC-20260504-02`). 본 §은 사용자 제출 단계 검증.

```bash
curl -s -X POST http://localhost:8080/api/recipes \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{
    "title":"김치볶음밥",
    "content":"1. 팬에 기름\n2. 김치 볶기",
    "ingredients":[
      {"name":"김치","amount":"200","unit":"g","type":"메인","note":null},
      {"name":"밥",  "amount":"1",  "unit":"공기","type":"메인","note":null}
    ]
  }' | jq
```

- [ ] 응답 `{"success":true,"data":{"pendingRecipeId":<n>,"status":"PENDING"}}`

```sql
SELECT pending_recipe_id, user_id, title, content, ingredients, status, is_active
FROM pending_recipes;
```

- [ ] `status = 'PENDING'`, `is_active = true`
- [ ] `ingredients` JSONB 가 `{name, amount, unit, type, note}` element schema 로 저장

> 승인 후 `recipes` 테이블에 row 가 생기고 트리거 `trigger_recipe_stats_create` 가 `recipe_stats(recipe_id, 0, 0)` 자동 INSERT. `recipe_stats` 의 사용자 작성 단계 INSERT 는 더 이상 발생하지 않는다.

**실패 케이스 (롤백)**: `aws.s3.public-url-prefix` 를 `https://invalid/` 로 설정하고 외부 `imageUrl` 보내면 400. `pending_recipes` 에 row 없어야 함.

- [ ] 롤백 케이스: `pending_recipes` row 0

---

## 7. 공개 목록 / 단건 / 내 제출 권한

> **2026-05-04 갱신**: 공개 목록은 `recipes.is_active = true` 만 (`SPEC-20260502-03` recipe-read v2). 사용자 제출(PENDING) 은 `pending_recipes` 에 별도 — `/api/recipes/my` 가 그쪽 조회.

테스트를 위해 admin 이 위 §6 의 pending 을 승인해 둔 상태:
```sql
-- ADMIN 권한이 있는 사용자가 필요. 첫 사용자를 ADMIN 으로 임시 승급:
UPDATE users SET role = 'ADMIN' WHERE user_id = 1;
```
그 뒤 admin 토큰으로 승인 호출:
```bash
ADMIN_TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"a@b.c","password":"pw123456"}' | jq -r '.data.accessToken')
curl -s -X POST -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' \
  -d '{"adminNote":"테스트 승인"}' \
  http://localhost:8080/api/admin/pending-recipes/1/approve | jq
# 응답에 recipeId 가 새로 발급됨. 이후부턴 그 recipeId 로 조회.
```

```bash
# 1) 비로그인 공개 목록 (활성 레시피만)
curl -s http://localhost:8080/api/recipes | jq '.data.totalElements'
# 1 이상 (방금 승인된 recipe 등장)

# 2) 비로그인 단건 (활성 레시피)
curl -si http://localhost:8080/api/recipes/1 | head -1
# HTTP/1.1 200 OK

# 3) 내 제출 목록 (비로그인) → 401
curl -si http://localhost:8080/api/recipes/my | head -1
# HTTP/1.1 401

# 4) 내 제출 목록 (본인) → pending_recipes 의 본인 행, 모든 status
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/recipes/my | jq
# items[0].status = 'APPROVED' (방금 승인됨), pendingRecipeId, adminNote 노출
```

비활성화 검증:
```sql
UPDATE recipes SET is_active = false WHERE recipe_id = 1;
```
```bash
curl -si http://localhost:8080/api/recipes/1 | head -1
# HTTP/1.1 404 (RECIPE_NOT_FOUND — is_active=false 면 미노출)
```

- [ ] 공개 목록 정상
- [ ] 비로그인 활성 단건 → 200
- [ ] is_active=false 단건 → 404
- [ ] /my 비로그인 → 401, 본인 → pending_recipes 노출

---

## 8. 레시피 삭제 — pending_recipes 본인 삭제 (V4 후 의미 변경)

> **2026-05-04 갱신**: V4 통합으로 `session_logs` 테이블 폐기. `DELETE /api/recipes/{id}` 의 `{id}` 는 이제 **`pending_recipe_id`** 의 의미 (`SPEC-20260502-04` recipe-delete v2). 승인된 `recipes` 의 삭제는 admin 영역(Step 6 이후 별도 endpoint).

세팅 (위 §6 으로 pending 1건 만든 상태):

```bash
# pending 1건 추가 (승인 안 함)
curl -s -X POST http://localhost:8080/api/recipes \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"title":"삭제 테스트","content":"본문"}' > /dev/null
```

```sql
SELECT pending_recipe_id, user_id, title, status FROM pending_recipes;
```

본인 삭제 호출:
```bash
curl -si -X DELETE -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/recipes/2
# HTTP/1.1 204
```

검증:
```sql
SELECT * FROM pending_recipes WHERE pending_recipe_id = 2;  -- 0 rows
```

- [ ] `pending_recipes` 본인 row 삭제됨
- [ ] `recipes` 는 영향 없음

타인 / 권한:
- 다른 유저 토큰으로 같은 endpoint 호출 → 403 `FORBIDDEN`
- 존재하지 않는 id → 404 `PENDING_RECIPE_NOT_FOUND`
- [ ] 권한·존재 분기 모두 의도대로

좋아요 / 스크랩 cascade 검증 (recipes 행 삭제 시):
```sql
INSERT INTO likes (user_id, recipe_id) VALUES (1, 1);
INSERT INTO scraps (user_id, recipe_id) VALUES (1, 1);
DELETE FROM recipes WHERE recipe_id = 1;
SELECT * FROM likes WHERE recipe_id = 1;       -- 0 rows (CASCADE)
SELECT * FROM scraps WHERE recipe_id = 1;      -- 0 rows (CASCADE)
SELECT * FROM recipe_stats WHERE recipe_id = 1; -- 0 rows (CASCADE)
```

- [ ] recipes DELETE 시 likes / scraps / recipe_stats 모두 CASCADE 로 같이 삭제 (트리거 카운터 감소도 발화)

---

## 9. 탈퇴 사용자 닉네임 치환

> **2026-05-04 갱신**: Step 4 (`SPEC-20260503-07` user-withdraw) 가 완료되어 `DELETE /api/users/me` API 가 존재. 본 §은 (a) endpoint 호출 / (b) DB 직접 조작 두 가지 방식 모두 지원.

방식 A — 탈퇴 endpoint 호출:
```bash
curl -si -X DELETE -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/users/me
# HTTP/1.1 204
```

방식 B — DB 직접:
```sql
UPDATE users
SET nickname = '탈퇴한 사용자_1',
    deleted_at = NOW(),
    is_blocked = true,
    is_active = false,
    email = NULL,
    password_hash = NULL
WHERE user_id = 1;
```

공개 응답 확인:
```bash
curl -s http://localhost:8080/api/recipes | jq '.data.items[0].authorNickname'
# "탈퇴한 사용자"   <- 꼬리표 제거된 형태로 보여야 함 (AuthorDisplayName)
```

- [ ] `authorNickname` 이 `"탈퇴한 사용자"` (꼬리표 없음)

원복 (다음 검증 진행을 위해 — 방식 B 만):
```sql
UPDATE users
SET email='a@b.c', password_hash='$2a$10$...', nickname='tester',
    deleted_at=NULL, is_blocked=false, is_active=true
WHERE user_id = 1;
```

---

## 10. pgvector 컬럼 직접 조작 (AI 서버 시뮬레이션)

API 서버는 `recipes.embedding` 에 쓰지 않지만, AI 서버가 채울 값이 실제로 저장·유지되는지 확인.

```sql
UPDATE recipes
SET embedding = (SELECT ARRAY(SELECT random() FROM generate_series(1, 1536))::vector)
WHERE recipe_id = 1;

SELECT recipe_id, vector_dims(embedding) FROM recipes WHERE recipe_id = 1;
-- recipe_id | vector_dims
--     1     |   1536
```

- [ ] vector 타입으로 저장됨, 차원 1536

이후 API 서버가 레시피 단건·목록을 조회해도 문제 없는지:
```bash
curl -s http://localhost:8080/api/recipes/1 | jq
```

- [ ] 정상 응답. embedding 컬럼은 응답에 노출되지 않음

---

## 체크리스트 요약

위 각 섹션에서 발견한 문제는 `docs/change-log-template.md` 형식으로 `docs/changes/` 에 기록한다.
모든 항목이 통과하면, Step 8 통합 테스트 작성 시 동일 시나리오를 JUnit 으로 코드화.
