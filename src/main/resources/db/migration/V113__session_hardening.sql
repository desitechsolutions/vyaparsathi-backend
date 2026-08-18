-- V113: Phase 6 session hardening
--
-- Extends refresh_token into the canonical "user session" record
-- (one row = one active browser/device) and introduces a small
-- denylist table that JwtAuthenticationFilter consults on every
-- authenticated request so a revoked session's still-valid access
-- token dies within milliseconds instead of waiting for its expiry.
--
-- Existing schema: refresh_token has (token, username, expiry_date,
-- revoked, shop_id, created_at, updated_at). We add device metadata
-- and a public session_id (UUID) that FE/BE both use to reference
-- the session. Old rows get a synthetic session_id via UUID().

-- ─── 1. Extend refresh_token with session/device columns ──────────
-- CREATE INDEX IF NOT EXISTS / ADD COLUMN IF NOT EXISTS are
-- PostgreSQL-only. On MySQL, guard via INFORMATION_SCHEMA so the
-- migration is idempotent across dev environments.

DROP PROCEDURE IF EXISTS v113_ensure_column;
DELIMITER $$
CREATE PROCEDURE v113_ensure_column(IN tbl VARCHAR(64), IN col VARCHAR(64), IN ddl TEXT)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = tbl AND COLUMN_NAME = col
    ) THEN
        SET @s = CONCAT('ALTER TABLE `', tbl, '` ADD COLUMN ', ddl);
        PREPARE stmt FROM @s;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

DROP PROCEDURE IF EXISTS v113_ensure_index;
DELIMITER $$
CREATE PROCEDURE v113_ensure_index(IN tbl VARCHAR(64), IN idx VARCHAR(64), IN cols VARCHAR(200))
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = tbl AND INDEX_NAME = idx
    ) THEN
        SET @s = CONCAT('CREATE INDEX `', idx, '` ON `', tbl, '` (', cols, ')');
        PREPARE stmt FROM @s;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

CALL v113_ensure_column('refresh_token', 'session_id',       'session_id VARCHAR(64) NULL');
CALL v113_ensure_column('refresh_token', 'device_label',     'device_label VARCHAR(255) NULL');
CALL v113_ensure_column('refresh_token', 'user_agent',       'user_agent VARCHAR(500) NULL');
CALL v113_ensure_column('refresh_token', 'ip_address',       'ip_address VARCHAR(64) NULL');
CALL v113_ensure_column('refresh_token', 'last_active_at',   'last_active_at DATETIME NULL');
CALL v113_ensure_column('refresh_token', 'revoked_at',       'revoked_at DATETIME NULL');

-- Backfill session_id for pre-existing rows so the unique index below
-- is safe to add. UUID() is fine here — this only runs once per row.
UPDATE refresh_token
   SET session_id = REPLACE(UUID(), '-', '')
 WHERE session_id IS NULL;

-- Enforce non-null + uniqueness once every row has a value.
ALTER TABLE refresh_token
    MODIFY COLUMN session_id VARCHAR(64) NOT NULL;

CALL v113_ensure_index('refresh_token', 'uk_refresh_token_session_id', 'session_id');
-- Convert idx → unique post-hoc. If the plain index was created first
-- above, drop it in favour of a unique one.
DROP PROCEDURE IF EXISTS v113_ensure_unique;
DELIMITER $$
CREATE PROCEDURE v113_ensure_unique(IN tbl VARCHAR(64), IN idx VARCHAR(64), IN cols VARCHAR(200))
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME  = tbl
          AND INDEX_NAME  = idx
          AND NON_UNIQUE  = 0
    ) THEN
        -- Drop non-unique dupe if present
        IF EXISTS (
            SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = tbl AND INDEX_NAME = idx
        ) THEN
            SET @d = CONCAT('DROP INDEX `', idx, '` ON `', tbl, '`');
            PREPARE stmt FROM @d;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;
        SET @s = CONCAT('CREATE UNIQUE INDEX `', idx, '` ON `', tbl, '` (', cols, ')');
        PREPARE stmt FROM @s;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

CALL v113_ensure_unique('refresh_token', 'uk_refresh_token_session_id', 'session_id');
CALL v113_ensure_index('refresh_token', 'idx_refresh_token_username_revoked', 'username, revoked_at');

-- Seed last_active_at for existing rows so the sessions list has
-- something sensible to show before the first refresh call updates it.
UPDATE refresh_token
   SET last_active_at = COALESCE(last_active_at, created_at)
 WHERE last_active_at IS NULL;

-- ─── 2. Session denylist ─────────────────────────────────────────
-- The filter checks this table (fronted by an in-memory cache in
-- SessionDenylistService) on every authenticated request. Entries
-- self-expire at expires_at — a scheduled sweep clears them out so
-- the table doesn't grow unbounded.
CREATE TABLE IF NOT EXISTS revoked_sessions (
    session_id  VARCHAR(64) NOT NULL,
    user_id     BIGINT      NULL,
    revoked_at  DATETIME    NOT NULL,
    expires_at  DATETIME    NOT NULL,
    reason      VARCHAR(64) NULL,
    PRIMARY KEY (session_id),
    KEY idx_revoked_sessions_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

DROP PROCEDURE IF EXISTS v113_ensure_column;
DROP PROCEDURE IF EXISTS v113_ensure_index;
DROP PROCEDURE IF EXISTS v113_ensure_unique;
