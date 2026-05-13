-- ============================================================
-- V1: 초기 스키마
--
-- 본 V1 은 구 `V1__init.sql` 을 폐기하고 구 `V4__fixed_schema.sql` 의
-- 설계를 V1 자리로 옮긴 결과다. AI 서버 OpenAPI 0.1.0 (docs/api-1.json)
-- 의 RecipeResponse / ChatMessageResponse / ChatRoomResponse 와 정합되도록
-- 짜였다. 결정 배경 및 타임라인은 docs/api-server-tasks.md §1.5,
-- 갭분석은 docs/spec/ai-server-contract.md.
--
-- V2 (소셜 로그인 unique 제약 추가) / V3 (탈퇴 익명화 deleted_at) 은
-- 본 V1 위에 ALTER 로 누적 적용된다. V2 와 충돌하지 않도록:
--   - users.password_hash 는 본 V1 에서 이미 nullable
--   - users.provider / provider_id 는 본 V1 에 이미 존재
--   - uq_provider_provider_id UNIQUE 제약은 V2 가 추가 (여기서는 두지 않는다)
--
-- 타입 규약: 모든 PK / FK 는 BIGSERIAL / BIGINT (JPA `Long` 매핑과 정합).
-- 시간 컬럼은 TIMESTAMPTZ (V2/V3 와 일관).
-- ============================================================

-- pgvector (RAG 검색용. AI 서버가 recipes.embedding R/W).
CREATE EXTENSION IF NOT EXISTS vector;

