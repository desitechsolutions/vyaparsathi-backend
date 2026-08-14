-- Migration V77: Soft-delete flags for item and item_variant.
--
-- Business rule: variants that have stock or have been sold must never
-- be hard-deleted. Historical sale_item rows continue to reference the
-- variant by FK, and reports (sales history, GST filings) rely on those
-- references resolving. So we soft-delete instead: flip `active` to
-- false, keep the row in place.
--
-- Both columns default to TRUE so existing rows stay visible.

SET @dbname = DATABASE();

DELIMITER $$

DROP PROCEDURE IF EXISTS add_active_if_missing $$
CREATE PROCEDURE add_active_if_missing(IN tbl VARCHAR(64))
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = tbl AND COLUMN_NAME = 'active'
  ) THEN
    SET @s = CONCAT('ALTER TABLE `', tbl, '` ADD COLUMN `active` BOOLEAN NOT NULL DEFAULT TRUE');
    PREPARE stmt FROM @s;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END $$

DROP PROCEDURE IF EXISTS add_index_if_missing_v77 $$
CREATE PROCEDURE add_index_if_missing_v77(IN tbl VARCHAR(64), IN idx VARCHAR(64), IN col VARCHAR(64))
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

CALL add_active_if_missing('item');
CALL add_active_if_missing('item_variant');

CALL add_index_if_missing_v77('item',         'idx_item_active',         'active');
CALL add_index_if_missing_v77('item_variant', 'idx_item_variant_active', 'active');

DROP PROCEDURE IF EXISTS add_active_if_missing;
DROP PROCEDURE IF EXISTS add_index_if_missing_v77;
