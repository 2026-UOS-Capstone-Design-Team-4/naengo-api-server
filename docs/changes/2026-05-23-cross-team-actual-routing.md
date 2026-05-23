# 크로스팀 실 라우팅 분석 — front 가 명시한 분담 + 우리 endpoint owner 확정

## 0. 메타

| 항목 | 값 |
|---|---|
| 변경 이력 ID | `2026-05-23-cross-team-actual-routing` |
| 트리거 | docs/api-3.json 폐기 + docs/api-ver.5.json 신규 (AI 팀) + naengo-front / naengo-admin 최신화 |
| 대상 | 우리 controller 인벤토리 / cross-team contract / 폐기 후보 식별 |
| 작성자 | API 서버 담당자 |
| 작성일 | 2026-05-23 |

---

## 1. 분석 범위

다음 3 repo 최신본 직접 스캔:

- `naengo-ai/app/api/v1/endpoints/**` — FastAPI router 실 구현 확인
- `naengo-admin/src/api/client.ts` 외 — React baseURL + 호출 endpoint 확인
- `naengo-front/lib/services/naengo_api_service.dart` — Flutter `baseUrl` / `springBase` 분리 + 모든 `Uri.parse` 매핑

→ spec 만이 아닌 **실 동작 라우팅** 확정.

---

## 2. 결정적 발견

### 2.1 AI 측 (FastAPI)

- 28개 endpoint **모두 실 코드로 구현** (chat/recipes/users/user-recipes/admin/guest_chat)
- SQLAlchemy 로 우리와 **같은 RDS (DBv5) 직접 접근**
- Auth: `app/api/v1/deps.py::get_current_user_id` 가 **`TEMP_USER_ID` env placeholder** — JWT 미구현
- 시스템 호출용 `X-Internal-Secret` 헤더 인증 채널 별도 존재

### 2.2 naengo-admin (React)

- `src/api/client.ts` baseURL: `http://localhost:8000` (= AI 서버 FastAPI)
- 우리 서버 (8080) 호출 **0**
- 사용자 block/unblock UI **자체 미구현** → 우리 `AdminUserController` 호출자 없음

### 2.3 naengo-front (Flutter) — 핵심

`lib/services/naengo_api_service.dart`:

```dart
static const String baseUrl = String.fromEnvironment(
  'NAENGO_API_BASE',
  defaultValue: 'http://43.201.62.254:8000',          // AI 서버
);

static const String _springBaseEnv = String.fromEnvironment(
  'NAENGO_SPRING_BASE',
  defaultValue: '',
);
static String get springBase =>
    _springBaseEnv.isNotEmpty
      ? _springBaseEnv
      : 'http://naengo-api-server-alb-176175450.ap-northeast-2.elb.amazonaws.com';   // 우리 ALB DNS
```

**front 가 두 서버 분리 호출을 명시적으로 채택.** 우리 ALB DNS 가 `springBase` default 값으로 박혀 있음.

---

## 3. Front 실 호출 분담 표

### 우리 서버 호출 (`springBase`)

| Front line | Method | Path | 우리 controller |
|---|---|---|---|
| L77 | POST | `/auth/social/kakao` | `AuthController.kakaoLogin` |
| L93 | POST | `/auth/logout` | `AuthController.logout` |
| L290 | GET | `/users/me/scraps` | `ScrapController.listMine` |
| L357 | POST (**JSON**) | `/user-recipes` | `UserRecipeController.create` |
| L373 | GET | `/user-recipes` | `UserRecipeController.listMine` |
| L384 | DELETE | `/user-recipes/{id}` | `UserRecipeController.delete` |
| L395 | GET | `/users/me` | `UserMeController.getMe` |
| L407 | PATCH | `/users/me` | `UserMeController.updateMe` |
| L424 | GET | `/users/me/profile` | `UserMeController.getProfile` |
| L439 | PATCH | `/users/me/profile` | `UserMeController.updateProfile` |

### AI 서버 호출 (`baseUrl`)

| Front line | Method | Path | 용도 |
|---|---|---|---|
| L116, L152, L173, L194 | GET/POST/DELETE | `/chat/rooms*` | 채팅방 목록/메시지/삭제 |
| L127 | POST | `/chat/rooms/{id}` | 기존 방 SSE |
| L250, L270 | GET | `/recipes`, `/recipes/{id}` | 레시피 목록/상세 |
| L334 | POST/DELETE | `/recipes/{id}/{likes,scraps}` | 좋아요/스크랩 토글 |

---

## 4. 결정 사항

### 4.1 운영 모델 = D+ (front 명시 분담 그대로)

