-- =====================================================================
-- V104 — Enterprise Customer CRM fields
-- =====================================================================
-- Brings customer up to full SaaS-CRM parity with the supplier module:
--   * active              — soft on/off flag
--   * customer_type       — INDIVIDUAL / BUSINESS
--   * credit_days         — default net-N receivable terms
--   * credit_limit        — max outstanding receivable we'll extend
--   * payment_terms       — free-text override
--   * tags                — comma-separated tags for segmentation
--   * trade_name          — display name distinct from legal
--   * date_of_birth       — for individual customers (birthday marketing)
--   * anniversary_date    — same
--   * industry            — for B2B customers
--   * source              — WALK_IN / REFERRAL / ONLINE / MARKETING / OTHER
--
-- All idempotent via INFORMATION_SCHEMA guards so re-runs are safe.
-- =====================================================================

SET @dbn := DATABASE();

SET @sql := (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = @dbn AND TABLE_NAME = 'customer' AND COLUMN_NAME = 'active') = 0,
    'ALTER TABLE `customer` ADD COLUMN `active` TINYINT(1) NOT NULL DEFAULT 1',
    'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = @dbn AND TABLE_NAME = 'customer' AND COLUMN_NAME = 'customer_type') = 0,
    'ALTER TABLE `customer` ADD COLUMN `customer_type` VARCHAR(20) NOT NULL DEFAULT ''INDIVIDUAL''',
    'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = @dbn AND TABLE_NAME = 'customer' AND COLUMN_NAME = 'credit_days') = 0,
    'ALTER TABLE `customer` ADD COLUMN `credit_days` INT NULL',
    'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = @dbn AND TABLE_NAME = 'customer' AND COLUMN_NAME = 'credit_limit') = 0,
    'ALTER TABLE `customer` ADD COLUMN `credit_limit` DECIMAL(14,2) NULL',
    'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = @dbn AND TABLE_NAME = 'customer' AND COLUMN_NAME = 'payment_terms') = 0,
    'ALTER TABLE `customer` ADD COLUMN `payment_terms` VARCHAR(200) NULL',
    'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = @dbn AND TABLE_NAME = 'customer' AND COLUMN_NAME = 'tags') = 0,
    'ALTER TABLE `customer` ADD COLUMN `tags` VARCHAR(500) NULL',
    'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = @dbn AND TABLE_NAME = 'customer' AND COLUMN_NAME = 'trade_name') = 0,
    'ALTER TABLE `customer` ADD COLUMN `trade_name` VARCHAR(255) NULL',
    'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = @dbn AND TABLE_NAME = 'customer' AND COLUMN_NAME = 'date_of_birth') = 0,
    'ALTER TABLE `customer` ADD COLUMN `date_of_birth` DATE NULL',
    'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = @dbn AND TABLE_NAME = 'customer' AND COLUMN_NAME = 'anniversary_date') = 0,
    'ALTER TABLE `customer` ADD COLUMN `anniversary_date` DATE NULL',
    'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = @dbn AND TABLE_NAME = 'customer' AND COLUMN_NAME = 'industry') = 0,
    'ALTER TABLE `customer` ADD COLUMN `industry` VARCHAR(100) NULL',
    'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = @dbn AND TABLE_NAME = 'customer' AND COLUMN_NAME = 'source') = 0,
    'ALTER TABLE `customer` ADD COLUMN `source` VARCHAR(30) NULL',
    'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
      WHERE TABLE_SCHEMA = @dbn AND TABLE_NAME = 'customer' AND INDEX_NAME = 'idx_customer_active') = 0,
    'CREATE INDEX idx_customer_active ON customer (shop_id, active)',
    'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
