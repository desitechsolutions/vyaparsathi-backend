-- ============================================================================
-- V4__onboarding_and_category_fixes.sql
-- MySQL 8.x / 9.x compatible
-- Date: Jan 2026
-- ============================================================================

/* ============================================================================
   PART 1: users.shop_id nullable + FK safe handling
   ============================================================================ */

-- Drop FK if exists
SET @fk_users_shop := (
    SELECT CONSTRAINT_NAME
    FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'users'
      AND COLUMN_NAME = 'shop_id'
      AND REFERENCED_TABLE_NAME = 'shop'
    LIMIT 1
);

SET @sql := IF(@fk_users_shop IS NOT NULL,
    CONCAT('ALTER TABLE users DROP FOREIGN KEY ', @fk_users_shop),
    'SELECT 1'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Make shop_id nullable
ALTER TABLE users
    MODIFY COLUMN shop_id BIGINT NULL;

-- Re-add FK
ALTER TABLE users
    ADD CONSTRAINT FK_users_shop
    FOREIGN KEY (shop_id)
    REFERENCES shop(id)
    ON DELETE SET NULL
    ON UPDATE CASCADE;

-- Comment
ALTER TABLE users
    MODIFY COLUMN shop_id BIGINT NULL
    COMMENT 'Nullable during registration/onboarding; required after shop assignment';


/* ============================================================================
   PART 2: categories.shop_id (safe add + FK)
   ============================================================================ */

-- Add shop_id column only if missing
SET @col_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'categories'
      AND COLUMN_NAME = 'shop_id'
);

SET @sql := IF(@col_exists = 0,
    'ALTER TABLE categories ADD COLUMN shop_id BIGINT NULL AFTER parent_id',
    'SELECT 1'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Populate NULL shop_id (change 1 to valid shop id if needed)
UPDATE categories SET shop_id = 1 WHERE shop_id IS NULL;

-- Make NOT NULL
ALTER TABLE categories
    MODIFY COLUMN shop_id BIGINT NOT NULL;

-- Drop FK if exists
SET @fk_cat_shop := (
    SELECT CONSTRAINT_NAME
    FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'categories'
      AND COLUMN_NAME = 'shop_id'
      AND REFERENCED_TABLE_NAME = 'shop'
    LIMIT 1
);

SET @sql := IF(@fk_cat_shop IS NOT NULL,
    CONCAT('ALTER TABLE categories DROP FOREIGN KEY ', @fk_cat_shop),
    'SELECT 1'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Add FK
ALTER TABLE categories
    ADD CONSTRAINT fk_categories_shop
    FOREIGN KEY (shop_id)
    REFERENCES shop(id)
    ON DELETE CASCADE
    ON UPDATE CASCADE;


/* ============================================================================
   PART 3: Indexes (MySQL-safe)
   ============================================================================ */

-- shop_id index
SET @idx_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'categories'
      AND INDEX_NAME = 'idx_categories_shop_id'
);

SET @sql := IF(@idx_exists = 0,
    'CREATE INDEX idx_categories_shop_id ON categories(shop_id)',
    'SELECT 1'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- unique (name, shop_id)
SET @idx_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'categories'
      AND INDEX_NAME = 'idx_categories_name_shop_unique'
);

SET @sql := IF(@idx_exists = 0,
    'CREATE UNIQUE INDEX idx_categories_name_shop_unique ON categories(name, shop_id)',
    'SELECT 1'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


/* ============================================================================
   PART 4: level column + hierarchy calculation
   ============================================================================ */

-- Add level column if missing
SET @level_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'categories'
      AND COLUMN_NAME = 'level'
);

SET @sql := IF(@level_exists = 0,
    'ALTER TABLE categories ADD COLUMN level INT DEFAULT 0 AFTER shop_id',
    'SELECT 1'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Populate level safely (MySQL 8+)
WITH RECURSIVE cat_tree AS (
    SELECT id, parent_id, 0 AS lvl
    FROM categories
    WHERE parent_id IS NULL
    UNION ALL
    SELECT c.id, c.parent_id, ct.lvl + 1
    FROM categories c
    JOIN cat_tree ct ON c.parent_id = ct.id
)
UPDATE categories c
JOIN cat_tree t ON c.id = t.id
SET c.level = t.lvl;


/* ============================================================================
   PART 5: Charset consistency
   ============================================================================ */

ALTER TABLE users
    CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

ALTER TABLE categories
    CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- ========================== END OF MIGRATION ================================
