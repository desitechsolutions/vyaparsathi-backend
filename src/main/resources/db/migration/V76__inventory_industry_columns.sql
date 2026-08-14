-- Migration V76: Enterprise multi-industry inventory columns.
--
-- Adds nullable columns to item_variant so JEWELLERY, ELECTRONICS and
-- AUTOMOBILE shops can persist industry-specific attributes that the
-- frontend has been collecting but the backend was silently dropping.
--
-- Also backfills item.attribute_1/2 from the legacy fabric/season
-- columns and drops those columns — the dual-write hack in
-- ItemMapper/ItemService is being retired in the same pass.
--
-- All new columns are nullable so a tenant that switches industries
-- keeps every existing row intact.

SET @dbname = DATABASE();

-- Helper: only add a column if it does not already exist. Wrapping every
-- ALTER TABLE with this makes the migration re-runnable on a partially-
-- migrated database (a common recovery path in this project — see the
-- Flyway V71 recovery note in project memory).
DELIMITER $$

DROP PROCEDURE IF EXISTS add_col_if_missing $$
CREATE PROCEDURE add_col_if_missing(IN tbl VARCHAR(64), IN col VARCHAR(64), IN col_def VARCHAR(255))
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

DROP PROCEDURE IF EXISTS drop_col_if_present $$
CREATE PROCEDURE drop_col_if_present(IN tbl VARCHAR(64), IN col VARCHAR(64))
BEGIN
  IF EXISTS (
    SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = tbl AND COLUMN_NAME = col
  ) THEN
    SET @s = CONCAT('ALTER TABLE `', tbl, '` DROP COLUMN `', col, '`');
    PREPARE stmt FROM @s;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END $$

DELIMITER ;

-- 1. Jewellery-specific columns
CALL add_col_if_missing('item_variant', 'metal_type',               'VARCHAR(40) DEFAULT NULL');
CALL add_col_if_missing('item_variant', 'metal_purity',             'VARCHAR(20) DEFAULT NULL');
CALL add_col_if_missing('item_variant', 'weight_grams',             'DECIMAL(10,3) DEFAULT NULL');
CALL add_col_if_missing('item_variant', 'net_weight_grams',         'DECIMAL(10,3) DEFAULT NULL');
CALL add_col_if_missing('item_variant', 'stone_weight_carats',      'DECIMAL(10,3) DEFAULT NULL');
CALL add_col_if_missing('item_variant', 'hallmark_no',              'VARCHAR(60)  DEFAULT NULL');
CALL add_col_if_missing('item_variant', 'making_charges_per_gram',  'DECIMAL(10,2) DEFAULT NULL');
CALL add_col_if_missing('item_variant', 'making_charges_pct',       'DECIMAL(5,2) DEFAULT NULL');

-- 2. Electronics-specific columns
CALL add_col_if_missing('item_variant', 'warranty_months',          'INT DEFAULT NULL');
CALL add_col_if_missing('item_variant', 'serial_number',            'VARCHAR(80)  DEFAULT NULL');

-- 3. Automobile-specific columns
CALL add_col_if_missing('item_variant', 'part_number',              'VARCHAR(80)  DEFAULT NULL');
CALL add_col_if_missing('item_variant', 'vehicle_compatibility',    'VARCHAR(500) DEFAULT NULL');

-- 4. Backfill attribute_1 / attribute_2 from the legacy fabric / season
--    columns before dropping them. `COALESCE(attribute_1, fabric)` keeps
--    any value the newer code already wrote — it never overwrites.
--    Guarded on fabric/season still existing so a re-run after the drop
--    is a no-op.
SET @has_fabric = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = 'item' AND COLUMN_NAME = 'fabric'
);
SET @sql = IF(@has_fabric > 0,
  'UPDATE item SET attribute_1 = COALESCE(attribute_1, fabric) WHERE attribute_1 IS NULL AND fabric IS NOT NULL',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_season = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = 'item' AND COLUMN_NAME = 'season'
);
SET @sql = IF(@has_season > 0,
  'UPDATE item SET attribute_2 = COALESCE(attribute_2, season) WHERE attribute_2 IS NULL AND season IS NOT NULL',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 5. Drop the legacy columns now that every reader in application code
--    has been switched to attribute_1 / attribute_2.
CALL drop_col_if_present('item', 'fabric');
CALL drop_col_if_present('item', 'season');

-- 6. Cleanup helpers so they don't linger in the schema.
DROP PROCEDURE IF EXISTS add_col_if_missing;
DROP PROCEDURE IF EXISTS drop_col_if_present;