| 도메인 | 정본 |
|---|---|
| auth 발급 / users 정보 / user-recipes 전체 / users/me/scraps GET | 우리 (Spring) |
| chat 전체 / recipes read / like·scrap toggle / 기타 admin | AI (FastAPI) |

### 4.2 C4 결판 — front 가 PATCH 채택

`/users/me/profile`:
- 우리 PATCH (전체 교체) — front L439 호출
- AI POST(append)/DELETE(remove) — front 미호출 (AI agent 내부용 dead path)

→ **C4 closed. front 는 PATCH 사용. AI 의 POST/DELETE 는 외부 호출자 0** (내부에서만 사용 시 유지).

### 4.3 C5 — JWT 적용 (AI 측 진행 중, 2026-05-23 시점)

front 가 이미 `Authorization: Bearer` 첨부 코드 박혀있음. AI 가 JWT 검증으로 `TEMP_USER_ID` 폐기해야 멀티유저 작동. AI 팀이 현재 구현 중 — 별도 진행.

### 4.4 C7 — AI 의 `/users/me`, `/users/me/profile` 폐기 (✅ 결정 2026-05-23)

| 사유 | 내용 |
|---|---|
| 책임 분리 | 사용자 도메인 owner = 우리 (auth + JWT 발급자). AI 측 endpoint 가 dead path 가 됨 |
| 충돌 회피 | front 가 path 만 보고 잘못 라우팅할 가능성 사전 차단 |
| spec/코드 정합 | api-ver.5.json 갱신 시 해당 path 제거 |

front 호출 검증 (재확인):
- L395 `GET /users/me` → `$springBase` (우리)
- L407 `PATCH /users/me` → `$springBase` (우리)
- L424 `GET /users/me/profile` → `$springBase` (우리)
- L439 `PATCH /users/me/profile` → `$springBase` (우리)

**액션:** AI 팀에 폐기 요청 별도 채널 전달 완료. 본 결정으로 owner 행 확정.

### 4.5 C6 — front fallback `pending_recipe_id` 제거 협의 (보류)

#### 위치 + 코드

`naengo-front/lib/services/naengo_api_service.dart` L368:
```dart
return json['user_recipe_id'] as int? ?? json['pending_recipe_id'] as int;
```

#### 배경

| 시점 | 우리 응답 키 | front 처리 |
|---|---|---|
| 옵션 A 이전 (~2026-05-21) | `pending_recipe_id` (BIGSERIAL Long) | `pending_recipe_id` 로 읽음 |
| 옵션 A 이후 | `user_recipe_id` (DBv5 SERIAL Integer) | `user_recipe_id` 먼저, 없으면 fallback |

front 가 양 버전 동시 호환을 위해 깔아둔 안전 장치. 현재 우리는 `user_recipe_id` 만 응답 → fallback 은 dead branch.

#### 리스크 / 의미

| 항목 | 평가 |
|---|---|
| 동작 영향 | 없음 (dead branch) |
| 코드 위생 | front 코드에 dead code 잔존 — 리팩토링 부담 |
| 잘못된 호환 부담 | 우리가 향후 옛 key 를 다시 만들 수도 있다는 잘못된 인상 |
| 의사결정 시급도 | 낮음 |

#### 권장 조치

- **우리 측 작업: 0**
- front 팀 1줄 협의: "옵션 A 운영 1~2주 안정화 후 fallback (`?? json['pending_recipe_id']`) 제거 부탁드려요."
- 제거 시점 = 운영에서 옛 키 응답이 0건임을 확인 후 (CloudWatch logs metric filter)

### 4.6 C8 — AI `/user-recipes` POST multipart owner 결정 (보류)

#### AI 측 정의 (api-ver.5.json)

```
POST /api/v1/user-recipes
Content-Type: multipart/form-data
| payload      | stringified JSON | ✓ |
| main_image   | UploadFile       |   |
| step_images  | UploadFile[]     |   |
```

→ AI 가 이미지를 직접 받아 (S3 등) 업로드하는 흐름.

#### 우리 측 정의

`UserRecipeController.create`:
```java
public ResponseEntity<UserRecipeResponse> create(
    @Valid @RequestBody UserRecipeCreateRequest request)
```

→ **JSON only**. 이미지는 `image_url` String 필드만 (URL 받음).

#### Front 실 동작 (조사 결과)

- L361: `body: jsonEncode(body)` — JSON 으로 우리에게 전송
- `MultipartFile`, `MultipartRequest`, `files.add` 사용 **0건** (full lib/ 스캔)
- `recipe_service.dart` 안에 `'image_url': null` 하드코딩 — **이미지 업로드 기능 자체 미구현**
- 모델 `recipe.dart` 는 `image_url` String 만 받음 (URL 기반)

