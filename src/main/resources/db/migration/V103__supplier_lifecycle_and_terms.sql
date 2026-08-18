-- =====================================================================
-- V103 — Enterprise Supplier fields
-- =====================================================================
-- Adds the fields the enterprise Supplier module needs but the legacy
-- schema was missing:
--   * `active`         — soft on/off flag (never physically delete a
--                        supplier that has posted transactions).
--   * `credit_days`    — default net-N payment terms in days.
--   * `credit_limit`   — max outstanding payable we're willing to carry.
--   * `payment_terms`  — free-text override (e.g. "50% advance, 50% NET30").
-- All idempotent via INFORMATION_SCHEMA guards so re-runs are safe.
-- =====================================================================

SET @dbn := DATABASE();

SET @sql := (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = @dbn
        AND TABLE_NAME = 'supplier'
        AND COLUMN_NAME = 'active') = 0,
    'ALTER TABLE `supplier` ADD COLUMN `active` TINYINT(1) NOT NULL DEFAULT 1',
    'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = @dbn
        AND TABLE_NAME = 'supplier'
        AND COLUMN_NAME = 'credit_days') = 0,
    'ALTER TABLE `supplier` ADD COLUMN `credit_days` INT NULL',
    'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = @dbn
        AND TABLE_NAME = 'supplier'
        AND COLUMN_NAME = 'credit_limit') = 0,
    'ALTER TABLE `supplier` ADD COLUMN `credit_limit` DECIMAL(14,2) NULL',
    'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = @dbn
        AND TABLE_NAME = 'supplier'
        AND COLUMN_NAME = 'payment_terms') = 0,
    'ALTER TABLE `supplier` ADD COLUMN `payment_terms` VARCHAR(200) NULL',
    'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = @dbn
        AND TABLE_NAME = 'supplier'
        AND COLUMN_NAME = 'notes') = 0,
    'ALTER TABLE `supplier` ADD COLUMN `notes` TEXT NULL',
    'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
      WHERE TABLE_SCHEMA = @dbn
        AND TABLE_NAME = 'supplier'
        AND INDEX_NAME = 'idx_supplier_active') = 0,
    'CREATE INDEX idx_supplier_active ON supplier (shop_id, active)',
    'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
