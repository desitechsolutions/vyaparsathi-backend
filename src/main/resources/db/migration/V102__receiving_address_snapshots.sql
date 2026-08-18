-- =====================================================================
-- V102 — Fill the V99 gap on `receiving`
-- =====================================================================
-- The Receiving JPA entity carries `bill_to_party_snapshot` and
-- `ship_to_party_snapshot` (matches the pattern on `sale` and
-- `purchase_order`), but V99 forgot to add these columns to the
-- `receiving` table. Any query that touches Receiving — including the
-- `purchase_return` list (JOIN receiving) — fails with
-- "Unknown column 'r1_0.bill_to_party_snapshot' in 'field list'".
-- Idempotent INFORMATION_SCHEMA-guarded adds so re-running is safe.
-- =====================================================================

SET @dbn := DATABASE();

SET @sql := (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = @dbn
        AND TABLE_NAME = 'receiving'
        AND COLUMN_NAME = 'bill_to_party_snapshot') = 0,
    'ALTER TABLE `receiving` ADD COLUMN `bill_to_party_snapshot` TEXT NULL',
    'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = @dbn
        AND TABLE_NAME = 'receiving'
        AND COLUMN_NAME = 'ship_to_party_snapshot') = 0,
    'ALTER TABLE `receiving` ADD COLUMN `ship_to_party_snapshot` TEXT NULL',
    'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
