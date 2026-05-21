# 옵션 A 채택 후 외부 contract diff (2026-05-21)

> 운영 RDS 가 `DBv5` (DB 팀원이 SQL 로 직접 적용) 로 고정됨에 따라 우리 코드를
> 전면 align 한 옵션 A 완료. 본 문서는 그 결과로 **외부 API contract 가 바뀐
> 5건**을 정리한다. 운영 배포 후 본 contract 가 진실원본이며, front 가 본
> 서버를 호출할 때 이 명세를 따른다.
>
> 적용된 변경 코드: PR commits `f0105dc`(V1) / `2523ddf`(Phase2) / `c1d189b`(Phase3) /
> `24df57e`(Phase4-6) / `47aa83a`(Phase7).
> 통합테스트 30/30 PASS, 운영 배포 성공: `docs/deploy-status.md`.

---

## 한 줄 요약

| 카테고리 | 옛 (옵션 A 이전) | 새 (현재 운영) |
|---|---|---|
| 사용자 식별자 컬럼/필드 | `email` | **`username`** |
| 사용자 제출 레시피 URL | `/api/v1/pending-recipes` | **`/api/v1/user-recipes`** |
| 관리자 제출 레시피 URL | `/api/v1/admin/pending-recipes` | **`/api/v1/admin/user-recipes`** |
| 사용자 제출 레시피 PK 응답키 | `pending_recipe_id` | **`user_recipe_id`** |
| 소셜 link 테이블 (내부) | `social_accounts` | `user_identities` (내부 변경, 응답 외형 영향 없음) |
| PK 정수 폭 | `BIGINT` (Long) | `INTEGER` (number, JSON 응답 그대로) |
| `users.deleted_at` 컬럼 | 존재(V3) | **제거**. 탈퇴 표식은 `is_active=false` + nickname 꼬리표 단일화 |

---

## 1. 인증 / 사용자

### 1.1 자체 회원가입 — `POST /api/v1/auth/signup`

**Request (변경됨)**:
```json
{
  "username": "user@naengo.com",
  "password": "pw12345A",
  "nickname": "냉장고요리왕"
}
```
- **`username`** (NotBlank, length 3~255). 이메일 형식 강제 안 함(자유 식별자). 사용자가 평소 이메일을 username 으로 쓰는 패턴은 호환.
- `password` (영문자+숫자 포함, 8~64자) — 그대로
- `nickname` (2~20자, UNIQUE) — 그대로

**Response 201**:
```json
{
  "user_id": 4,
  "nickname": "냉장고요리왕",
  "role": "USER",
  "access_token": "eyJ..."
}
```
+ `Set-Cookie: NAENGO_AT=<jwt>; Path=/; Max-Age=86400; Secure; HttpOnly; SameSite=Lax`

### 1.2 자체 로그인 — `POST /api/v1/auth/login`

**Request (변경됨)**:
```json
{ "username": "user@naengo.com", "password": "pw12345A" }
```

응답 동일 형식.

### 1.3 소셜 로그인 — `POST /api/v1/auth/social/kakao`

**Request / Response 외형 그대로**. 내부 처리만 변경:
- `user_identities` 테이블에 link 저장 (옛 `social_accounts`)
- 신규 가입 시 `users.username` 에 카카오 이메일(또는 placeholder `kakao_<id>@social.naengo.com`) 저장

### 1.4 마이페이지 조회 — `GET /api/v1/users/me`

**Response (변경됨)**:
```json
{
  "user_id": 4,
  "username": "user@naengo.com",
  "nickname": "냉장고요리왕",
  "role": "USER",
  "provider": "LOCAL",
  "is_active": true,
  "created_at": "2026-05-21T19:34:00Z"
}
```
- `email` → **`username`**
- `deleted_at` 필드 사라짐 (DB 컬럼 폐기 — 탈퇴는 `is_active=false` 로만 판정)
- `provider`: `"LOCAL" | "KAKAO" | "GOOGLE" | "NAVER" | "APPLE"` (서비스 구현은 LOCAL/KAKAO 만)

### 1.5 비밀번호 변경 — `POST /api/v1/users/me/password`

**Request 동일**. 내부 가드만 변경: 옛 `provider != LOCAL` 검사 → **`passwordHash == null`** 검사 (의미 동일, 소셜 전용 가입자 거부).

### 1.6 회원 탈퇴 — `DELETE /api/v1/users/me`

응답·동작 동일 (`204 + Set-Cookie expire`). 내부 처리만 `users.deleted_at` 제거 → `is_active=false` 로 표식 단일화. 소셜 link(`user_identities`) 삭제 동시에.

---

## 2. 사용자 제출 레시피 (path + 응답 키 변경)

