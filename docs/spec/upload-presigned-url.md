# SPEC-20260422-05: 이미지 업로드 presigned URL 발급

## 0. 메타

| 항목 | 값 |
|---|---|
| 명세서 ID | `SPEC-20260422-05` |
| 도메인 | Upload |
| 기능명 | S3 presigned PUT URL 발급 |
| 작성자 | API 서버 담당자 |
| 작성일 | 2026-04-22 |
| 우선순위 | P0 (단, 구현은 AWS S3 준비 후) |
| 관련 명세서 | `SPEC-20260422-02` |

> **구현 상태**: 본 명세는 작성 완료. **실제 코드 구현은 AWS S3 버킷 / IAM 이 팀원에 의해 생성된 이후에 진행** (§0 AWS 메모 참조).

---

## 1. 목적

레시피 이미지는 수 MB 에 달할 수 있다. API 서버 인스턴스를 경유하면 대역폭·메모리 부담이 크므로, **클라이언트 → S3 직접 업로드** 방식을 택한다. API 서버는 1회용 PUT URL 만 발급한다.

---

## 2. 사용자 시나리오

```
As a   로그인 사용자
I want 레시피 이미지를 업로드하고
So that 작성 시 해당 이미지를 첨부할 수 있다
```

---

## 3. API 스펙

### 3.1 엔드포인트

| Method | Path | 인증 | 설명 |
|---|---|---|---|
| `POST` | `/api/uploads/presigned-url` | 필수 (USER) | PUT 전용 presigned URL 발급 |

### 3.2 Request

**Body**
```json
{
  "type": "RECIPE",
  "contentType": "image/jpeg",
  "contentLength": 524288
}
```

- `type`: `"RECIPE"` 고정 (추후 `"PROFILE"` 등 확장).
- `contentType`: MIME. 허용 목록 외는 400.
- `contentLength`: bytes. `aws.s3.max-content-length` 초과 시 400.

### 3.3 Response

성공 (HTTP 200):
```json
{
  "success": true,
  "data": {
    "uploadUrl": "https://naengo-prod.s3.ap-northeast-2.amazonaws.com/recipes/xxxx.jpg?X-Amz-...",
    "publicUrl": "https://naengo-prod.s3.ap-northeast-2.amazonaws.com/recipes/xxxx.jpg",
    "objectKey": "recipes/xxxx.jpg",
    "expiresInSeconds": 300
  }
}
```

실패:

| HTTP | code | 언제 |
|---|---|---|
| 400 | `INVALID_INPUT` | contentType 화이트리스트 외 / contentLength 초과 / type 미지원 |
| 401 | `UNAUTHORIZED` | 토큰 없음 |
| 503 | `UPLOAD_NOT_CONFIGURED` | AWS S3 설정 미완 (버킷 env 미설정). 구현 완료 직후에만 일시적으로 관측됨. |

---

## 4. 비즈니스 규칙

1. `contentType` 화이트리스트: `image/jpeg`, `image/png`, `image/webp`.
2. `contentLength` 상한: 5 MB (`aws.s3.max-content-length = 5242880`).
3. `objectKey` 는 서버가 생성. 형식: `{prefix}/{yyyy/MM/dd}/{uuid}.{ext}`.
   - `prefix` 는 type 별: `RECIPE → recipes`.
   - 확장자는 contentType 에서 매핑 (jpeg → jpg, png → png, webp → webp).
4. presigned URL 유효 시간: **5분** (300초).
5. URL 에는 `Content-Type`, `Content-Length` 를 서명에 포함 → 클라이언트가 업로드 시 같은 값으로 PUT 해야 서명 일치.
6. 업로드 성공 검증은 API 서버가 별도로 하지 않는다. 레시피 작성 API (`SPEC-20260422-02`) 에서 `imageUrl` 프리픽스 체크만 한다. 업로드 실패 시 `imageUrl` 이 없는 상태로 작성됨.
7. 발급된 URL 사용 여부 추적하지 않음 (stateless).

---

## 5. 데이터 모델 영향

- 없음. DB 상태 변경 없음.

---

## 6. 외부 의존성

- AWS S3 (`PutObject`).
- SDK: `software.amazon.awssdk:s3`, `software.amazon.awssdk:s3-transfer-manager` 불필요, `s3` + 내장 presigner 로 충분.

---

## 7. 권한·보안

- 인증 필수. 비로그인 업로드 허용하지 않는다 (스팸·악성 파일 방지).
- 발급 시 rate limit 고려 (MVP 는 보류).
- presigned URL 의 서명은 `Content-Type` / `Content-Length` 에 종속 → 클라이언트가 임의 파일로 바꿔 치기 불가.
- 버킷 정책: `PutObject` 만 허용. `GetObject` 는 퍼블릭 ACL 또는 CloudFront 경유.

---

## 8. 성능·확장

- S3Presigner 는 싱글턴 Bean. 스레드 안전.
- 호출 빈도 낮음 (레시피 작성 전 1회).

---

## 9. 테스트 케이스

- [ ] 정상 발급 → 200, uploadUrl 에 `X-Amz-Signature` 포함, publicUrl 은 프리픽스 일치
- [ ] `contentType="image/gif"` → 400
- [ ] `contentLength = 10 MB` → 400
- [ ] 토큰 없음 → 401
- [ ] 버킷 env 미설정 상태에서 호출 → 503 `UPLOAD_NOT_CONFIGURED`
- [ ] 발급된 URL 로 실제 PUT (통합 테스트, AWS 연결 필요 — Step 8)

---

## 10. 결정 사항

- 업로드 완료 콜백(S3 event → API 서버)은 도입하지 않는다. 레시피 작성 API 에서 URL 유효성만 체크.
- 재료 분석 이미지는 본 엔드포인트를 쓰지 않는다 (AI 서버 직통, §0 Phase 0-4).
- LocalStack 사용은 로컬 테스트 옵션으로 열어두되 필수 아님.

---

## 11. 범위 밖

- 파일 바이러스 스캔
- 썸네일 자동 생성
- 원본 삭제/교체 API (레시피 수정 불가 정책과 연계 — 이미지 교체도 불가)
- 재료 분석 이미지 업로드 (AI 서버 영역)

---

## 12. 설정 키 (application.yml)

```yaml
aws:
  region: ${AWS_REGION:ap-northeast-2}
  s3:
    bucket: ${AWS_S3_BUCKET:}
    public-url-prefix: ${AWS_S3_PUBLIC_URL_PREFIX:}
    presigned-url-ttl-seconds: 300
    max-content-length: 5242880
    allowed-content-types: image/jpeg,image/png,image/webp
```

- `bucket` / `public-url-prefix` 가 비어있으면 `UploadController` 는 503 반환.
