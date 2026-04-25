-- ============================================================
-- V2: 소셜 로그인 지원을 위한 users 테이블 스키마 변경
-- 적용 방법: psql -d naengo -f V2__add_social_login_fields.sql
-- ============================================================

-- 1. password_hash 컬럼을 nullable로 변경
--    (소셜 로그인 사용자는 비밀번호를 갖지 않음)
ALTER TABLE users
    ALTER COLUMN password_hash DROP NOT NULL;

-- 2. 소셜 로그인 제공자 컬럼 추가 (LOCAL / KAKAO / GOOGLE)
--    기존 사용자는 모두 LOCAL 처리
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS provider VARCHAR(20) NOT NULL DEFAULT 'LOCAL';

-- 3. 소셜 제공자가 발급한 사용자 고유 ID 컬럼 추가
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS provider_id VARCHAR(255);

-- 4. (provider, provider_id) 복합 유니크 제약 추가
--    동일 제공자에서 동일 ID가 중복 등록되는 것을 방지
ALTER TABLE users
    ADD CONSTRAINT uq_provider_provider_id UNIQUE (provider, provider_id);
