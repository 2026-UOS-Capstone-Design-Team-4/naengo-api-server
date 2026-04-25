-- ============================================================
-- V1: 초기 스키마
-- 소셜 로그인 기능은 V2 에서 추가된다. 따라서 본 V1 은
-- 소셜 로그인 도입 이전 시점의 스키마를 기술한다.
-- (신규 환경에서는 V1 → V2 → V3 순으로 적용되어 최신 상태가 된다)
-- ============================================================

-- pgvector 확장 (RAG 검색용). AI 서버가 recipes.embedding 에 R/W.
CREATE EXTENSION IF NOT EXISTS vector;

-- ──────────────────────────────────────────────────────────
-- users
-- ──────────────────────────────────────────────────────────
CREATE TABLE users (
    user_id       BIGSERIAL    PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    nickname      VARCHAR(50)  NOT NULL UNIQUE,
    role          VARCHAR(20)  NOT NULL DEFAULT 'USER' CHECK (role IN ('USER', 'ADMIN')),
    is_blocked    BOOLEAN      NOT NULL DEFAULT FALSE,
    preferences   JSONB,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- ──────────────────────────────────────────────────────────
-- recipes (통합 레시피 DB: 기본 + 사용자 업로드)
-- ──────────────────────────────────────────────────────────
CREATE TABLE recipes (
    recipe_id    BIGSERIAL    PRIMARY KEY,
    title        VARCHAR(255) NOT NULL,
    full_content TEXT         NOT NULL,
    image_url    VARCHAR(512),
    source       VARCHAR(20)  NOT NULL DEFAULT 'STANDARD' CHECK (source IN ('STANDARD', 'USER')),
    author_id    BIGINT       REFERENCES users(user_id) ON DELETE SET NULL,
    status       VARCHAR(20)  NOT NULL DEFAULT 'PENDING' CHECK (status IN ('APPROVED', 'PENDING', 'REJECTED')),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    embedding    VECTOR(1536),  -- AI 서버가 관리자 승인 시점에 채움
    ingredients  JSONB
);

CREATE INDEX idx_recipes_status      ON recipes(status);
CREATE INDEX idx_recipes_author_id   ON recipes(author_id);
CREATE INDEX idx_recipes_created_at  ON recipes(created_at DESC);

-- ──────────────────────────────────────────────────────────
-- recipe_stats (좋아요/스크랩 카운트 캐시)
-- ──────────────────────────────────────────────────────────
CREATE TABLE recipe_stats (
    recipe_id   BIGINT  PRIMARY KEY REFERENCES recipes(recipe_id) ON DELETE CASCADE,
    likes_count INTEGER NOT NULL DEFAULT 0,
    scrap_count INTEGER NOT NULL DEFAULT 0
);

-- ──────────────────────────────────────────────────────────
-- scraps
-- ──────────────────────────────────────────────────────────
CREATE TABLE scraps (
    scrap_id   BIGSERIAL   PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    recipe_id  BIGINT      NOT NULL REFERENCES recipes(recipe_id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_scraps_user_recipe UNIQUE (user_id, recipe_id)
);

CREATE INDEX idx_scraps_user_id ON scraps(user_id);

-- ──────────────────────────────────────────────────────────
-- likes
-- ──────────────────────────────────────────────────────────
CREATE TABLE likes (
    like_id    BIGSERIAL   PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    recipe_id  BIGINT      NOT NULL REFERENCES recipes(recipe_id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_likes_user_recipe UNIQUE (user_id, recipe_id)
);

CREATE INDEX idx_likes_user_id ON likes(user_id);

-- ──────────────────────────────────────────────────────────
-- chat_rooms / session_logs
-- AI 서버가 primary writer, API 서버는 read-only.
-- DDL 변경은 API 서버가 Flyway 로 단독 관리.
-- ──────────────────────────────────────────────────────────
CREATE TABLE chat_rooms (
    room_id    VARCHAR(100) PRIMARY KEY,   -- UUID 로 AI 서버가 생성
    user_id    BIGINT       NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    title      VARCHAR(100) NOT NULL DEFAULT '새로운 레시피 상담',
    is_active  BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_chat_rooms_user_id ON chat_rooms(user_id);

CREATE TABLE session_logs (
    session_id             VARCHAR(100) PRIMARY KEY,
    room_id                VARCHAR(100) NOT NULL REFERENCES chat_rooms(room_id) ON DELETE CASCADE,
    user_id                BIGINT       NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    extracted_ingredients  JSONB,
    user_feedback          JSONB,
    recommended_recipe_ids BIGINT[],
    selected_recipe_id     BIGINT       REFERENCES recipes(recipe_id),
    chat_messages          JSONB,
    status                 VARCHAR(20)  NOT NULL DEFAULT 'IN_PROGRESS' CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'ABORTED')),
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_session_logs_room_id ON session_logs(room_id);
CREATE INDEX idx_session_logs_user_id ON session_logs(user_id);

-- ──────────────────────────────────────────────────────────
-- fridge
-- ──────────────────────────────────────────────────────────
CREATE TABLE fridge (
    id              BIGSERIAL    PRIMARY KEY,
    user_id         BIGINT       NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    ingredient_name VARCHAR(100) NOT NULL,
    amount          VARCHAR(50),
    added_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_fridge_user_id ON fridge(user_id);
