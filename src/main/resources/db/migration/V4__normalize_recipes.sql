-- ============================================================
-- V4: 레시피 정규화 (옵션 B)
--
-- naengo-ai/db/schema.sql (정규화 설계) 의 recipe 서브시스템을 채택한다.
-- V1 의 비정규화 recipes / pending_recipes 를 폐기하고, 재료/조리/라벨/
-- 미디어/영양/출처를 분리 테이블로 정규화한다.
--
-- 의도적 deviation (docs/changes 의 B 설계 메모와 동일):
--   - PK/FK 타입은 BIGSERIAL/BIGINT 유지 (schema.sql 의 SERIAL/INTEGER 미채택).
--     int4↔int8 전환은 JPA Long 매핑·전 도메인에 파급되며 기능적 이득이 없다.
--   - users / user_profiles 는 우리 소유 (V1+V2+V3) 유지. 본 V4 는 건드리지
--     않으며, user_profiles.preferred_cooking_time → _minutes 리네임만 적용.
--   - AI 파이프라인 테이블(recipe_sources, *_extractions, *_image_generations,
--     *_embeddings, *_quality_scores, *_classifications)도 DDL 은 생성하여
--     공유 DB 가 schema.sql 과 정합되게 하되, api-server 는 JPA 매핑하지 않는다
--     (ddl-auto: validate 는 미매핑 테이블을 검사하지 않음).
-- ============================================================

-- ── 0. 구 비정규화 객체 폐기 ──────────────────────────────
DROP TRIGGER IF EXISTS trigger_scrap_count ON scraps;
DROP TRIGGER IF EXISTS trigger_likes_count ON likes;
DROP TRIGGER IF EXISTS trigger_recipe_stats_create ON recipes;
DROP FUNCTION IF EXISTS update_scrap_count();
DROP FUNCTION IF EXISTS update_likes_count();
DROP FUNCTION IF EXISTS create_recipe_stats();

DROP TABLE IF EXISTS recipe_stats CASCADE;
DROP TABLE IF EXISTS scraps CASCADE;
DROP TABLE IF EXISTS likes CASCADE;
DROP TABLE IF EXISTS pending_recipes CASCADE;
DROP TABLE IF EXISTS recipes CASCADE;

-- ── 1. user_profiles: 정규화 명칭 정렬 ─────────────────────
ALTER TABLE user_profiles RENAME COLUMN preferred_cooking_time TO preferred_cooking_time_minutes;

-- ── 2. recipe_sources (AI 소유 — DDL 만) ──────────────────
CREATE TABLE recipe_sources (
    source_id            BIGSERIAL    PRIMARY KEY,
    source_type          VARCHAR(30)  NOT NULL CHECK (source_type IN ('INTERNAL','USER_SUBMISSION','WEB_SCRAPE','VIDEO','EXTERNAL_API','PUBLIC_DATA')),
    source_site          VARCHAR(50)  NOT NULL,
    parser_type          VARCHAR(20)  NOT NULL CHECK (parser_type IN ('MANUAL','HTML','AI','API','DATASET')),
    source_recipe_id     VARCHAR(100),
    source_url           VARCHAR(1024),
    source_record_id     VARCHAR(100),
    source_organization  VARCHAR(255),
    source_dataset_id    VARCHAR(100),
    source_dataset_name  VARCHAR(255),
    source_api_url       VARCHAR(1024),
    source_license       VARCHAR(100),
    source_license_url   VARCHAR(1024),
    source_author_name   VARCHAR(255),
    source_author_url    VARCHAR(1024),
    source_published_at  TIMESTAMPTZ,
    raw_payload          JSONB        NOT NULL DEFAULT '{}',
    raw_content_hash     VARCHAR(64),
    parse_status         VARCHAR(30)  NOT NULL DEFAULT 'NOT_PARSED' CHECK (parse_status IN ('NOT_PARSED','PARSED','INVALID','DUPLICATE','REVIEW_REQUIRED')),
    review_status        VARCHAR(30)  NOT NULL DEFAULT 'PENDING' CHECK (review_status IN ('PENDING','APPROVED','REJECTED')),
    import_status        VARCHAR(30)  NOT NULL DEFAULT 'NOT_IMPORTED' CHECK (import_status IN ('NOT_IMPORTED','IMPORTED','FAILED')),
    validation_errors    JSONB        NOT NULL DEFAULT '[]',
    extraction_version   VARCHAR(50),
    collected_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    parsed_at            TIMESTAMPTZ,
    reviewed_at          TIMESTAMPTZ,
    imported_at          TIMESTAMPTZ,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (source_site, source_recipe_id),
    UNIQUE (source_dataset_id, source_record_id),
    UNIQUE (source_url)
);

