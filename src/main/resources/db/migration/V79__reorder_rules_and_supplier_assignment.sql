-- Migration V79: Enterprise reorder rules + explicit supplier assignment on
-- item_variant.
--
-- Motivation: the low-stock alerts page has been carrying a single
-- `low_stock_threshold` column and inferring the "usual supplier" from the
-- most recent purchase order. Enterprise SaaS (Zoho / NetSuite / Cin7)
-- separates these into distinct concepts:
--
--   * reorder_point       — level at which to trigger a reorder. Not the
--                           same as safety_stock (the buffer below it).
--                           If null, existing low_stock_threshold is used.
--   * reorder_qty         — fixed quantity to buy each time. When set,
--                           overrides the velocity-based suggestion.
--   * safety_stock        — buffer kept for demand uncertainty.
--   * max_stock           — cap on how much we want on hand.
--   * lead_time_days      — days between PO and receipt; feeds the
--                           suggested-qty formula: cover = avg_daily × lead_time.
--   * preferred_supplier_id — first-choice supplier for this variant.
--   * backup_supplier_id  — fallback when the preferred one is unavailable.
--
-- All columns are nullable so a shop that never configures reorder rules
-- keeps the current "threshold-only" behavior. FKs use ON DELETE SET NULL
-- so removing a supplier does not orphan a variant.
--
-- Guarded via the INFORMATION_SCHEMA pattern used by V76/V77/V78 (see the
-- MySQL migration pitfalls memory note) so the migration is re-runnable.

SET @dbname = DATABASE();

DELIMITER $$

DROP PROCEDURE IF EXISTS add_col_if_missing_v79 $$
CREATE PROCEDURE add_col_if_missing_v79(IN tbl VARCHAR(64), IN col VARCHAR(64), IN col_def VARCHAR(255))
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = tbl AND COLUMN_NAME = col
  ) THEN
    SET @s = CONCAT('ALTER TABLE `', tbl, '` ADD COLUMN `', col, '` ', col_def);
    PREPARE stmt FROM @s;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END $$

DROP PROCEDURE IF EXISTS add_fk_if_missing_v79 $$
CREATE PROCEDURE add_fk_if_missing_v79(IN tbl VARCHAR(64), IN fk_name VARCHAR(64),
                                       IN col VARCHAR(64), IN ref_tbl VARCHAR(64),
                                       IN ref_col VARCHAR(64))
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = tbl
      AND CONSTRAINT_NAME = fk_name AND CONSTRAINT_TYPE = 'FOREIGN KEY'
  ) THEN
    SET @s = CONCAT('ALTER TABLE `', tbl, '` ADD CONSTRAINT `', fk_name,
                    '` FOREIGN KEY (`', col, '`) REFERENCES `', ref_tbl,
                    '` (`', ref_col, '`) ON DELETE SET NULL');
    PREPARE stmt FROM @s;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END $$

DROP PROCEDURE IF EXISTS add_index_if_missing_v79 $$
CREATE PROCEDURE add_index_if_missing_v79(IN tbl VARCHAR(64), IN idx VARCHAR(64), IN col VARCHAR(64))
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = tbl AND INDEX_NAME = idx
  ) THEN
    SET @s = CONCAT('CREATE INDEX `', idx, '` ON `', tbl, '` (`', col, '`)');
    PREPARE stmt FROM @s;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END $$

DELIMITER ;

-- 1. Reorder rule columns
CALL add_col_if_missing_v79('item_variant', 'reorder_point',   'DECIMAL(12,2) DEFAULT NULL');
CALL add_col_if_missing_v79('item_variant', 'reorder_qty',     'DECIMAL(12,2) DEFAULT NULL');
CALL add_col_if_missing_v79('item_variant', 'safety_stock',    'DECIMAL(12,2) DEFAULT NULL');
CALL add_col_if_missing_v79('item_variant', 'max_stock',       'DECIMAL(12,2) DEFAULT NULL');
CALL add_col_if_missing_v79('item_variant', 'lead_time_days',  'INT DEFAULT NULL');

-- 2. Preferred + backup supplier columns
CALL add_col_if_missing_v79('item_variant', 'preferred_supplier_id', 'BIGINT DEFAULT NULL');
CALL add_col_if_missing_v79('item_variant', 'backup_supplier_id',    'BIGINT DEFAULT NULL');

-- 3. Foreign keys — ON DELETE SET NULL so a removed supplier doesn't cascade
--    into deleting the variant catalog.
CALL add_fk_if_missing_v79('item_variant', 'fk_item_variant_preferred_supplier',
                            'preferred_supplier_id', 'supplier', 'id');
CALL add_fk_if_missing_v79('item_variant', 'fk_item_variant_backup_supplier',
                            'backup_supplier_id', 'supplier', 'id');

-- 4. Indexes for the "which variants supply from X" query pattern used by
--    the supplier-detail view.
CALL add_index_if_missing_v79('item_variant', 'idx_item_variant_preferred_supplier',
                               'preferred_supplier_id');
CALL add_index_if_missing_v79('item_variant', 'idx_item_variant_backup_supplier',
                               'backup_supplier_id');

DROP PROCEDURE IF EXISTS add_col_if_missing_v79;
DROP PROCEDURE IF EXISTS add_fk_if_missing_v79;
DROP PROCEDURE IF EXISTS add_index_if_missing_v79;
