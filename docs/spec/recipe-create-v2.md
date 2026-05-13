# SPEC-20260502-02: 레시피 작성 v2 (사용자 제출 → pending_recipes)

## 0. 메타

| 항목 | 값 |
|---|---|
| 명세서 ID | `SPEC-20260502-02` |
| 도메인 | Recipe |
| 기능명 | 로그인 사용자의 레시피 제출 (관리자 승인 대기 큐) |
| 작성자 | API 서버 담당자 |
| 작성일 | 2026-05-02 |
| 우선순위 | P0 |
| 관련 명세서 | `SPEC-20260502-03` (조회 v2), `SPEC-20260502-04` (삭제 v2), `SPEC-20260422-05` (업로드), `SPEC-20260502-01` (AI 서버 contract) |
| 대체 대상 | [`SPEC-20260422-02`](recipe-create.md) — 보존되며 변경 사유는 [`docs/changes/SPEC-20260422-02-CL01.md`](../changes/SPEC-20260422-02-CL01.md) + [`docs/changes/V4-integration-resolved.md`](../changes/V4-integration-resolved.md) |

---

## 1. 목적 (Why)

V4 통합 (2026-05-02) 으로 데이터 모델이 둘로 분리됐다:
- `recipes` — **승인된** 레시피만. AI 서버 `RecipeResponse` 와 1:1 매핑되며 RAG 검색의 인덱싱 대상.
- `pending_recipes` — 사용자가 제출한 레시피. 관리자 승인 시 `recipes` 로 이동.

따라서 사용자 작성 엔드포인트는 더 이상 `recipes` 를 직접 INSERT 하지 않고 `pending_recipes` 에 INSERT 한다. 또한 AI contract 정합을 위해 입력 필드가 11개 늘었다(대부분 optional).

---

## 2. 사용자 시나리오

```
As a   로그인 사용자
I want 내가 만든 레시피를 제출하여
So that 관리자 승인을 거친 뒤 다른 사용자에게 공유할 수 있다
```

---

## 3. API 스펙

### 3.1 엔드포인트

| Method | Path | 인증 | 설명 |
|---|---|---|---|
| `POST` | `/api/recipes` | 필수 (USER) | 레시피 제출 — `pending_recipes` 에 INSERT |

> 엔드포인트 경로는 v1 과 동일하지만, 의미가 "DB 의 recipes INSERT" 에서 "관리자 승인 큐 등록" 으로 바뀌었다.

### 3.2 Request

**Body**
```json
{
  "title":          "string, 1~255자, 필수",
  "description":    "string, 0~1000자, 선택",
  "content":        "string, 1~10000자, 필수 — 자유 형식 본문",
  "ingredients":    [{"name":"string","amount":"string","unit":"string","type":"string","note":"string|null"}],
  "ingredientsRaw": "string, 0~2000자, 선택 — '김치 200g, 두부 1모' 같은 원문",
  "instructions":   ["string, 각 1~500자, 최대 50개"],
  "servings":       "number(>=0), 선택 — 인분",
  "cookingTime":    "integer(>0), 선택 — 분",
  "calories":       "integer(>=0), 선택 — kcal",
  "difficulty":     "easy|normal|hard, 선택",
  "category":       ["string, 각 1~50자, 최대 20개"],
  "tags":           ["string, 각 1~50자, 최대 20개"],
  "tips":           ["string, 각 1~500자, 최대 20개"],
  "videoUrl":       "string, 0~512자, 선택 — YouTube 등",
  "imageUrl":       "string, 0~512자, 선택 — 사전 업로드된 S3 URL"
}
```

예시 (최소):
```json
{
  "title":   "김치볶음밥",
  "content": "1. 팬에 기름을 두른다\n2. 김치를 볶는다\n3. ..."
}
```

예시 (풍부):
```json
{
  "title":          "김치볶음밥",
  "description":    "10분만에 만드는 든든한 한 그릇.",
  "content":        "1. 팬에 기름을 두른다 ...",
  "ingredients":    [
    {"name":"김치", "amount":"200", "unit":"g",   "type":"메인",   "note":"잘 익은 것"},
    {"name":"밥",   "amount":"1",   "unit":"공기","type":"메인",   "note":null}
  ],
  "ingredientsRaw": "김치 200g, 밥 1공기",
  "instructions":   ["팬에 기름을 두른다", "김치를 볶는다", "밥을 넣고 볶는다"],
  "servings":       1.0,
  "cookingTime":    10,
  "difficulty":     "easy",
  "category":       ["한식","볶음"],
  "tags":           ["간단","한그릇"],
  "tips":           ["김치는 충분히 익은 것이 좋다"],
  "imageUrl":       "https://naengo-prod.s3.ap-northeast-2.amazonaws.com/recipes/abc.jpg"
}
```

### 3.3 Response

성공 (HTTP 201):
```json
{
  "success": true,
  "data": {
    "pendingRecipeId": 42,
    "status": "PENDING"
  }
}
```

실패:

| HTTP | code | 언제 |
|---|---|---|
| 400 | `INVALID_INPUT` | 필수 필드 누락 / 길이/범위 초과 / `difficulty` enum 외 / `imageUrl` 이 우리 S3 프리픽스 외 |
| 401 | `UNAUTHORIZED` | 토큰 / 쿠키 없음·만료 |
| 403 | `USER_BLOCKED` | 차단된 사용자 (Step 4 이후) |