#### 의미

| 사실 | 결론 |
|---|---|
| AI multipart endpoint 호출자 | **0** (front 가 JSON 으로 우리 호출) |
| 우리 JSON endpoint 호출자 | front L357 ✅ |
| 이미지 업로드 실 흐름 | **양쪽 모두 dead** — front 가 image 자체를 안 보냄 |
| 진짜 이미지 업로드 owner | **미정** — 아직 구현 시점 안 옴 |

#### 권장 조치

- **현 시점 폐기 협의는 보류 권장** — AI multipart 가 dead 이지만, 향후 이미지 업로드의 정본이 될 후보. 지금 폐기하면 그때 다시 만들어야 함.
- 협의 안건 명시: "이미지 업로드 owner 를 우리/AI/클라이언트 중 누구로 할지 front 화면 구현 직전에 결정."
- 결정 후보:
  - **(가) 우리 (Spring multipart + S3 SDK)** — 인증 흐름과 일관성, 권한 검증 쉬움
  - **(나) AI (FastAPI UploadFile)** — 이미 코드 존재. 추가 작업 0
  - **(다) 클라이언트 → S3 presigned URL 직접** — 서버 부하 0, 보안 설계 필요
- 우리 측 잠정 작업: **0**

### 4.7 폐기 검토 6개 controller — 정밀 리스크 분석

전제: 모든 후보 controller 의 외부 호출자(front/admin) **0건 확인됨**. 다음은 내부/우회 의존성 검사 결과.

#### 공통 사실

- 6개 Service 모두 자기 Controller 만 의존 (cross-domain 호출 없음)
- **Repository 들은 `UserMeService.withdraw()` 에서 직접 호출** → Repository 유지 필수:
  ```java
  likeRepository.deleteAllByUserId(userId);
  scrapRepository.deleteAllByUserId(userId);
  userRecipeRepository.deleteAllByUserId(userId);
  chatRoomRepository.deactivateAllByUserId(userId);
  ```
- `RecipeListMapper` 가 ChatService/ScrapService/AdminRecipeService 공유 — Chat/Recipe 폐기 시 import 정리

#### 정밀 평가

| Controller | 외부 호출 | 내부 호출 | Repository | 즉시 리스크 | 미래 리스크 |
|---|---|---|---|---|---|
| `RecipeController` (GET /recipes·{id}) | 0 | 0 | 유지 (다른 곳에서 사용) | 🟢 0 | 🟢 0 |
| `LikeController` (POST·DELETE) | 0 | 0 | 유지 (withdraw 호출) | 🟢 0 | 🟢 0 |
| `ScrapController` POST·DELETE (GET listMine 유지) | 0 | 0 | 유지 (withdraw 호출) | 🟡 0.5 부분 폐기 | 🟢 0 |
| `ChatController` (GET·DELETE) | 0 | 0 | 유지 (withdraw 호출) | 🟡 0.5 | 🟡 chat PII 합의 시 service 재생성 가능 |
| `AdminRecipeController` (admin/user-recipes) | 0 | 0 | 유지 | 🟡 0.5 | 🟡 admin UI 라우팅 변경 시 재생성 |
| `AdminRecipeLookupController` (?video_url) | 0 | 0 | 유지 | 🟢 0 | 🟢 0 |

#### "아무런 리스크가 없는가?" 직답

**기능 동작 측면: 100% 없음** (호출자 0 + 데이터 흐름 무관).

**소프트 리스크 (모두 PR 1개로 처리 가능):**
1. 테스트 / e2e 스크립트 동반 정리 안 하면 CI 깨짐 — 폐기 PR 에 포함시키면 해결
2. chat PII 스크럽이 우리 책임으로 오면 ChatController 재생성
3. admin UI 가 우리 라우팅으로 이전 시 AdminRecipeController 재생성
4. 이미지 업로드 owner 가 우리로 결정되면 UserRecipeController multipart 추가 (C8 의 연장)

→ **폐기 = 일시적 0 리스크 + 미래 시점에 복원 필요할 낮은 확률.** 운영 부담 감소 이점이 더 큼.

#### 폐기 PR 권고 순서

| # | PR | 시점 |
|---|---|---|
| 1 | `AdminRecipeLookupController` (단일 메서드, 리스크 0) | 운영 1주 안정화 후 |
| 2 | `RecipeController`, `LikeController`, `ScrapController` 부분 (메서드 단위) | 1차 후 |
| 3 | `ChatController` / `AdminRecipeController` | chat PII / admin 라우팅 합의 후 |

#### 폐기 시 동반 작업

