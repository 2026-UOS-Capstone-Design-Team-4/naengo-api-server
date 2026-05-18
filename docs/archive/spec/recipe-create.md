# SPEC-20260422-02: 레시피 작성

## 0. 메타

| 항목 | 값 |
|---|---|
| 명세서 ID | `SPEC-20260422-02` |
| 도메인 | Recipe |
| 기능명 | 로그인 사용자의 레시피 작성 |
| 작성자 | API 서버 담당자 |
| 작성일 | 2026-04-22 |
| 우선순위 | P0 |
| 관련 명세서 | `SPEC-20260422-03` (읽기), `SPEC-20260422-04` (삭제), `SPEC-20260422-05` (업로드) |

---

## 1. 목적 (Why)

사용자가 자신만의 레시피를 게시판에 업로드할 수 있어야 한다. 서비스의 핵심 가치는 "기본 레시피 + 사용자 레시피"로 구성된 통합 레시피 DB 이므로, 사용자 업로드 경로가 없으면 서비스가 성립하지 않는다. 다만 품질 관리를 위해 **관리자 승인 전에는 일반 목록에 노출되지 않는다**.

---

## 2. 사용자 시나리오

```
As a   로그인 사용자
I want 내가 만든 레시피를 제목·재료·본문과 함께 업로드하고
So that 관리자 승인을 거친 뒤 다른 사용자에게 공유하고 싶다
```

---

## 3. API 스펙

### 3.1 엔드포인트

| Method | Path | 인증 | 설명 |
|---|---|---|---|
| `POST` | `/api/recipes` | 필수 (USER) | 레시피 작성 |

### 3.2 Request

**Body**
```json
{
  "title": "string, 1~255자, 필수",
  "fullContent": "string, 1~10000자, 필수",
  "imageUrl": "string, optional. 사전 업로드된 S3 URL (SPEC-20260422-05)",
  "ingredients": [
    {"name": "string, 1~100자, 필수", "amount": "string, 0~50자"}
  ]
}
```

예시:
```json
{
  "title": "김치볶음밥",
  "fullContent": "1. 팬에 기름을 두른다\n2. 김치를 볶는다\n...",
  "imageUrl": "https://naengo-prod.s3.ap-northeast-2.amazonaws.com/recipes/abc.jpg",
  "ingredients": [
    {"name": "김치", "amount": "200g"},
    {"name": "밥",   "amount": "1공기"}
  ]
}
```

### 3.3 Response

성공 (HTTP 201):
```json
{
  "success": true,
  "data": {
    "recipeId": 42,
    "status": "PENDING"
  }
}
```

실패:

| HTTP | code | 언제 |
|---|---|---|
| 400 | `INVALID_INPUT` | 필수 필드 누락 / 길이 초과 / ingredients 비어있음 |
| 401 | `UNAUTHORIZED` | 토큰 없음/만료 |
| 403 | `USER_BLOCKED` | 차단된 사용자 (추후 적용 — 현재는 Security 단에서 거부됨) |

---

## 4. 비즈니스 규칙

1. `source` 는 항상 `'USER'` 로 고정 (STANDARD 는 운영자 시드 데이터 전용).
2. `status` 는 항상 `'PENDING'` 으로 고정. 클라이언트가 전달해도 무시.
3. `authorId` 는 JWT `sub` 에서 꺼낸 user_id 를 사용. Body 로 받지 않는다. (DTO 에 필드 없음)
4. `embedding` 은 NULL 로 INSERT. 승인 시점에 AI 서버가 채움 (Step 6/7).
5. `ingredients` 는 최소 1개 이상. 빈 배열이면 400.
6. `imageUrl` 은 optional. 값이 있을 경우 **우리 S3 버킷 프리픽스로 시작해야 함** (외부 URL 차단). 구체 프리픽스는 `application.yml` 의 `aws.s3.public-url-prefix` 로 설정.
7. 레시피 생성과 동시에 같은 트랜잭션에서 `recipe_stats (recipe_id, 0, 0)` INSERT.
8. 같은 사용자의 동일 제목 중복 작성은 **허용** (중복 검사 없음).
9. 작성 후 어떤 경우에도 **수정 불가** (정책). 재작성만 가능.

---

## 5. 데이터 모델 영향

### 5.1 변경되는 테이블

| 테이블 | 변경 | 설명 |
|---|---|---|
| `recipes` | INSERT | `source='USER'`, `status='PENDING'`, `embedding=NULL`, `author_id=JWT sub` |
| `recipe_stats` | INSERT | `likes_count=0`, `scrap_count=0` |

### 5.2 마이그레이션 필요 여부

- [x] 스키마 변경 없음 (V1 에 이미 포함).

### 5.3 트랜잭션 경계

`@Transactional`: `recipes` INSERT + `recipe_stats` INSERT 는 한 트랜잭션.

---

## 6. 외부 의존성

- AI 서버: **호출하지 않음** (임베딩은 승인 시점 — Step 6/7).
- S3: 직접 호출하지 않음. 클라이언트가 업로드 완료한 URL 만 수신.

---

## 7. 권한·보안

- 인증: 필수 (USER role).
- `@Valid` + 제약:
  - `title`: `@NotBlank`, `@Size(max=255)`
  - `fullContent`: `@NotBlank`, `@Size(max=10000)`
  - `ingredients`: `@NotEmpty`, 원소 `@Valid`
  - `ingredients[*].name`: `@NotBlank`, `@Size(max=100)`
  - `ingredients[*].amount`: `@Size(max=50)` (null/빈문자 허용)
- 로그에 `fullContent` 원문 남기지 않음 (길이만).

---

## 8. 성능·확장 고려

- 예상 호출 빈도: 낮음~중간 (작성은 조회보다 드물다).
- 페이징 불필요 (단건 생성).
- 특별한 인덱스 추가 불필요 — V1 에 기본 인덱스 존재.

---

## 9. 테스트 케이스

- [ ] 정상 작성 → 201, `status="PENDING"`, recipe_stats 도 같이 INSERT
- [ ] 토큰 없이 요청 → 401
- [ ] `title` 누락 → 400 `INVALID_INPUT`
- [ ] `ingredients` 빈 배열 → 400
- [ ] `imageUrl` 이 외부 도메인 → 400
- [ ] Body 에 `status="APPROVED"` 보내도 저장된 값은 PENDING
- [ ] Body 에 `authorId` 보내도 JWT 의 user_id 로 저장

---

## 10. 결정 사항

- 이미지는 Body 에 base64 로 받지 않는다 (대용량 바이너리 중계 금지). 반드시 SPEC-20260422-05 선행.
- PENDING 상태에서는 `recipe_stats` 카운터가 0 이지만 조회 가능 범위가 작성자·관리자로 제한되므로 실질적 노출 없음.

---

## 11. 범위 밖

- 레시피 수정 (정책상 영구 불가)
- 임시저장
- 임베딩 자동 생성 (승인 시점으로 이연)
- `ingredients` 를 자연어로 추출해 파싱 (AI 서버의 재료 분석 기능과 다른 맥락)