-- ──────────────────────────────────────────────────────────
-- users
-- 소셜 로그인 필드(provider / provider_id) 는 V2 의 합리화된 기본값을
-- V1 단계에서 이미 보유한다. uq_provider_provider_id 만 V2 에 위임.
-- ──────────────────────────────────────────────────────────
CREATE TABLE users (
    user_id       BIGSERIAL    PRIMARY KEY,
    email         VARCHAR(255) UNIQUE,                                   -- nullable: 회원 탈퇴 익명화 시 NULL (UNIQUE 는 다중 NULL 허용)
    password_hash VARCHAR(255),                                          -- 소셜 로그인 사용자는 NULL
    nickname      VARCHAR(50)  NOT NULL UNIQUE,
    role          VARCHAR(20)  NOT NULL DEFAULT 'USER' CHECK (role IN ('USER', 'ADMIN')),
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,                    -- 계정 활성화 여부 (탈퇴/이메일 인증 등)
    is_blocked    BOOLEAN      NOT NULL DEFAULT FALSE,                   -- 악성 사용자 차단
    provider      VARCHAR(20)  NOT NULL DEFAULT 'LOCAL',
    provider_id   VARCHAR(255),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- ──────────────────────────────────────────────────────────
-- user_profiles
-- 구 users.preferences JSONB 를 풍부한 컬럼으로 분리.
-- AI 서버가 채팅 분석으로 채우거나 사용자가 직접 입력.
-- ──────────────────────────────────────────────────────────
CREATE TABLE user_profiles (
    user_id                       BIGINT       PRIMARY KEY REFERENCES users(user_id) ON DELETE CASCADE,
    user_input                    JSONB        NOT NULL DEFAULT '[]',
    allergies                     JSONB,
    dietary_restrictions          JSONB,
    preferred_ingredients         JSONB,
    disliked_ingredients          JSONB,
    preferred_categories          JSONB,
    frequently_used_ingredients   JSONB,
    taste_keywords                JSONB,
    cooking_skill                 VARCHAR(10)  CHECK (cooking_skill IN ('easy', 'normal', 'hard')),
    preferred_cooking_time        INTEGER,
    serving_size                  NUMERIC(4, 1),
    recent_recipe_ids             JSONB,
    ai_analyzed_at                TIMESTAMPTZ,
    updated_at                    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- ──────────────────────────────────────────────────────────
-- recipes (관리자가 노출 결정한 승인 레시피만)
-- 승인 전 레시피는 pending_recipes 로 분리됨.
-- AI 서버 RecipeResponse 와 1:1 매핑.
-- ──────────────────────────────────────────────────────────
CREATE TABLE recipes (
    recipe_id        BIGSERIAL    PRIMARY KEY,
    title            VARCHAR(255) NOT NULL,
    description      TEXT         NOT NULL,
    ingredients      JSONB        NOT NULL,                              -- IngredientItem[] (name/amount/unit/type/note)
    ingredients_raw  TEXT         NOT NULL,
    instructions     JSONB        NOT NULL,                              -- string[]
    servings         NUMERIC(4,1) NOT NULL,
    cooking_time     INTEGER      NOT NULL,                              -- 분
    calories         INTEGER,
    difficulty       VARCHAR(10)  NOT NULL CHECK (difficulty IN ('easy', 'normal', 'hard')),
    category         JSONB        NOT NULL,                              -- string[]
    tags             JSONB        NOT NULL DEFAULT '[]',
    tips             JSONB        NOT NULL DEFAULT '[]',
    content          TEXT,                                               -- 자유 형식 본문 (선택)
    video_url        VARCHAR(512),
    image_url        VARCHAR(512),
    is_active        BOOLEAN      NOT NULL DEFAULT TRUE,                 -- 검색/노출 토글
    author_type      VARCHAR(20)  NOT NULL DEFAULT 'ADMIN' CHECK (author_type IN ('ADMIN', 'USER')),
    author_id        BIGINT       REFERENCES users(user_id) ON DELETE SET NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    embedding        VECTOR(1536)                                        -- AI 서버가 채움
);

CREATE INDEX idx_recipes_is_active   ON recipes(is_active);
CREATE INDEX idx_recipes_author_id   ON recipes(author_id);
CREATE INDEX idx_recipes_created_at  ON recipes(created_at DESC);
CREATE INDEX idx_recipes_video_url   ON recipes(video_url) WHERE video_url IS NOT NULL;

-- ──────────────────────────────────────────────────────────
-- pending_recipes (사용자 제출 → 관리자 승인 → recipes 로 이동)
-- ──────────────────────────────────────────────────────────
CREATE TABLE pending_recipes (
    pending_recipe_id BIGSERIAL    PRIMARY KEY,
    user_id           BIGINT       NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    title             VARCHAR(255) NOT NULL,
    description       TEXT,
    content           TEXT         NOT NULL,                             -- 사용자가 자유 형식으로 작성
    ingredients       JSONB,
    ingredients_raw   TEXT,
    instructions      JSONB,
    servings          NUMERIC(4,1),
    cooking_time      INTEGER,
    calories          INTEGER,
    difficulty        VARCHAR(10)  CHECK (difficulty IN ('easy', 'normal', 'hard')),
    category          JSONB,
    tags              JSONB,
    tips              JSONB,
    video_url         VARCHAR(512),
    image_url         VARCHAR(512),
    is_active         BOOLEAN      NOT NULL DEFAULT TRUE,                -- 사용자 취소/삭제 플래그
    status            VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
                                   CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    admin_note        TEXT,                                              -- 반려 사유 등
    reviewed_at       TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_pending_recipes_user_id ON pending_recipes(user_id);
CREATE INDEX idx_pending_recipes_status  ON pending_recipes(status);

-- ──────────────────────────────────────────────────────────
-- chat_rooms / chat_messages (AI 서버 SSE 채팅의 영속 저장)
-- AI 서버가 primary writer, API 서버는 read-only.
-- DDL 은 API 서버가 Flyway 로 단독 관리.
-- ──────────────────────────────────────────────────────────
CREATE TABLE chat_rooms (
    room_id    BIGSERIAL    PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    title      VARCHAR(100) NOT NULL DEFAULT '새로운 레시피 상담',
    is_active  BOOLEAN      NOT NULL DEFAULT TRUE,                       -- 사용자 숨김 플래그
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_chat_rooms_user_id ON chat_rooms(user_id);

CREATE TABLE chat_messages (
    message_id BIGSERIAL    PRIMARY KEY,
    room_id    BIGINT       NOT NULL REFERENCES chat_rooms(room_id) ON DELETE CASCADE,
    role       VARCHAR(20)  NOT NULL CHECK (role IN ('user', 'model')),
    content    TEXT         NOT NULL,
    recipe_ids JSONB,                                                    -- 추천된 recipe_id 목록
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_chat_messages_room_id ON chat_messages(room_id);

-- ──────────────────────────────────────────────────────────
-- likes / scraps + recipe_stats + 트리거
-- recipe_stats 는 recipes 와 1:1. 새 recipe INSERT 시 0,0 row 자동 생성.
-- likes/scraps INSERT/DELETE 시 트리거가 카운터 자동 증감.
-- ──────────────────────────────────────────────────────────
CREATE TABLE likes (
    like_id    BIGSERIAL    PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    recipe_id  BIGINT       NOT NULL REFERENCES recipes(recipe_id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_likes_user_recipe UNIQUE (user_id, recipe_id)
);

CREATE INDEX idx_likes_user_id ON likes(user_id);

CREATE TABLE scraps (
    scrap_id   BIGSERIAL    PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    recipe_id  BIGINT       NOT NULL REFERENCES recipes(recipe_id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_scraps_user_recipe UNIQUE (user_id, recipe_id)
);

CREATE INDEX idx_scraps_user_id ON scraps(user_id);

CREATE TABLE recipe_stats (
    recipe_id   BIGINT  PRIMARY KEY REFERENCES recipes(recipe_id) ON DELETE CASCADE,
    likes_count INTEGER NOT NULL DEFAULT 0,
    scrap_count INTEGER NOT NULL DEFAULT 0
);

-- recipes INSERT 시 recipe_stats(0,0) 자동 생성
CREATE OR REPLACE FUNCTION create_recipe_stats()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO recipe_stats (recipe_id) VALUES (NEW.recipe_id)
    ON CONFLICT (recipe_id) DO NOTHING;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_recipe_stats_create
AFTER INSERT ON recipes
FOR EACH ROW EXECUTE FUNCTION create_recipe_stats();

-- likes INSERT/DELETE → recipe_stats.likes_count
CREATE OR REPLACE FUNCTION update_likes_count()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        UPDATE recipe_stats SET likes_count = likes_count + 1 WHERE recipe_id = NEW.recipe_id;
    ELSIF TG_OP = 'DELETE' THEN
        UPDATE recipe_stats SET likes_count = GREATEST(likes_count - 1, 0) WHERE recipe_id = OLD.recipe_id;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_likes_count
AFTER INSERT OR DELETE ON likes
FOR EACH ROW EXECUTE FUNCTION update_likes_count();

-- scraps INSERT/DELETE → recipe_stats.scrap_count
CREATE OR REPLACE FUNCTION update_scrap_count()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        UPDATE recipe_stats SET scrap_count = scrap_count + 1 WHERE recipe_id = NEW.recipe_id;
    ELSIF TG_OP = 'DELETE' THEN
        UPDATE recipe_stats SET scrap_count = GREATEST(scrap_count - 1, 0) WHERE recipe_id = OLD.recipe_id;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_scrap_count
AFTER INSERT OR DELETE ON scraps
FOR EACH ROW EXECUTE FUNCTION update_scrap_count();