- 통합테스트: `RecipeFlowIntegrationTest`(6), `ProfileChatIntegrationTest`(3 일부) 삭제 또는 잔존 endpoint 만으로 축소
- e2e 스크립트 (`scripts/e2e-smoke-prod.sh`): 33/33 → ~20개로 축소
- `auth-user-api.md`: 폐기 endpoint 절 제거
- Repository / Entity / DTO 는 유지 (다른 흐름이 사용)

---

## 5. 우리 폴더 endpoint 현황 — 최종 인벤토리

### 🟩 활성 유지 (front 가 실 호출)

| Controller | endpoint |
|---|---|
| `AuthController` | `POST /auth/social/kakao`, `POST /auth/logout` |
| `UserMeController` | `GET /me`, `PATCH /me`, `GET /me/profile`, `PATCH /me/profile` |
| `UserRecipeController` | `POST`, `GET`, `DELETE /{id}` |
| `ScrapController` | `GET /users/me/scraps` (`listMine` 만) |
| JWT 발급/검증 | `JwtTokenProvider`, 보안 필터 전체 |

### 🟨 잠재 유지 (호출자 0, front UI 추가 시 활성)

| Controller | endpoint | 조건 |
|---|---|---|
| `AuthController` | `POST /auth/signup`, `POST /auth/login` | front 가 LOCAL 가입/로그인 화면 추가 시 (현재 `UnimplementedError`) |
| `UserMeController` | `POST /me/password` | 비밀번호 변경 화면 |
| `UserMeController` | `DELETE /me` | 탈퇴 화면 |
| `UserMeController` | `GET·PATCH /me/preferences` | 확장 선호도 화면 |
| `AdminUserController` | `POST /admin/users/{id}/{block,unblock}` | admin UI block 화면 |

### 🟥 폐기 검토 (호출자 0 + AI 가 정본, 우리 컨트롤러 dead)

| Controller | path | 폐기 근거 |
|---|---|---|
| `RecipeController` | `GET /recipes`, `GET /recipes/{id}` | front L250, L270 → AI 8000 호출 |
| `LikeController` | `POST·DELETE /recipes/{id}/likes` | front L334 → AI |
| `ScrapController` 일부 | `POST·DELETE /recipes/{id}/scraps` | front L334 → AI (단 `GET /users/me/scraps` 는 유지) |
| `ChatController` | `GET /rooms`, `GET /rooms/{id}`, `DELETE /rooms/{id}` | front → AI. 단 탈퇴 시 chat soft-delete 내부 호출이 있으면 service 로 빼서 유지 |
| `AdminRecipeController` | `/admin/user-recipes*` | admin UI → AI |
| `AdminRecipeLookupController` | `/admin/recipes?video_url=` | 어디서도 호출 없음 |

**폐기 시 부수 작업:**
- 통합테스트: `RecipeFlowIntegrationTest`(6), `ProfileChatIntegrationTest`(3) 정리
- e2e 스크립트 (`scripts/e2e-smoke-prod.sh`) 33/33 → 잔존 endpoint 만 (~20개)
- `auth-user-api.md` 폐기 endpoint 절 제거

> ⚠️ **폐기는 별도 PR + 운영 안정화 후 진행 권장.** 현재 호출자 0 이므로 살아있어도 위험 없음. 우선순위 낮음.

---

## 6. 후속 액션 (우선순위)

1. **C7 AI 팀 통보** — 별도 카톡/텔레 첨부용 .md 로 전달 (본 PR 외 로컬 파일)
2. **C5 JWT 적용 완료 대기** — AI 팀 진행 중
3. **front 팀 path 정정 협의 (C6)** — 옵션 A 후 fallback 제거 시점
4. **AI 팀 multipart 폐기 협의 (C8)** — front 가 JSON 만 보내므로 dead path
5. **운영 안정화 후 우리 controller 폐기 PR** — RecipeController, LikeController 등 6개 (호출자 0)
6. **docs/auth-user-api.md 갱신** — front 분담 표 + dead endpoint 표시
7. **docs/changes/2026-05-19-ai-admin-snapshot-delta.md 종결 표시** — 본 문서로 흡수

---

## 7. 참조

- 직전 분석 (api-3.json 기준): `docs/changes/2026-05-19-ai-admin-snapshot-delta.md`
- 옵션 A 계약 차이: `docs/changes/2026-05-21-option-a-contract-diff.md`
- chat 탈퇴 합의: `docs/changes/chat-withdrawal-ai-agreement.md`
- C3 JWT 핸드오버 (AI 팀): 로컬 파일 (git 미포함)
- C7 폐기 요청 (AI 팀): 로컬 파일 (git 미포함) — 본 문서와 함께 생성
