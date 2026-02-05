-- V7__add_sale_delivery_relationship.sql
-- Makes sale ↔ delivery a proper bidirectional JPA @OneToOne relationship
-- Preserves existing data, adds FK constraint, renames column if needed

-- ==========================================================
-- STEP 1: Rename saleId → sale_id (standard snake_case) if it still exists
-- ==========================================================

SET @col_exists = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'deliveries'
      AND COLUMN_NAME = 'saleId'
);

SET @rename_stmt = IF(@col_exists > 0,
    'ALTER TABLE deliveries CHANGE COLUMN saleId sale_id BIGINT NOT NULL',
    'SELECT "Column already renamed or does not exist" AS msg'
);

PREPARE stmt FROM @rename_stmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ==========================================================
-- STEP 2: Ensure sale_id is NOT NULL (should already be, but enforce)
-- ==========================================================

ALTER TABLE deliveries
    MODIFY COLUMN sale_id BIGINT NOT NULL
    COMMENT 'Foreign key to sale(id) - required for JPA @OneToOne';

-- ==========================================================
-- STEP 3: Drop any existing FK on sale_id (in case of previous manual attempts)
-- ==========================================================

SET @fk_exists = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'deliveries'
      AND CONSTRAINT_NAME = 'fk_delivery_sale'
      AND CONSTRAINT_TYPE = 'FOREIGN KEY'
);

SET @drop_fk = IF(@fk_exists > 0,
    'ALTER TABLE deliveries DROP FOREIGN KEY fk_delivery_sale',
    'SELECT "No old FK to drop" AS msg'
);

PREPARE stmt FROM @drop_fk;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ==========================================================
-- STEP 4: Add proper foreign key constraint
-- ==========================================================

ALTER TABLE deliveries
    ADD CONSTRAINT fk_delivery_sale
    FOREIGN KEY (sale_id)
    REFERENCES sale(id)
    ON DELETE CASCADE          -- or SET NULL / RESTRICT based on your business rule
    ON UPDATE CASCADE;

-- ==========================================================
-- STEP 5: Add index on sale_id (for fast joins)
-- ==========================================================

SET @idx_exists = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'deliveries'
      AND INDEX_NAME = 'idx_delivery_sale_id'
);

SET @create_idx = IF(@idx_exists = 0,
    'CREATE INDEX idx_delivery_sale_id ON deliveries(sale_id)',
    'SELECT "Index already exists" AS msg'
);

PREPARE stmt FROM @create_idx;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ==========================================================
-- STEP 6: Optional - charset/collation safety (recommended)
-- ==========================================================

ALTER TABLE deliveries
    CONVERT TO CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

-- End of migration