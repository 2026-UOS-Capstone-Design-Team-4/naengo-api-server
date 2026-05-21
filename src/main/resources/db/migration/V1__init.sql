-- ============================================================
-- V1 (2026-05-21 옵션 A 채택 후 재작성)
--
-- 운영 RDS 의 ground truth (DB 팀원이 SQL 로 직접 적용한 DBv5) 기준.
-- 이전 V1~V5 (우리 자체 진화) 는 폐기. 본 V1 이 dev DB / Testcontainers /
-- 운영 RDS 모두의 정본 기준이다.
--
-- 운영 RDS 는 이미 본 V1 동일 결과가 들어 있으므로 Flyway 가
-- baseline-on-migrate=true + baseline-version="1" 설정으로 migrate 없이
-- baseline row 만 INSERT (application-prod.yml). dev/test 는 처음부터 본 V1 적용.
--
-- 원본 DBv5.sql 대비 정정 2곳:
--   - 라인 614 (DBv5): idx_social_accounts_user_id → 테이블이 user_identities 라 정정
--   - 라인 539~541 (DBv5): touch_pending_recipes_updated_at trigger 의 대상 테이블을 user_recipes 로 정정
-- ============================================================

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE users (
    user_id SERIAL PRIMARY KEY,
    username VARCHAR(255) UNIQUE,
    password_hash VARCHAR(255),
    nickname VARCHAR(50) UNIQUE NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'USER'
        CHECK (role IN ('USER', 'ADMIN')),
    is_active BOOLEAN NOT NULL DEFAULT true,
    is_blocked BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE user_identities (
    id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    email VARCHAR(255),
    provider VARCHAR(30) NOT NULL
        CHECK (provider IN ('KAKAO', 'GOOGLE', 'NAVER', 'APPLE')),
    provider_user_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (provider, provider_user_id)
);

CREATE TABLE user_profiles (
    user_id INTEGER PRIMARY KEY REFERENCES users(user_id) ON DELETE CASCADE,
    user_input JSONB NOT NULL DEFAULT '[]',
    allergies JSONB NOT NULL DEFAULT '[]',
    dietary_restrictions JSONB NOT NULL DEFAULT '[]',
    preferred_ingredients JSONB NOT NULL DEFAULT '[]',
    disliked_ingredients JSONB NOT NULL DEFAULT '[]',
    preferred_categories JSONB NOT NULL DEFAULT '[]',
    frequently_used_ingredients JSONB NOT NULL DEFAULT '[]',
    taste_keywords JSONB NOT NULL DEFAULT '[]',
    cooking_skill VARCHAR(10)
        CHECK (cooking_skill IN ('easy', 'normal', 'hard')),
    preferred_cooking_time_minutes INTEGER,
    serving_size NUMERIC(4, 1),
    recent_recipe_ids JSONB NOT NULL DEFAULT '[]',
    ai_analyzed_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE recipe_sources (
    source_id SERIAL PRIMARY KEY,
    source_type VARCHAR(30) NOT NULL
        CHECK (
            source_type IN (
                'INTERNAL',
                'USER_SUBMISSION',
                'WEB_SCRAPE',
                'VIDEO',
                'EXTERNAL_API',
                'PUBLIC_DATA'
            )
        ),
    source_site VARCHAR(50) NOT NULL,
    parser_type VARCHAR(20) NOT NULL
        CHECK (parser_type IN ('MANUAL', 'HTML', 'AI', 'API', 'DATASET')),
    source_recipe_id VARCHAR(100),
    source_url VARCHAR(1024),
    source_record_id VARCHAR(100),
    source_organization VARCHAR(255),
    source_dataset_id VARCHAR(100),
    source_dataset_name VARCHAR(255),
    source_api_url VARCHAR(1024),
    source_license VARCHAR(100),
    source_license_url VARCHAR(1024),
    source_author_name VARCHAR(255),
    source_author_url VARCHAR(1024),
    source_published_at TIMESTAMP WITH TIME ZONE,
    raw_payload JSONB NOT NULL DEFAULT '{}',
    raw_content_hash VARCHAR(64),
    parse_status VARCHAR(30) NOT NULL DEFAULT 'NOT_PARSED'
        CHECK (
            parse_status IN (
                'NOT_PARSED',
                'PARSED',
                'INVALID',
                'DUPLICATE',
                'REVIEW_REQUIRED'
            )
        ),
    review_status VARCHAR(30) NOT NULL DEFAULT 'PENDING'
        CHECK (review_status IN ('PENDING', 'APPROVED', 'REJECTED')),
    import_status VARCHAR(30) NOT NULL DEFAULT 'NOT_IMPORTED'
        CHECK (import_status IN ('NOT_IMPORTED', 'IMPORTED', 'FAILED')),
    validation_errors JSONB NOT NULL DEFAULT '[]',
    extraction_version VARCHAR(50),
    collected_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    parsed_at TIMESTAMP WITH TIME ZONE,
    reviewed_at TIMESTAMP WITH TIME ZONE,
    imported_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (source_site, source_recipe_id),
    UNIQUE (source_dataset_id, source_record_id),
    UNIQUE (source_url)
);

CREATE TABLE recipe_source_extractions (
    extraction_id SERIAL PRIMARY KEY,
    source_id INTEGER NOT NULL UNIQUE
        REFERENCES recipe_sources(source_id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    summary TEXT,
    description TEXT,
    servings NUMERIC(4, 1),
    cooking_time_minutes INTEGER,
    kcal_per_serving INTEGER,
    serving_weight_grams NUMERIC(10, 2),
    carbohydrate_grams NUMERIC(10, 2),
    protein_grams NUMERIC(10, 2),
    fat_grams NUMERIC(10, 2),
    sodium_milligrams NUMERIC(10, 2),
    nutrition_source VARCHAR(30)
        CHECK (nutrition_source IN ('SOURCE', 'RULE', 'AI', 'ADMIN')),
    nutrition_raw JSONB NOT NULL DEFAULT '{}',
    difficulty VARCHAR(10)
        CHECK (difficulty IN ('easy', 'normal', 'hard')),
    source_main_image_url VARCHAR(1024),
    source_thumbnail_url VARCHAR(1024),
    source_video_url VARCHAR(1024),
    content_hash VARCHAR(64),
    extracted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE recipe_source_quality_scores (
    extraction_id INTEGER PRIMARY KEY
        REFERENCES recipe_source_extractions(extraction_id) ON DELETE CASCADE,
    completeness_score NUMERIC(5, 2),
    parse_confidence NUMERIC(5, 2),
    ingredient_confidence NUMERIC(5, 2),
    metadata_confidence NUMERIC(5, 2),
    rewrite_confidence NUMERIC(5, 2),
    nutrition_confidence NUMERIC(5, 2),
    duplicate_score NUMERIC(5, 2),
    estimated_fields JSONB NOT NULL DEFAULT '[]',
    validation_summary JSONB NOT NULL DEFAULT '[]',
    quality_notes JSONB NOT NULL DEFAULT '{}',
    reviewed_by INTEGER REFERENCES users(user_id) ON DELETE SET NULL,
    reviewed_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE recipe_source_extracted_ingredients (
    extracted_ingredient_id SERIAL PRIMARY KEY,
    extraction_id INTEGER NOT NULL
        REFERENCES recipe_source_extractions(extraction_id) ON DELETE CASCADE,
    group_name VARCHAR(100),
    name VARCHAR(100) NOT NULL,
    normalized_name VARCHAR(100),
    amount_text VARCHAR(100),
    quantity NUMERIC(10, 3),
    unit VARCHAR(50),
    note TEXT,
    raw_text TEXT,
    is_optional BOOLEAN NOT NULL DEFAULT false,
    sort_order INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE recipe_source_extracted_steps (
    extracted_step_id SERIAL PRIMARY KEY,
    extraction_id INTEGER NOT NULL
        REFERENCES recipe_source_extractions(extraction_id) ON DELETE CASCADE,
    step_no INTEGER NOT NULL,
    instruction TEXT NOT NULL,
    source_image_url VARCHAR(1024),
    tip TEXT,
    raw_text TEXT,
    sort_order INTEGER NOT NULL DEFAULT 0,
    UNIQUE (extraction_id, step_no)
);

CREATE TABLE recipe_source_extracted_labels (
    extracted_label_id SERIAL PRIMARY KEY,
    extraction_id INTEGER NOT NULL
        REFERENCES recipe_source_extractions(extraction_id) ON DELETE CASCADE,
    label_type VARCHAR(30) NOT NULL
        CHECK (label_type IN ('TAG', 'TIP', 'CATEGORY', 'WARNING')),
    label_value TEXT NOT NULL,
    confidence_score NUMERIC(5, 2),
    source VARCHAR(30) NOT NULL DEFAULT 'SCRAPE'
        CHECK (source IN ('SCRAPE', 'RULE', 'AI', 'ADMIN')),
    sort_order INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE recipes (
    recipe_id SERIAL PRIMARY KEY,
    source_id INTEGER REFERENCES recipe_sources(source_id) ON DELETE RESTRICT,
    title VARCHAR(255) NOT NULL,
    summary TEXT,
    description TEXT NOT NULL,
    servings NUMERIC(4, 1) NOT NULL,
    cooking_time_minutes INTEGER NOT NULL,
    kcal_per_serving INTEGER,
    difficulty VARCHAR(10) NOT NULL
        CHECK (difficulty IN ('easy', 'normal', 'hard')),
    visibility VARCHAR(20) NOT NULL DEFAULT 'PUBLIC'
        CHECK (visibility IN ('PUBLIC', 'ADMIN_ONLY')),
    author_type VARCHAR(20) NOT NULL DEFAULT 'ADMIN'
        CHECK (author_type IN ('ADMIN', 'USER', 'SOURCE')),
    author_id INTEGER REFERENCES users(user_id) ON DELETE SET NULL,
    classification_status VARCHAR(30) NOT NULL DEFAULT 'NOT_CLASSIFIED'
        CHECK (
            classification_status IN (
                'NOT_CLASSIFIED',
                'CLASSIFIED',
                'FAILED',
                'REVIEW_REQUIRED'
            )
        ),
    classified_at TIMESTAMP WITH TIME ZONE,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE recipe_sources
ADD COLUMN imported_recipe_id INTEGER
REFERENCES recipes(recipe_id) ON DELETE SET NULL;

CREATE TABLE recipe_ingredients (
    ingredient_id SERIAL PRIMARY KEY,
    recipe_id INTEGER NOT NULL REFERENCES recipes(recipe_id) ON DELETE CASCADE,
    group_name VARCHAR(100),
    name VARCHAR(100) NOT NULL,
    normalized_name VARCHAR(100),
    amount_text VARCHAR(100),
    quantity NUMERIC(10, 3),
    unit VARCHAR(50),
    note TEXT,
    raw_text TEXT,
    is_optional BOOLEAN NOT NULL DEFAULT false,
    sort_order INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE recipe_steps (
    step_id SERIAL PRIMARY KEY,
    recipe_id INTEGER NOT NULL REFERENCES recipes(recipe_id) ON DELETE CASCADE,
    step_no INTEGER NOT NULL,
    instruction TEXT NOT NULL,
    tip TEXT,
    sort_order INTEGER NOT NULL DEFAULT 0,
    UNIQUE (recipe_id, step_no)
);

CREATE TABLE recipe_labels (
    label_id SERIAL PRIMARY KEY,
    recipe_id INTEGER NOT NULL REFERENCES recipes(recipe_id) ON DELETE CASCADE,
    label_type VARCHAR(30) NOT NULL
        CHECK (
            label_type IN (
                'TAG',
                'TIP',
                'CATEGORY',
                'WARNING',
                'OCCASION',
                'SEASON'
            )
        ),
    label_value TEXT NOT NULL,
    source VARCHAR(30) NOT NULL DEFAULT 'SCRAPE'
        CHECK (source IN ('SCRAPE', 'RULE', 'AI', 'ADMIN')),
    confidence_score NUMERIC(5, 2),
    sort_order INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE recipe_nutrition (
    recipe_id INTEGER PRIMARY KEY REFERENCES recipes(recipe_id) ON DELETE CASCADE,
    serving_weight_grams NUMERIC(10, 2),
    kcal_per_serving INTEGER,
    carbohydrate_grams NUMERIC(10, 2),
    protein_grams NUMERIC(10, 2),
    fat_grams NUMERIC(10, 2),
    sodium_milligrams NUMERIC(10, 2),
    source VARCHAR(30) NOT NULL DEFAULT 'SOURCE'
        CHECK (source IN ('SOURCE', 'RULE', 'AI', 'ADMIN')),
    raw_payload JSONB NOT NULL DEFAULT '{}',
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE recipe_classifications (
    recipe_id INTEGER PRIMARY KEY REFERENCES recipes(recipe_id) ON DELETE CASCADE,
    cuisine_type VARCHAR(50),
    dish_type VARCHAR(50),
    cooking_methods JSONB NOT NULL DEFAULT '[]',
    meal_types JSONB NOT NULL DEFAULT '[]',
    occasions JSONB NOT NULL DEFAULT '[]',
    situations JSONB NOT NULL DEFAULT '[]',
    main_ingredients JSONB NOT NULL DEFAULT '[]',
    taste_keywords JSONB NOT NULL DEFAULT '[]',
    texture_keywords JSONB NOT NULL DEFAULT '[]',
    diet_keywords JSONB NOT NULL DEFAULT '[]',
    allergen_keywords JSONB NOT NULL DEFAULT '[]',
    equipment JSONB NOT NULL DEFAULT '[]',
    season JSONB NOT NULL DEFAULT '[]',
    category_labels JSONB NOT NULL DEFAULT '[]',
    classification_source VARCHAR(30) NOT NULL DEFAULT 'RULE'
        CHECK (classification_source IN ('RULE', 'AI', 'ADMIN')),
    confidence_score NUMERIC(5, 2),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE recipe_media (
    media_id SERIAL PRIMARY KEY,
    recipe_id INTEGER NOT NULL REFERENCES recipes(recipe_id) ON DELETE CASCADE,
    step_id INTEGER REFERENCES recipe_steps(step_id) ON DELETE CASCADE,
    media_type VARCHAR(20) NOT NULL
        CHECK (media_type IN ('IMAGE', 'VIDEO')),
    image_role VARCHAR(30)
        CHECK (
            image_role IN (
                'MAIN',
                'THUMBNAIL',
                'STEP',
                'GALLERY',
                'GENERATED_CANDIDATE'
            )
        ),
    source_url VARCHAR(1024),
    storage_url VARCHAR(1024) NOT NULL,
    thumbnail_url VARCHAR(1024),
    width INTEGER,
    height INTEGER,
    file_size_bytes INTEGER,
    mime_type VARCHAR(100),
    storage_provider VARCHAR(30) NOT NULL DEFAULT 'S3',
    generation_id INTEGER,
    is_primary BOOLEAN NOT NULL DEFAULT false,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE recipe_image_generations (
    generation_id SERIAL PRIMARY KEY,
    recipe_id INTEGER NOT NULL REFERENCES recipes(recipe_id) ON DELETE CASCADE,
    requested_by_user_id INTEGER REFERENCES users(user_id) ON DELETE SET NULL,
    source_id INTEGER REFERENCES recipe_sources(source_id) ON DELETE SET NULL,
    provider VARCHAR(50) NOT NULL,
    model VARCHAR(100) NOT NULL,
    prompt TEXT NOT NULL,
    negative_prompt TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'REQUESTED'
        CHECK (
            status IN (
                'REQUESTED',
                'GENERATING',
                'SUCCEEDED',
                'FAILED',
                'SELECTED',
                'REJECTED'
            )
        ),
    generated_media_id INTEGER REFERENCES recipe_media(media_id) ON DELETE SET NULL,
    error_message TEXT,
    metadata JSONB NOT NULL DEFAULT '{}',
    requested_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP WITH TIME ZONE,
    selected_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE recipe_media
ADD CONSTRAINT fk_recipe_media_generation
FOREIGN KEY (generation_id)
REFERENCES recipe_image_generations(generation_id)
ON DELETE SET NULL;

CREATE TABLE recipe_embeddings (
    embedding_id SERIAL PRIMARY KEY,
    recipe_id INTEGER NOT NULL REFERENCES recipes(recipe_id) ON DELETE CASCADE,
    embedding_type VARCHAR(30) NOT NULL DEFAULT 'RECIPE_SEARCH'
        CHECK (embedding_type IN ('RECIPE_SEARCH', 'INGREDIENTS', 'STEPS')),
    model VARCHAR(100) NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    embedding VECTOR(1536) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (recipe_id, embedding_type, model, content_hash)
);

CREATE TABLE recipe_quality_scores (
    recipe_id INTEGER PRIMARY KEY REFERENCES recipes(recipe_id) ON DELETE CASCADE,
    completeness_score NUMERIC(5, 2),
    image_quality_score NUMERIC(5, 2),
    instruction_quality_score NUMERIC(5, 2),
    nutrition_confidence NUMERIC(5, 2),
    classification_confidence NUMERIC(5, 2),
    duplicate_score NUMERIC(5, 2),
    reviewed_by INTEGER REFERENCES users(user_id) ON DELETE SET NULL,
    reviewed_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE recipe_stats (
    recipe_id INTEGER PRIMARY KEY REFERENCES recipes(recipe_id) ON DELETE CASCADE,
    likes_count INTEGER NOT NULL DEFAULT 0 CHECK (likes_count >= 0),
    scrap_count INTEGER NOT NULL DEFAULT 0 CHECK (scrap_count >= 0)
);

CREATE TABLE user_recipes (
    user_recipe_id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    submission_text TEXT NOT NULL,
    draft_payload JSONB NOT NULL DEFAULT '{
        "description": null,
        "ingredients": [],
        "ingredients_raw": [],
        "instructions": [],
        "servings": null,
        "cooking_time_minutes": null,
        "kcal_per_serving": null,
        "difficulty": null,
        "category": [],
        "tags": [],
        "tips": [],
        "video_url": null,
        "image_url": null
    }',
    ai_suggested_patch JSONB NOT NULL DEFAULT '{
        "description": null,
        "ingredients": [],
        "ingredients_raw": [],
        "instructions": [],
        "servings": null,
        "cooking_time_minutes": null,
        "kcal_per_serving": null,
        "difficulty": null,
        "category": [],
        "tags": [],
        "tips": [],
        "video_url": null,
        "image_url": null
    }',
    validation_errors JSONB NOT NULL DEFAULT '[]',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    import_status VARCHAR(30) NOT NULL DEFAULT 'NOT_IMPORTED'
        CHECK (import_status IN ('NOT_IMPORTED', 'IMPORTED', 'FAILED')),
    is_active BOOLEAN NOT NULL DEFAULT true,
    admin_note TEXT,
    rejection_reason TEXT,
    reviewed_by INTEGER REFERENCES users(user_id) ON DELETE SET NULL,
    reviewed_at TIMESTAMP WITH TIME ZONE,
    imported_recipe_id INTEGER REFERENCES recipes(recipe_id) ON DELETE SET NULL,
    imported_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE chat_rooms (
    room_id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    title VARCHAR(100) DEFAULT '새로운 레시피 상담',
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE chat_messages (
    message_id SERIAL PRIMARY KEY,
    room_id INTEGER NOT NULL REFERENCES chat_rooms(room_id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL CHECK (role IN ('user', 'model')),
    content TEXT NOT NULL,
    image_url TEXT,
    recipe_ids JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE likes (
    like_id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    recipe_id INTEGER NOT NULL REFERENCES recipes(recipe_id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, recipe_id)
);

CREATE TABLE scraps (
    scrap_id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    recipe_id INTEGER NOT NULL REFERENCES recipes(recipe_id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, recipe_id)
);

CREATE OR REPLACE FUNCTION touch_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER touch_users_updated_at
BEFORE UPDATE ON users
FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

CREATE TRIGGER touch_user_profiles_updated_at
BEFORE UPDATE ON user_profiles
FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

CREATE TRIGGER touch_recipe_sources_updated_at
BEFORE UPDATE ON recipe_sources
FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

CREATE TRIGGER touch_recipe_source_extractions_updated_at
BEFORE UPDATE ON recipe_source_extractions
FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

CREATE TRIGGER touch_recipe_source_quality_scores_updated_at
BEFORE UPDATE ON recipe_source_quality_scores
FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

CREATE TRIGGER touch_recipes_updated_at
BEFORE UPDATE ON recipes
FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

CREATE TRIGGER touch_recipe_classifications_updated_at
BEFORE UPDATE ON recipe_classifications
FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

CREATE TRIGGER touch_recipe_nutrition_updated_at
BEFORE UPDATE ON recipe_nutrition
FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

CREATE TRIGGER touch_recipe_image_generations_updated_at
BEFORE UPDATE ON recipe_image_generations
FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

CREATE TRIGGER touch_recipe_quality_scores_updated_at
BEFORE UPDATE ON recipe_quality_scores
FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

-- 정정: DBv5.sql 의 touch_pending_recipes_updated_at 는 존재하지 않는 테이블 참조 → user_recipes 로 정정
CREATE TRIGGER touch_user_recipes_updated_at
BEFORE UPDATE ON user_recipes
FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

CREATE TRIGGER touch_chat_rooms_updated_at
BEFORE UPDATE ON chat_rooms
FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

CREATE OR REPLACE FUNCTION create_recipe_dependents()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO recipe_stats (recipe_id)
    VALUES (NEW.recipe_id)
    ON CONFLICT (recipe_id) DO NOTHING;

    INSERT INTO recipe_classifications (recipe_id)
    VALUES (NEW.recipe_id)
    ON CONFLICT (recipe_id) DO NOTHING;

    INSERT INTO recipe_quality_scores (recipe_id)
    VALUES (NEW.recipe_id)
    ON CONFLICT (recipe_id) DO NOTHING;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_create_recipe_dependents
AFTER INSERT ON recipes
FOR EACH ROW EXECUTE FUNCTION create_recipe_dependents();

CREATE OR REPLACE FUNCTION update_likes_count()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        INSERT INTO recipe_stats (recipe_id, likes_count, scrap_count)
        VALUES (NEW.recipe_id, 1, 0)
        ON CONFLICT (recipe_id)
        DO UPDATE SET likes_count = recipe_stats.likes_count + 1;
    ELSIF TG_OP = 'DELETE' THEN
        UPDATE recipe_stats
        SET likes_count = GREATEST(likes_count - 1, 0)
        WHERE recipe_id = OLD.recipe_id;
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
        ON CONFLICT (recipe_id)
        DO UPDATE SET scrap_count = recipe_stats.scrap_count + 1;
    ELSIF TG_OP = 'DELETE' THEN
        UPDATE recipe_stats
        SET scrap_count = GREATEST(scrap_count - 1, 0)
        WHERE recipe_id = OLD.recipe_id;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_scrap_count
AFTER INSERT OR DELETE ON scraps
FOR EACH ROW EXECUTE FUNCTION update_scrap_count();

CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_nickname ON users(nickname);
-- 정정: DBv5.sql 의 idx_social_accounts_user_id 는 존재하지 않는 테이블 참조 → user_identities 로 정정
CREATE INDEX idx_user_identities_user_id ON user_identities(user_id);

CREATE INDEX idx_recipe_sources_lifecycle
ON recipe_sources(parse_status, review_status, import_status, source_id DESC);
CREATE INDEX idx_recipe_sources_hash ON recipe_sources(raw_content_hash);

CREATE INDEX idx_source_extracted_ingredients_name
ON recipe_source_extracted_ingredients(normalized_name);
CREATE INDEX idx_source_extracted_labels_extraction
ON recipe_source_extracted_labels(extraction_id, label_type);

CREATE INDEX idx_recipes_active_id ON recipes(is_active, recipe_id DESC);
CREATE INDEX idx_recipes_classification_status
ON recipes(classification_status, recipe_id DESC);

CREATE INDEX idx_recipe_ingredients_recipe_id ON recipe_ingredients(recipe_id);
CREATE INDEX idx_recipe_ingredients_name ON recipe_ingredients(normalized_name);
CREATE INDEX idx_recipe_labels_recipe_type ON recipe_labels(recipe_id, label_type);
CREATE INDEX idx_recipe_nutrition_kcal ON recipe_nutrition(kcal_per_serving);
CREATE INDEX idx_recipe_nutrition_sodium ON recipe_nutrition(sodium_milligrams);

CREATE INDEX idx_recipe_classifications_cuisine
ON recipe_classifications(cuisine_type);
CREATE INDEX idx_recipe_classifications_dish
ON recipe_classifications(dish_type);
CREATE INDEX idx_recipe_classifications_main_ingredients_gin
ON recipe_classifications USING GIN (main_ingredients);
CREATE INDEX idx_recipe_classifications_keywords_gin
ON recipe_classifications USING GIN (taste_keywords);
CREATE INDEX idx_recipe_classifications_categories_gin
ON recipe_classifications USING GIN (category_labels);

CREATE INDEX idx_recipe_media_recipe_role
ON recipe_media(recipe_id, image_role, is_primary);
CREATE INDEX idx_recipe_image_generations_recipe_status
ON recipe_image_generations(recipe_id, status, requested_at DESC);
CREATE INDEX idx_recipe_embeddings_recipe_type
ON recipe_embeddings(recipe_id, embedding_type);

CREATE INDEX idx_chat_rooms_user_active_updated
ON chat_rooms(user_id, is_active, updated_at DESC);
CREATE INDEX idx_chat_messages_room_created ON chat_messages(room_id, created_at);
CREATE INDEX idx_user_recipes_user_status_created
ON user_recipes(user_id, is_active, status, user_recipe_id DESC);
CREATE INDEX idx_likes_recipe_id ON likes(recipe_id);
CREATE INDEX idx_scraps_recipe_id ON scraps(recipe_id);
CREATE INDEX idx_scraps_user_created ON scraps(user_id, scrap_id DESC);
