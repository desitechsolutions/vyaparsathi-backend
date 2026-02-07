-- V5__make_refresh_token_shop_nullable.sql
-- Compatible with MySQL 9.4.0
-- Make refresh_token.shop_id nullable (Flyway-safe & re-runnable)

-- ==========================================================
-- STEP 1: Drop FK if it exists
-- ==========================================================

SET @fk_exists = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'refresh_token'
      AND CONSTRAINT_NAME = 'fk_refresh_token_shop'
      AND CONSTRAINT_TYPE = 'FOREIGN KEY'
);

SET @drop_stmt = IF(@fk_exists > 0,
   'ALTER TABLE refresh_token DROP FOREIGN KEY fk_refresh_token_shop',
   'SELECT "FK does not exist, skipping drop" AS msg'
);

PREPARE stmt FROM @drop_stmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ==========================================================
-- STEP 2: Make shop_id nullable
-- ==========================================================

ALTER TABLE refresh_token
    MODIFY COLUMN shop_id BIGINT NULL
    COMMENT 'Nullable during login/onboarding; assigned after shop creation';

-- ==========================================================
-- STEP 3: Re-add FK allowing NULL
-- ==========================================================

ALTER TABLE refresh_token
    ADD CONSTRAINT fk_refresh_token_shop
    FOREIGN KEY (shop_id)
    REFERENCES shop(id)
    ON DELETE SET NULL
    ON UPDATE CASCADE;

-- ==========================================================
-- STEP 4: Create index ONLY IF MISSING (MySQL-safe)
-- ==========================================================

SET @idx_exists = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'refresh_token'
      AND INDEX_NAME = 'idx_refresh_token_shop_id'
);

SET @create_idx = IF(@idx_exists = 0,
   'CREATE INDEX idx_refresh_token_shop_id ON refresh_token(shop_id)',
   'SELECT "Index already exists" AS msg'
);

PREPARE stmt FROM @create_idx;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ==========================================================
-- STEP 5: Charset safety (recommended)
-- ==========================================================

ALTER TABLE refresh_token
    CONVERT TO CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

-- End of migration