---

## 4. 비즈니스 규칙

1. INSERT 대상은 **`pending_recipes`** 테이블. `recipes` 는 절대 건드리지 않는다 (관리자 승인 시 별도 트랜잭션이 이동).
2. `status` 는 항상 `'PENDING'` 으로 고정. 클라이언트가 보내도 무시.
3. `user_id` 는 JWT 의 `sub` (또는 쿠키의 동등 정보) 에서 추출. Body 로 받지 않는다 (DTO 에 필드 없음).
4. `content` 는 필수 — 사용자가 자유 형식으로 작성한 본문. 나머지 메타데이터는 모두 선택.
5. `ingredients` element schema 는 `{name, amount, unit, type, note}` (AI `IngredientItem` 정합). v1 의 `{name, amount}` 는 폐기.
6. `imageUrl` 가 있으면 우리 S3 프리픽스(`aws.s3.public-url-prefix`) 로 시작해야 함. 빈 값이거나 prefix 미설정이면 검증 스킵.
7. `videoUrl` 도 형식 검증 없이 길이만 체크 (운영 시 도메인 화이트리스트 도입 검토).
8. `recipe_stats` row 는 본 트랜잭션에서 만들지 않는다. **승인되어 `recipes` 로 이동하는 시점** 에 DB 트리거 `trigger_recipe_stats_create` 가 자동 생성.
9. 같은 사용자의 동일 제목 중복 제출은 **허용** (사용자가 같은 레시피의 다른 버전을 제출할 수 있음).
10. 작성 후 어떤 경우에도 **수정 불가** (정책). 재제출만 가능. 본인이 `pending_recipes` row 를 삭제(SPEC-20260502-04) 한 뒤 다시 제출.

---

## 5. 데이터 모델 영향

### 5.1 변경되는 테이블

| 테이블 | 변경 | 설명 |
|---|---|---|
| `pending_recipes` | INSERT | `user_id = JWT sub`, `status = 'PENDING'`, `is_active = true` |

### 5.2 마이그레이션 필요 여부

- [x] 스키마 변경 없음 (V1 에 이미 포함).

### 5.3 트랜잭션 경계

`@Transactional`: `pending_recipes` INSERT 단일 작업. 추가 동기화 작업 없음.

---

## 6. 외부 의존성

- AI 서버: **호출하지 않음**. 임베딩·승인은 Step 6/7 에서 별도.
- S3: 직접 호출하지 않음. 클라이언트가 업로드 완료한 URL 만 수신.

---

## 7. 권한·보안

- 인증: 필수 (USER role).
- 인증 통로: `Authorization: Bearer <JWT>` 헤더 또는 **HttpOnly Cookie** (2026-05-02 합의, [`api-server-tasks.md §3 인증 흐름 갱신`](../api-server-tasks.md))
- `@Valid` + 제약: §3.2 의 길이/범위 그대로
- 로그에 `content` 원문 남기지 않음 (길이만)

---

## 8. 성능·확장 고려

- 호출 빈도: 낮음~중간 (작성은 조회보다 드물다)
- 페이징 불필요 (단건 생성)
- 인덱스: `idx_pending_recipes_user_id` / `idx_pending_recipes_status` (V1 에 존재)

---

## 9. 테스트 케이스

- [ ] 정상 제출 → 201, `pendingRecipeId` 반환, `status="PENDING"`
- [ ] 토큰 없이 요청 → 401
- [ ] `title` / `content` 누락 → 400 `INVALID_INPUT`
- [ ] `difficulty="impossible"` 같은 enum 외 → 400
- [ ] `imageUrl` 이 외부 도메인 → 400
- [ ] Body 에 `status="APPROVED"` 보내도 저장된 값은 PENDING (DTO 에 필드 없음)
- [ ] Body 에 `userId` 보내도 JWT 의 `sub` 로 저장 (DTO 에 필드 없음)
- [ ] `ingredients` 에 v1 schema (`{name, amount}` 만) 보내면 400 (`unit`/`type` 누락)
- [ ] 최소 필드 (title + content) 만 보내도 201 (다른 메타는 NULL 허용)

---

## 10. 결정 사항

- AI contract 정합을 위해 `recipes` 는 풍부한 메타가 NOT NULL 이지만, `pending_recipes` 는 모두 nullable. 관리자가 승인 단계에서 부족한 필드를 보정한다 (Step 6).
- 입력 schema 가 풍부해진 결과, 모바일 클라이언트는 단계별 폼 (제목+본문 → 재료 → 메타) 으로 UX 분리 가능. 본 명세는 한 번에 전체 본문을 받는 형태.
- v1 의 `recipe_stats` 동시 INSERT 는 폐기. DB 트리거가 책임.

---

## 11. 범위 밖

- 레시피 수정 (정책상 영구 불가)
- 임시저장 (사용자가 제출하지 않은 초안)
- 임베딩 자동 생성 (관리자 승인 + AI 서버 처리 — Step 6/7)
- `ingredients` 자연어 자동 추출 (AI 서버 영역)
- 이미지 업로드 (별도 SPEC-20260422-05)
