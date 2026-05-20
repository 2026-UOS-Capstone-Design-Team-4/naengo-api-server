-- ============================================================
-- V5: users 에서 소셜 식별자(provider/provider_id) 분리 → social_accounts
--
-- 배경 / 결정 (2026-05-19):
--   팀원 요청 — 일반/소셜 분리. 한 사용자가 여러 소셜을 동시에 link 할
--   여지를 둔 1:N 정규화. LOCAL 가입자는 social_accounts 에 row 없음.
--   소셜 가입자는 1행. 미래에 같은 user_id 가 KAKAO + GOOGLE 등 다중
--   provider 를 가질 수 있음 (UNIQUE 가 (provider, provider_user_id) 와
--   (user_id, provider) 두 축으로 막아 중복만 차단).
--
-- 우리 V1/V2 에 들어있던 users.provider / users.provider_id 와
-- uq_provider_provider_id 제약은 본 마이그레이션에서 제거된다. LOCAL 이
-- 아닌 기존 row 가 있으면 social_accounts 로 손실 없이 이전한다.
-- ============================================================

CREATE TABLE social_accounts (
    social_account_id BIGSERIAL    PRIMARY KEY,
    user_id           BIGINT       NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    provider          VARCHAR(20)  NOT NULL CHECK (provider IN ('KAKAO')),     -- 신규 provider 추가 시 CHECK 확장
    provider_user_id  VARCHAR(255) NOT NULL,                                   -- 제공자가 발급한 사용자 고유 ID
    linked_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_social_provider_pid UNIQUE (provider, provider_user_id),    -- 동일 외부 계정 중복 link 차단
    CONSTRAINT uq_social_user_provider UNIQUE (user_id, provider)             -- 한 user 가 같은 provider 두 번 link 차단
);

CREATE INDEX idx_social_user_id ON social_accounts(user_id);

-- 기존 데이터 이전: provider != 'LOCAL' 인 행만
INSERT INTO social_accounts (user_id, provider, provider_user_id, linked_at)
SELECT user_id, provider, provider_id, created_at
  FROM users
 WHERE provider <> 'LOCAL' AND provider_id IS NOT NULL;

-- 구 제약 / 컬럼 제거
ALTER TABLE users DROP CONSTRAINT IF EXISTS uq_provider_provider_id;
ALTER TABLE users DROP COLUMN IF EXISTS provider_id;
ALTER TABLE users DROP COLUMN IF EXISTS provider;
