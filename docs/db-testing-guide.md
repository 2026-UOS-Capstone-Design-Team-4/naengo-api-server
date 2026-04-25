# DB 동작 수동 검증 가이드

> **위치**: Step 8 에서 JUnit 통합 테스트로 자동화하기 전, **수동으로** 돌려 확신을 얻기 위한 체크리스트.
> **선결**: `docker compose up -d` 로 로컬 Postgres(pgvector) 가 기동 중이어야 한다.

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
| rank | version | description             | success |
|------|---------|-------------------------|---------|
| 1    | 1       | init                    | t       |
| 2    | 2       | add social login fields | t       |
| 3    | 3       | add user deleted at     | t       |

- [ ] 3건 모두 `success = true`

---

## 2. 테이블 스키마가 의도대로 생성되었다

```sql
\d users
\d recipes
\d recipe_stats
\d scraps
\d likes
\d chat_rooms
\d session_logs
\d fridge
```

확인 포인트:
- [ ] `users.provider` (VARCHAR(20) NOT NULL DEFAULT 'LOCAL')
- [ ] `users.provider_id` (VARCHAR(255), UNIQUE 복합 제약 `uq_provider_provider_id`)
- [ ] `users.deleted_at` (TIMESTAMPTZ NULL)
- [ ] `recipes.embedding` (`vector(1536)` 타입)
- [ ] `recipes.author_id` 가 `BIGINT REFERENCES users(user_id) ON DELETE SET NULL`
- [ ] `scraps`, `likes` 의 FK 가 `ON DELETE CASCADE`
- [ ] `session_logs.selected_recipe_id` FK 에 `ON DELETE` 지정 없음 (NO ACTION). 이 경우 API 서버가 애플리케이션 단에서 NULL 처리 후 삭제해야 한다 — §8 에서 검증.

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

## 6. 레시피 작성 → `recipes` + `recipe_stats` 동일 트랜잭션 INSERT

```bash
curl -s -X POST http://localhost:8080/api/recipes \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{
    "title":"김치볶음밥",
    "fullContent":"1. 팬에 기름\n2. 김치 볶기",
    "ingredients":[{"name":"김치","amount":"200g"},{"name":"밥","amount":"1공기"}]
  }' | jq
```

- [ ] 응답 `{"success":true,"data":{"recipeId":<n>,"status":"PENDING"}}`

```sql
SELECT r.recipe_id, r.source, r.status, r.author_id, r.ingredients,
       s.likes_count, s.scrap_count
FROM recipes r LEFT JOIN recipe_stats s USING (recipe_id);
```

- [ ] `source = 'USER'`, `status = 'PENDING'`
- [ ] `ingredients` 가 JSONB 배열로 저장됨 (psql 에서 바로 보임)
- [ ] `recipe_stats` 도 같이 INSERT 되어 `likes_count = 0, scrap_count = 0`

**실패 케이스**: 서비스가 예외 던지면 두 INSERT 모두 롤백되는지 확인. 예: 엔드포인트 호출 직전에 `application.yml` 의 `aws.s3.public-url-prefix` 를 `https://invalid/` 로 바꾸고 외부 URL 을 보내면 400. 이후 DB 재조회 시 row 0 이어야 함.

- [ ] 롤백 케이스: `recipes` / `recipe_stats` 모두 row 없음

---

## 7. 목록·단건·내 레시피 권한

PENDING 상태에서:

```bash
# 비로그인으로 목록 조회 → PENDING 은 안 나와야 함
curl -s http://localhost:8080/api/recipes | jq '.data.items | length'
# 0 이어야 함 (APPROVED 만 노출)

# 비로그인으로 PENDING 단건 → 403
curl -si http://localhost:8080/api/recipes/1 | head -1
# HTTP/1.1 403 ...

# 본인이 단건 조회 → 200
curl -si -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/recipes/1 | head -1

# 내 레시피 목록 (비로그인) → 401
curl -si http://localhost:8080/api/recipes/my | head -1

# 내 레시피 목록 (본인) → PENDING 포함
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/recipes/my | jq
```

- [ ] 위 4가지 권한 분기가 모두 기대대로 동작

수동 승인으로 목록 등장 확인:
```sql
UPDATE recipes SET status = 'APPROVED' WHERE recipe_id = 1;
```
```bash
curl -s http://localhost:8080/api/recipes | jq '.data.totalElements'
# 1 이어야 함
```

- [ ] APPROVED 승인 후 공개 목록에 등장

---

## 8. 레시피 삭제 — FK CASCADE + session_logs 선행 NULL 처리

세팅: scraps·likes·session_logs 가 참조하도록 수동 INSERT.

```sql
INSERT INTO scraps (user_id, recipe_id) VALUES (1, 1);
INSERT INTO likes  (user_id, recipe_id) VALUES (1, 1);

INSERT INTO chat_rooms (room_id, user_id) VALUES ('room-a', 1);
INSERT INTO session_logs (session_id, room_id, user_id, selected_recipe_id)
VALUES ('sess-a', 'room-a', 1, 1);
```

삭제 호출:
```bash
curl -si -X DELETE -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/recipes/1
# HTTP/1.1 204
```

검증:
```sql
SELECT * FROM recipes      WHERE recipe_id = 1;  -- 0 rows
SELECT * FROM recipe_stats WHERE recipe_id = 1;  -- 0 rows (CASCADE)
SELECT * FROM scraps       WHERE recipe_id = 1;  -- 0 rows (CASCADE)
SELECT * FROM likes        WHERE recipe_id = 1;  -- 0 rows (CASCADE)
SELECT selected_recipe_id FROM session_logs WHERE session_id = 'sess-a';
-- NULL 이어야 함 (애플리케이션이 선행 UPDATE 수행)
```

- [ ] `recipes` 삭제됨
- [ ] `recipe_stats`, `scraps`, `likes` 가 CASCADE 로 같이 삭제
- [ ] `session_logs.selected_recipe_id` 는 NULL (row 는 남음)

타인 삭제 시도:
- 다른 유저로 로그인한 토큰으로 같은 엔드포인트 호출 → 403 `FORBIDDEN`
- [ ] 권한 오류가 의도대로 떨어짐

---

## 9. 탈퇴 사용자 닉네임 치환 (수동)

Step 4 구현 전까지는 탈퇴 API 가 없으므로 DB 에서 직접 조작해 테스트.

```sql
-- 레시피 하나 APPROVE 해서 목록에 띄워둔 상태 기준
UPDATE users
SET nickname = '탈퇴한 사용자_1',
    deleted_at = NOW()
WHERE user_id = 1;
```

```bash
curl -s http://localhost:8080/api/recipes | jq '.data.items[0].authorNickname'
# "탈퇴한 사용자"   <- 꼬리표 제거된 형태로 보여야 함
```

- [ ] `authorNickname` 이 `"탈퇴한 사용자"` (꼬리표 없음)

원복 (다음 검증 진행을 위해):
```sql
UPDATE users SET nickname = 'tester', deleted_at = NULL WHERE user_id = 1;
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