CREATE TABLE recipe_source_extractions (
    extraction_id         BIGSERIAL    PRIMARY KEY,
    source_id             BIGINT       NOT NULL UNIQUE REFERENCES recipe_sources(source_id) ON DELETE CASCADE,
    title                 VARCHAR(255) NOT NULL,
    summary               TEXT,
    description           TEXT,
    servings              NUMERIC(4,1),
    cooking_time_minutes  INTEGER,
    kcal_per_serving      INTEGER,
    serving_weight_grams  NUMERIC(10,2),
    carbohydrate_grams    NUMERIC(10,2),
    protein_grams         NUMERIC(10,2),
    fat_grams             NUMERIC(10,2),
    sodium_milligrams     NUMERIC(10,2),
    nutrition_source      VARCHAR(30)  CHECK (nutrition_source IN ('SOURCE','RULE','AI','ADMIN')),
    nutrition_raw         JSONB        NOT NULL DEFAULT '{}',
    difficulty            VARCHAR(10)  CHECK (difficulty IN ('easy','normal','hard')),
    source_main_image_url VARCHAR(1024),
    source_thumbnail_url  VARCHAR(1024),
    source_video_url      VARCHAR(1024),
    content_hash          VARCHAR(64),
    extracted_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE recipe_source_quality_scores (
    extraction_id          BIGINT      PRIMARY KEY REFERENCES recipe_source_extractions(extraction_id) ON DELETE CASCADE,
    completeness_score     NUMERIC(5,2),
    parse_confidence       NUMERIC(5,2),
    ingredient_confidence  NUMERIC(5,2),
    metadata_confidence    NUMERIC(5,2),
    rewrite_confidence     NUMERIC(5,2),
    nutrition_confidence   NUMERIC(5,2),
    duplicate_score        NUMERIC(5,2),
    estimated_fields       JSONB       NOT NULL DEFAULT '[]',
    validation_summary     JSONB       NOT NULL DEFAULT '[]',
    quality_notes          JSONB       NOT NULL DEFAULT '{}',
    reviewed_by            BIGINT      REFERENCES users(user_id) ON DELETE SET NULL,
    reviewed_at            TIMESTAMPTZ,
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE recipe_source_extracted_ingredients (
    extracted_ingredient_id BIGSERIAL   PRIMARY KEY,
    extraction_id           BIGINT      NOT NULL REFERENCES recipe_source_extractions(extraction_id) ON DELETE CASCADE,
    group_name              VARCHAR(100),
    name                    VARCHAR(100) NOT NULL,
    normalized_name         VARCHAR(100),
    amount_text             VARCHAR(100),
    quantity                NUMERIC(10,3),
    unit                    VARCHAR(50),
    note                    TEXT,
    raw_text                TEXT,
    is_optional             BOOLEAN     NOT NULL DEFAULT false,
    sort_order              INTEGER     NOT NULL DEFAULT 0
);

CREATE TABLE recipe_source_extracted_steps (
    extracted_step_id BIGSERIAL PRIMARY KEY,
    extraction_id     BIGINT    NOT NULL REFERENCES recipe_source_extractions(extraction_id) ON DELETE CASCADE,
    step_no           INTEGER   NOT NULL,
    instruction       TEXT      NOT NULL,
    source_image_url  VARCHAR(1024),
    tip               TEXT,
    raw_text          TEXT,
    sort_order        INTEGER   NOT NULL DEFAULT 0,
    UNIQUE (extraction_id, step_no)
);

CREATE TABLE recipe_source_extracted_labels (
    extracted_label_id BIGSERIAL  PRIMARY KEY,
    extraction_id      BIGINT     NOT NULL REFERENCES recipe_source_extractions(extraction_id) ON DELETE CASCADE,
    label_type         VARCHAR(30) NOT NULL CHECK (label_type IN ('TAG','TIP','CATEGORY','WARNING')),
    label_value        TEXT       NOT NULL,
    confidence_score   NUMERIC(5,2),
    source             VARCHAR(30) NOT NULL DEFAULT 'SCRAPE' CHECK (source IN ('SCRAPE','RULE','AI','ADMIN')),
    sort_order         INTEGER    NOT NULL DEFAULT 0
);

-- ── 3. recipes (정규화 코어 — api-server 매핑) ─────────────
CREATE TABLE recipes (
    recipe_id             BIGSERIAL    PRIMARY KEY,
    source_id             BIGINT       REFERENCES recipe_sources(source_id) ON DELETE RESTRICT,
    title                 VARCHAR(255) NOT NULL,
    summary               TEXT,
    description           TEXT         NOT NULL,
    servings              NUMERIC(4,1) NOT NULL,
    cooking_time_minutes  INTEGER      NOT NULL,
    kcal_per_serving      INTEGER,
    difficulty            VARCHAR(10)  NOT NULL CHECK (difficulty IN ('easy','normal','hard')),
    visibility            VARCHAR(20)  NOT NULL DEFAULT 'PUBLIC' CHECK (visibility IN ('PUBLIC','ADMIN_ONLY')),
    author_type           VARCHAR(20)  NOT NULL DEFAULT 'ADMIN' CHECK (author_type IN ('ADMIN','USER','SOURCE')),
    author_id             BIGINT       REFERENCES users(user_id) ON DELETE SET NULL,
    classification_status VARCHAR(30)  NOT NULL DEFAULT 'NOT_CLASSIFIED' CHECK (classification_status IN ('NOT_CLASSIFIED','CLASSIFIED','FAILED','REVIEW_REQUIRED')),
    classified_at         TIMESTAMPTZ,
    is_active             BOOLEAN      NOT NULL DEFAULT true,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

ALTER TABLE recipe_sources
    ADD COLUMN imported_recipe_id BIGINT REFERENCES recipes(recipe_id) ON DELETE SET NULL;

CREATE TABLE recipe_ingredients (
    ingredient_id   BIGSERIAL    PRIMARY KEY,
    recipe_id       BIGINT       NOT NULL REFERENCES recipes(recipe_id) ON DELETE CASCADE,
    group_name      VARCHAR(100),
    name            VARCHAR(100) NOT NULL,
    normalized_name VARCHAR(100),
    amount_text     VARCHAR(100),
    quantity        NUMERIC(10,3),
    unit            VARCHAR(50),
    note            TEXT,
    raw_text        TEXT,
    is_optional     BOOLEAN      NOT NULL DEFAULT false,
    sort_order      INTEGER      NOT NULL DEFAULT 0
);

CREATE TABLE recipe_steps (
    step_id     BIGSERIAL PRIMARY KEY,
    recipe_id   BIGINT    NOT NULL REFERENCES recipes(recipe_id) ON DELETE CASCADE,
    step_no     INTEGER   NOT NULL,
    instruction TEXT      NOT NULL,
    tip         TEXT,
    sort_order  INTEGER   NOT NULL DEFAULT 0,
    UNIQUE (recipe_id, step_no)
);

CREATE TABLE recipe_labels (
    label_id         BIGSERIAL  PRIMARY KEY,
    recipe_id        BIGINT     NOT NULL REFERENCES recipes(recipe_id) ON DELETE CASCADE,
    label_type       VARCHAR(30) NOT NULL CHECK (label_type IN ('TAG','TIP','CATEGORY','WARNING','OCCASION','SEASON')),
    label_value      TEXT       NOT NULL,
    source           VARCHAR(30) NOT NULL DEFAULT 'SCRAPE' CHECK (source IN ('SCRAPE','RULE','AI','ADMIN')),
    confidence_score NUMERIC(5,2),
    sort_order       INTEGER    NOT NULL DEFAULT 0
);

CREATE TABLE recipe_nutrition (
    recipe_id            BIGINT      PRIMARY KEY REFERENCES recipes(recipe_id) ON DELETE CASCADE,
    serving_weight_grams NUMERIC(10,2),
    kcal_per_serving     INTEGER,
    carbohydrate_grams   NUMERIC(10,2),
    protein_grams        NUMERIC(10,2),
    fat_grams            NUMERIC(10,2),
    sodium_milligrams    NUMERIC(10,2),
    source               VARCHAR(30) NOT NULL DEFAULT 'SOURCE' CHECK (source IN ('SOURCE','RULE','AI','ADMIN')),
    raw_payload          JSONB       NOT NULL DEFAULT '{}',
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE recipe_classifications (
    recipe_id             BIGINT      PRIMARY KEY REFERENCES recipes(recipe_id) ON DELETE CASCADE,
    cuisine_type          VARCHAR(50),
    dish_type             VARCHAR(50),
    cooking_methods       JSONB       NOT NULL DEFAULT '[]',
    meal_types            JSONB       NOT NULL DEFAULT '[]',
    occasions             JSONB       NOT NULL DEFAULT '[]',
    situations            JSONB       NOT NULL DEFAULT '[]',
    main_ingredients      JSONB       NOT NULL DEFAULT '[]',
    taste_keywords        JSONB       NOT NULL DEFAULT '[]',
    texture_keywords      JSONB       NOT NULL DEFAULT '[]',
    diet_keywords         JSONB       NOT NULL DEFAULT '[]',
    allergen_keywords     JSONB       NOT NULL DEFAULT '[]',
    equipment             JSONB       NOT NULL DEFAULT '[]',
    season                JSONB       NOT NULL DEFAULT '[]',
    category_labels       JSONB       NOT NULL DEFAULT '[]',
    classification_source VARCHAR(30) NOT NULL DEFAULT 'RULE' CHECK (classification_source IN ('RULE','AI','ADMIN')),
    confidence_score      NUMERIC(5,2),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE recipe_media (
    media_id         BIGSERIAL   PRIMARY KEY,
    recipe_id        BIGINT      NOT NULL REFERENCES recipes(recipe_id) ON DELETE CASCADE,
    step_id          BIGINT      REFERENCES recipe_steps(step_id) ON DELETE CASCADE,
    media_type       VARCHAR(20) NOT NULL CHECK (media_type IN ('IMAGE','VIDEO')),
    image_role       VARCHAR(30) CHECK (image_role IN ('MAIN','THUMBNAIL','STEP','GALLERY','GENERATED_CANDIDATE')),
    source_url       VARCHAR(1024),
    storage_url      VARCHAR(1024) NOT NULL,
    thumbnail_url    VARCHAR(1024),
    width            INTEGER,
    height           INTEGER,
    file_size_bytes  INTEGER,
    mime_type        VARCHAR(100),
    storage_provider VARCHAR(30) NOT NULL DEFAULT 'S3',
    generation_id    BIGINT,
    is_primary       BOOLEAN     NOT NULL DEFAULT false,
    sort_order       INTEGER     NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE recipe_image_generations (
    generation_id        BIGSERIAL   PRIMARY KEY,
    recipe_id            BIGINT      NOT NULL REFERENCES recipes(recipe_id) ON DELETE CASCADE,
    requested_by_user_id BIGINT      REFERENCES users(user_id) ON DELETE SET NULL,
    source_id            BIGINT      REFERENCES recipe_sources(source_id) ON DELETE SET NULL,
    provider             VARCHAR(50) NOT NULL,
    model                VARCHAR(100) NOT NULL,
    prompt               TEXT        NOT NULL,
    negative_prompt      TEXT,
    status               VARCHAR(30) NOT NULL DEFAULT 'REQUESTED' CHECK (status IN ('REQUESTED','GENERATING','SUCCEEDED','FAILED','SELECTED','REJECTED')),
    generated_media_id   BIGINT      REFERENCES recipe_media(media_id) ON DELETE SET NULL,
    error_message        TEXT,
    metadata             JSONB       NOT NULL DEFAULT '{}',
    requested_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at         TIMESTAMPTZ,
    selected_at          TIMESTAMPTZ,
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE recipe_media
    ADD CONSTRAINT fk_recipe_media_generation
    FOREIGN KEY (generation_id) REFERENCES recipe_image_generations(generation_id) ON DELETE SET NULL;

CREATE TABLE recipe_embeddings (
    embedding_id   BIGSERIAL   PRIMARY KEY,
    recipe_id      BIGINT      NOT NULL REFERENCES recipes(recipe_id) ON DELETE CASCADE,
    embedding_type VARCHAR(30) NOT NULL DEFAULT 'RECIPE_SEARCH' CHECK (embedding_type IN ('RECIPE_SEARCH','INGREDIENTS','STEPS')),
    model          VARCHAR(100) NOT NULL,
    content_hash   VARCHAR(64) NOT NULL,
    embedding      VECTOR(1536) NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (recipe_id, embedding_type, model, content_hash)
);

CREATE TABLE recipe_quality_scores (
    recipe_id                 BIGINT      PRIMARY KEY REFERENCES recipes(recipe_id) ON DELETE CASCADE,
    completeness_score        NUMERIC(5,2),
    image_quality_score       NUMERIC(5,2),
    instruction_quality_score NUMERIC(5,2),
    nutrition_confidence      NUMERIC(5,2),
    classification_confidence NUMERIC(5,2),
    duplicate_score           NUMERIC(5,2),
    reviewed_by               BIGINT      REFERENCES users(user_id) ON DELETE SET NULL,
    reviewed_at               TIMESTAMPTZ,
    updated_at                TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE recipe_stats (
    recipe_id   BIGINT  PRIMARY KEY REFERENCES recipes(recipe_id) ON DELETE CASCADE,
    likes_count INTEGER NOT NULL DEFAULT 0 CHECK (likes_count >= 0),
    scrap_count INTEGER NOT NULL DEFAULT 0 CHECK (scrap_count >= 0)
);

-- ── 4. pending_recipes (정규화 — submission_text + draft_payload) ──
CREATE TABLE pending_recipes (
    pending_recipe_id  BIGSERIAL    PRIMARY KEY,
    user_id            BIGINT       NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    title              VARCHAR(255) NOT NULL,
    submission_text    TEXT         NOT NULL,
    draft_payload      JSONB        NOT NULL DEFAULT '{"description":null,"ingredients":[],"ingredients_raw":[],"instructions":[],"servings":null,"cooking_time_minutes":null,"kcal_per_serving":null,"difficulty":null,"category":[],"tags":[],"tips":[],"video_url":null,"image_url":null}',
    ai_suggested_patch JSONB        NOT NULL DEFAULT '{"description":null,"ingredients":[],"ingredients_raw":[],"instructions":[],"servings":null,"cooking_time_minutes":null,"kcal_per_serving":null,"difficulty":null,"category":[],"tags":[],"tips":[],"video_url":null,"image_url":null}',
    validation_errors  JSONB        NOT NULL DEFAULT '[]',
    status             VARCHAR(20)  NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','APPROVED','REJECTED')),
    import_status      VARCHAR(30)  NOT NULL DEFAULT 'NOT_IMPORTED' CHECK (import_status IN ('NOT_IMPORTED','IMPORTED','FAILED')),
    is_active          BOOLEAN      NOT NULL DEFAULT true,
    admin_note         TEXT,
    rejection_reason   TEXT,
    reviewed_by        BIGINT       REFERENCES users(user_id) ON DELETE SET NULL,
    reviewed_at        TIMESTAMPTZ,
    imported_recipe_id BIGINT       REFERENCES recipes(recipe_id) ON DELETE SET NULL,
    imported_at        TIMESTAMPTZ,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- ── 5. likes / scraps (recipe_id 정규화 코어 참조) ─────────
CREATE TABLE likes (
    like_id    BIGSERIAL   PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    recipe_id  BIGINT      NOT NULL REFERENCES recipes(recipe_id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_likes_user_recipe UNIQUE (user_id, recipe_id)
);

CREATE TABLE scraps (
    scrap_id   BIGSERIAL   PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    recipe_id  BIGINT      NOT NULL REFERENCES recipes(recipe_id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_scraps_user_recipe UNIQUE (user_id, recipe_id)
);

-- ── 6. updated_at 트리거 ───────────────────────────────────
CREATE OR REPLACE FUNCTION touch_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER touch_recipe_sources_updated_at BEFORE UPDATE ON recipe_sources FOR EACH ROW EXECUTE FUNCTION touch_updated_at();
CREATE TRIGGER touch_recipe_source_extractions_updated_at BEFORE UPDATE ON recipe_source_extractions FOR EACH ROW EXECUTE FUNCTION touch_updated_at();
CREATE TRIGGER touch_recipe_source_quality_scores_updated_at BEFORE UPDATE ON recipe_source_quality_scores FOR EACH ROW EXECUTE FUNCTION touch_updated_at();
CREATE TRIGGER touch_recipes_updated_at BEFORE UPDATE ON recipes FOR EACH ROW EXECUTE FUNCTION touch_updated_at();
CREATE TRIGGER touch_recipe_classifications_updated_at BEFORE UPDATE ON recipe_classifications FOR EACH ROW EXECUTE FUNCTION touch_updated_at();
CREATE TRIGGER touch_recipe_nutrition_updated_at BEFORE UPDATE ON recipe_nutrition FOR EACH ROW EXECUTE FUNCTION touch_updated_at();
CREATE TRIGGER touch_recipe_image_generations_updated_at BEFORE UPDATE ON recipe_image_generations FOR EACH ROW EXECUTE FUNCTION touch_updated_at();
CREATE TRIGGER touch_recipe_quality_scores_updated_at BEFORE UPDATE ON recipe_quality_scores FOR EACH ROW EXECUTE FUNCTION touch_updated_at();
CREATE TRIGGER touch_pending_recipes_updated_at BEFORE UPDATE ON pending_recipes FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

-- ── 7. recipe INSERT 시 종속 1:1 행 자동 생성 ──────────────
CREATE OR REPLACE FUNCTION create_recipe_dependents()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO recipe_stats (recipe_id) VALUES (NEW.recipe_id) ON CONFLICT (recipe_id) DO NOTHING;
    INSERT INTO recipe_classifications (recipe_id) VALUES (NEW.recipe_id) ON CONFLICT (recipe_id) DO NOTHING;
    INSERT INTO recipe_quality_scores (recipe_id) VALUES (NEW.recipe_id) ON CONFLICT (recipe_id) DO NOTHING;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_create_recipe_dependents
AFTER INSERT ON recipes
FOR EACH ROW EXECUTE FUNCTION create_recipe_dependents();

-- ── 8. likes / scraps 카운터 트리거 (recipe_stats upsert) ──
CREATE OR REPLACE FUNCTION update_likes_count()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        INSERT INTO recipe_stats (recipe_id, likes_count, scrap_count)
        VALUES (NEW.recipe_id, 1, 0)
        ON CONFLICT (recipe_id) DO UPDATE SET likes_count = recipe_stats.likes_count + 1;
    ELSIF TG_OP = 'DELETE' THEN
        UPDATE recipe_stats SET likes_count = GREATEST(likes_count - 1, 0) WHERE recipe_id = OLD.recipe_id;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_likes_count
AFTER INSERT OR DELETE ON likes
FOR EACH ROW EXECUTE FUNCTION update_likes_count();

CREATE OR REPLACE FUNCTION update_scrap_count()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        INSERT INTO recipe_stats (recipe_id, likes_count, scrap_count)
        VALUES (NEW.recipe_id, 0, 1)
        ON CONFLICT (recipe_id) DO UPDATE SET scrap_count = recipe_stats.scrap_count + 1;
    ELSIF TG_OP = 'DELETE' THEN
        UPDATE recipe_stats SET scrap_count = GREATEST(scrap_count - 1, 0) WHERE recipe_id = OLD.recipe_id;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_scrap_count
AFTER INSERT OR DELETE ON scraps
FOR EACH ROW EXECUTE FUNCTION update_scrap_count();

-- ── 9. 인덱스 ──────────────────────────────────────────────
CREATE INDEX idx_recipe_sources_lifecycle ON recipe_sources(parse_status, review_status, import_status, source_id DESC);
CREATE INDEX idx_recipe_sources_hash ON recipe_sources(raw_content_hash);
CREATE INDEX idx_source_extracted_ingredients_name ON recipe_source_extracted_ingredients(normalized_name);
CREATE INDEX idx_source_extracted_labels_extraction ON recipe_source_extracted_labels(extraction_id, label_type);
CREATE INDEX idx_recipes_active_id ON recipes(is_active, recipe_id DESC);
CREATE INDEX idx_recipes_classification_status ON recipes(classification_status, recipe_id DESC);
CREATE INDEX idx_recipes_author_id ON recipes(author_id);
CREATE INDEX idx_recipe_ingredients_recipe_id ON recipe_ingredients(recipe_id);
CREATE INDEX idx_recipe_ingredients_name ON recipe_ingredients(normalized_name);
CREATE INDEX idx_recipe_steps_recipe_id ON recipe_steps(recipe_id);
CREATE INDEX idx_recipe_labels_recipe_type ON recipe_labels(recipe_id, label_type);
CREATE INDEX idx_recipe_nutrition_kcal ON recipe_nutrition(kcal_per_serving);
CREATE INDEX idx_recipe_nutrition_sodium ON recipe_nutrition(sodium_milligrams);
CREATE INDEX idx_recipe_classifications_cuisine ON recipe_classifications(cuisine_type);
CREATE INDEX idx_recipe_classifications_dish ON recipe_classifications(dish_type);
CREATE INDEX idx_recipe_classifications_main_ingredients_gin ON recipe_classifications USING GIN (main_ingredients);
CREATE INDEX idx_recipe_classifications_keywords_gin ON recipe_classifications USING GIN (taste_keywords);
CREATE INDEX idx_recipe_classifications_categories_gin ON recipe_classifications USING GIN (category_labels);
CREATE INDEX idx_recipe_media_recipe_role ON recipe_media(recipe_id, image_role, is_primary);
CREATE INDEX idx_recipe_image_generations_recipe_status ON recipe_image_generations(recipe_id, status, requested_at DESC);
CREATE INDEX idx_recipe_embeddings_recipe_type ON recipe_embeddings(recipe_id, embedding_type);
CREATE INDEX idx_pending_recipes_user_status_created ON pending_recipes(user_id, is_active, status, created_at DESC);
CREATE INDEX idx_likes_recipe_id ON likes(recipe_id);
CREATE INDEX idx_likes_user_id ON likes(user_id);
CREATE INDEX idx_scraps_recipe_id ON scraps(recipe_id);
CREATE INDEX idx_scraps_user_created ON scraps(user_id, scrap_id DESC);
