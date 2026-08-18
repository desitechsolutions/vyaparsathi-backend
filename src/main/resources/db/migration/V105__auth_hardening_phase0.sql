-- =====================================================================
-- V106 — Auth Hardening (Phase 0)
-- =====================================================================
-- 1. password_reset_tokens: rename `token` -> `token_hash` (SHA-256 hex,
--    64 chars). Existing rows are wiped: their raw UUIDs would never
--    match a hash-based lookup, so keeping them is worse than useless.
-- 2. users: add failed_login_attempts, locked_until, email verification
--    columns to prepare for Phase 1 (lockout) and Phase 2 (email verify).
--
-- All statements are wrapped in INFORMATION_SCHEMA-guarded procedures so
-- the migration is idempotent on partial reruns (MySQL doesn't support
-- IF NOT EXISTS on ADD COLUMN).
-- =====================================================================

DROP PROCEDURE IF EXISTS v106_add_col;
DROP PROCEDURE IF EXISTS v106_drop_col;
DROP PROCEDURE IF EXISTS v106_add_index;

DELIMITER $$

CREATE PROCEDURE v106_add_col(
    IN p_table  VARCHAR(64),
    IN p_column VARCHAR(64),
    IN p_ddl    VARCHAR(1024)
)
BEGIN
    IF EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table
    )
    AND NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = p_table
          AND COLUMN_NAME = p_column
    )
    THEN
        SET @sql = CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN ', p_ddl);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

CREATE PROCEDURE v106_drop_col(
    IN p_table  VARCHAR(64),
    IN p_column VARCHAR(64)
)
BEGIN
    IF EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = p_table
          AND COLUMN_NAME = p_column
    )
    THEN
        SET @sql = CONCAT('ALTER TABLE `', p_table, '` DROP COLUMN `', p_column, '`');
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

CREATE PROCEDURE v106_add_index(
    IN p_table VARCHAR(64),
    IN p_index VARCHAR(64),
    IN p_cols  VARCHAR(256)
)
BEGIN
    IF EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table
    )
    AND NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = p_table
          AND INDEX_NAME = p_index
    )
    THEN
        SET @sql = CONCAT('CREATE INDEX `', p_index, '` ON `', p_table, '` (', p_cols, ')');
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

DELIMITER ;

-- ---------------------------------------------------------------------
-- 1. password_reset_tokens: move to hashed tokens
-- ---------------------------------------------------------------------
-- Clear any pre-existing rows; their raw tokens will never match hashes.
DELETE FROM password_reset_tokens WHERE 1 = 1;

-- Add the new token_hash column (nullable temporarily so ADD COLUMN succeeds
-- on tables that have never been populated, then made NOT NULL below).
CALL v106_add_col(
    'password_reset_tokens',
    'token_hash',
    '`token_hash` VARCHAR(64) NULL AFTER `id`'
);

-- Drop the old plaintext token column if present.
CALL v106_drop_col('password_reset_tokens', 'token');

-- Enforce NOT NULL + UNIQUE now that no legacy rows survive.
ALTER TABLE password_reset_tokens
    MODIFY COLUMN token_hash VARCHAR(64) NOT NULL;

CALL v106_add_index(
    'password_reset_tokens',
    'uk_password_reset_tokens_token_hash',
    '`token_hash`'
);

-- ---------------------------------------------------------------------
-- 2. users: lockout + email verification columns
-- ---------------------------------------------------------------------
CALL v106_add_col(
    'users',
    'failed_login_attempts',
    '`failed_login_attempts` INT NOT NULL DEFAULT 0'
);

CALL v106_add_col(
    'users',
    'locked_until',
    '`locked_until` DATETIME NULL'
);

CALL v106_add_col(
    'users',
    'email_verified',
    '`email_verified` BOOLEAN NOT NULL DEFAULT FALSE'
);

CALL v106_add_col(
    'users',
    'email_verification_token_hash',
    '`email_verification_token_hash` VARCHAR(64) NULL'
);

CALL v106_add_col(
    'users',
    'email_verification_expiry',
    '`email_verification_expiry` DATETIME NULL'
);

CALL v106_add_index(
    'users',
    'idx_users_locked_until',
    '`locked_until`'
);

CALL v106_add_index(
    'users',
    'idx_users_email_verification_token_hash',
    '`email_verification_token_hash`'
);

-- Backfill: existing users are treated as already-verified so the Phase 2
-- email-verification gate doesn't lock them out at rollout time. Users
-- created after this migration get email_verified=false and must confirm.
UPDATE users SET email_verified = TRUE WHERE email_verified = FALSE;

-- Cleanup helper procedures.
DROP PROCEDURE IF EXISTS v106_add_col;
DROP PROCEDURE IF EXISTS v106_drop_col;
DROP PROCEDURE IF EXISTS v106_add_index;
