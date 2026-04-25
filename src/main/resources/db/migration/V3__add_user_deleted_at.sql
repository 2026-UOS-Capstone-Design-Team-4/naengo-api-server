-- ============================================================
-- V3: 회원 탈퇴 익명화 지원
-- 탈퇴 시 PII 를 nullify 하고 deleted_at 에 타임스탬프를 찍는다.
-- 구체 정책: docs/api-server-tasks.md §5 참조
-- ============================================================

ALTER TABLE users
    ADD COLUMN deleted_at TIMESTAMPTZ NULL;

CREATE INDEX idx_users_deleted_at ON users(deleted_at);