### 2.1 제출 — `POST /api/v1/user-recipes` ← *path 변경*

**Request body 외형 그대로**: `title`, `content`, `description`, `ingredients`, `ingredients_raw`, `instructions`, `servings`, `cooking_time`, `calories`, `difficulty`, `category`, `tags`, `tips`, `video_url`, `image_url`.

**Response 201 (필드 변경)**:
```json
{
  "user_recipe_id": 7,   // ← was "pending_recipe_id"
  "title": "...",
  "content": "...",
  "status": "PENDING",
  ...
}
```

### 2.2 본인 제출 목록 — `GET /api/v1/user-recipes` ← *path 변경*
### 2.3 본인 제출 단건 — `GET /api/v1/user-recipes/{id}` ← *path 변경*
### 2.4 본인 제출 삭제 — `DELETE /api/v1/user-recipes/{id}` ← *path 변경*

### 2.5 관리자 검토 목록 — `GET /api/v1/admin/user-recipes` ← *path 변경*
### 2.6 관리자 검토 단건 — `GET /api/v1/admin/user-recipes/{id}` ← *path 변경*
### 2.7 관리자 수정·승인 — `PATCH /api/v1/admin/user-recipes/{id}` ← *path 변경*
### 2.8 관리자 삭제 — `DELETE /api/v1/admin/user-recipes/{id}` ← *path 변경*

> 응답 외형 (UserRecipeResponse / AdminUserRecipeDetailResponse 등) 의 PK 필드명도 `pending_recipe_id` → `user_recipe_id` 로 일관 변경.

---

## 3. 변하지 않은 endpoint

| Endpoint | 메모 |
|---|---|
| `GET /` , `GET /health` | 그대로 (`{"status":"UP"}`) |
| `POST /api/v1/auth/logout` | 그대로 |
| `GET /api/v1/users/me/profile` | 그대로 (`{"user_input":[...]}`) |
| `PATCH /api/v1/users/me/profile` | 그대로 |
| `GET /api/v1/users/me/preferences` | 그대로 |
| `PATCH /api/v1/users/me/preferences` | 그대로 |
| `GET /api/v1/users/me/scraps` | 그대로 |
| `GET /api/v1/recipes` | 그대로 (cursor, items / next_cursor / has_next) |
| `GET /api/v1/recipes/{id}` | 그대로 |
| `POST /api/v1/recipes/{id}/likes`, `DELETE /api/v1/recipes/{id}/likes` | 그대로 |
| `POST /api/v1/recipes/{id}/scraps`, `DELETE /api/v1/recipes/{id}/scraps` | 그대로 |
| `GET /api/v1/admin/recipes`, `/api/v1/admin/recipes/{id}` | 그대로 |
| `POST /api/v1/admin/users/{userId}/block`, `/unblock` | 그대로 |
| `GET|POST /api/v1/chat/rooms`, `/rooms/{id}` (SSE) | 그대로 |
| `DELETE /api/v1/chat/rooms/{id}` | 그대로 |

---

## 4. JSON 의 number 형 변화 — 영향 없음

DB PK 가 `BIGINT(Long)` → `INTEGER` 로 좁아졌으나 JSON 응답에서 그냥 숫자(`"user_id": 4`)로 직렬화. JS Number 안전 범위(`2^53`) 안이라 클라이언트 영향 0. front/admin 의 TS `number` 타입 코드 그대로.

---

## 5. front 호환 체크리스트 (배포 후 front 가 우리 호출 전 점검)

- [ ] signup/login request body 의 `email` → **`username`** 로 변경
- [ ] `/api/v1/users/me` 응답 파싱에서 `email` → **`username`**
- [ ] 사용자 제출 레시피 경로 `/api/v1/pending-recipes` → **`/api/v1/user-recipes`**
- [ ] 응답 필드 `pending_recipe_id` → **`user_recipe_id`**
- [ ] (선택) `deleted_at` 처리 코드 있으면 제거 — 응답에 없음

위 5개만 정정하면 호환. 다른 endpoint·필드는 모두 동일.

---

## 6. 운영 진실원본 / 보조 참조

- **본 문서** = 옵션 A 후 contract 변경 단일 진실원본
- `docs/auth-user-api.md` = 인증·사용자 API 가이드 (옵션 A 정합으로 갱신됨, 동일 시점)
- `docs/api-3.json` = OpenAPI dump. 옵션 A 이전 스냅샷이라 5건 stale — 본 문서로 보정. 다음 정식 dump (api-4.json) 이 나올 때까지 보조 참조용.
- `docs/spec/*.md` = 도메인 명세. 다수가 옵션 A 이전이라 stale. 진실원본은 본 문서 + `auth-user-api.md` + 코드.
