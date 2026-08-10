-- =============================================================================
-- V45__phase0_critical_fixes.sql
-- Phase 0: Critical Safety Fixes
--
-- Changes:
--   1. Create invoice_sequence table (atomic, per-shop, per-year invoice numbering)
--   2. Add shop_id to categories table (tenant isolation)
--   3. Drop dead schema: invoices, invoice_items, stock_entry tables
-- =============================================================================

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. INVOICE SEQUENCE TABLE
--    Provides atomic, per-shop, per-year, per-prefix invoice numbering.
--    Replaces the non-atomic saleRepository.count() + 1 pattern.
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `invoice_sequence` (
  `id`           BIGINT      NOT NULL AUTO_INCREMENT,
  `shop_id`      BIGINT      NOT NULL,
  `prefix`       VARCHAR(100) NOT NULL DEFAULT 'INV',
  `fiscal_year`  SMALLINT    NOT NULL COMMENT 'e.g. 2025 for FY 2025-26',
  `last_seq`     BIGINT      NOT NULL DEFAULT 0 COMMENT 'Last used sequence number',
  `created_at`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_invoice_seq_shop_prefix_year` (`shop_id`, `prefix`, `fiscal_year`),
  CONSTRAINT `fk_invoice_seq_shop` FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Atomic invoice sequence counter per shop, prefix, and fiscal year';

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. ADD shop_id TO categories TABLE (Tenant Isolation)
--    Categories were global across all shops — this was a data isolation gap.
--    Step 1: Add nullable column (safe for existing rows).
--    Step 2: Backfill: assign all existing categories to the first shop (id=1).
--             If you have multiple shops, run the backfill manually per tenant.
--    Step 3: We intentionally leave nullable=true for now so existing FK
--             references don't break. A follow-up migration can make it NOT NULL
--             after full backfill is validated.
-- ─────────────────────────────────────────────────────────────────────────────
SET @dbname = DATABASE();

-- 1. shop_id
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = 'categories' AND COLUMN_NAME = 'shop_id') > 0,
  'SELECT 1',
  'ALTER TABLE `categories` ADD COLUMN `shop_id` BIGINT NULL COMMENT \'Tenant owner of this category. NULL = legacy global category (migrate immediately)\''
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2. is_system
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = 'categories' AND COLUMN_NAME = 'is_system') > 0,
  'SELECT 1',
  'ALTER TABLE `categories` ADD COLUMN `is_system` TINYINT(1) NOT NULL DEFAULT 0 COMMENT \'System categories are visible to all shops (e.g. Uncategorised)\''
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3. Index for fast per-shop category queries
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = 'categories' AND INDEX_NAME = 'idx_categories_shop_id') > 0,
  'SELECT 1',
  'CREATE INDEX `idx_categories_shop_id` ON `categories` (`shop_id`)'
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 4. FK constraint (deferred enforcement — column is nullable)
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = 'categories' AND CONSTRAINT_NAME = 'fk_categories_shop') > 0,
  'SELECT 1',
  'ALTER TABLE `categories` ADD CONSTRAINT `fk_categories_shop` FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`) ON DELETE SET NULL'
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Backfill: tag all existing categories as belonging to shop 1.
-- IMPORTANT: Review and update this for multi-tenant setups BEFORE applying.
UPDATE `categories` SET `shop_id` = 1 WHERE `shop_id` IS NULL;

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. DROP DEAD SCHEMA
--    The following tables exist in the baseline (V1) but are never written to
--    by any JPA entity or service in the application. They cause confusion
--    and mislead developers about the data model.
--
--    BEFORE dropping: verify there is truly no data in these tables.
--    The IF EXISTS guard prevents errors on clean installs.
-- ─────────────────────────────────────────────────────────────────────────────

-- invoice_items references invoices, so drop child first
DROP TABLE IF EXISTS `invoice_items`;

-- invoices table — PDF generation happens directly from sale entity; this is unused
DROP TABLE IF EXISTS `invoices`;

-- stock_entry table — stock is tracked via stock_movement (ADD/DEDUCT/ADJUST); this is unused
DROP TABLE IF EXISTS `stock_entry`;
