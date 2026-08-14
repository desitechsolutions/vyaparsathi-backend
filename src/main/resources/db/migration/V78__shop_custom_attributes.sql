-- Migration V78: Per-shop custom attribute definitions + JSON storage
-- on item_variant.
--
-- Motivation: the built-in industry field registry (IndustryFieldRegistry)
-- covers 80% of what a shop wants to store on a variant, but each shop
-- eventually needs one or two idiosyncratic fields — "Warranty Vendor",
-- "Import Batch", "Return Window Days" — that don't belong in a global
-- migration.
--
-- Two additions:
--
-- 1. shop_custom_attribute_def — one row per shop-defined attribute.
--    Rendered by IndustrySlot on the frontend alongside the industry
--    fields. Ordering (display_order) is user-controlled so the shop
--    owner can arrange the form.
--
-- 2. item_variant.custom_attributes — a JSON blob storing the actual
--    values, keyed by the `key_name` from the definition table. No
--    schema migration required to add or remove a field — the frontend
--    reads the definition list, the backend blindly persists the map.
--
-- Both additions are guarded so the migration is re-runnable.

SET @dbname = DATABASE();

DELIMITER $$

DROP PROCEDURE IF EXISTS add_col_if_missing_v78 $$
CREATE PROCEDURE add_col_if_missing_v78(IN tbl VARCHAR(64), IN col VARCHAR(64), IN col_def VARCHAR(255))
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

DELIMITER ;

-- 1. JSON slot on item_variant. `custom_attributes` uses the native JSON
--    type on MySQL 5.7+ so we can index individual keys later if a
--    tenant grows a hot query pattern.
CALL add_col_if_missing_v78('item_variant', 'custom_attributes', 'JSON DEFAULT NULL');

-- 2. shop_custom_attribute_def — the definitions.
CREATE TABLE IF NOT EXISTS shop_custom_attribute_def (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    shop_id       BIGINT       NOT NULL,
    key_name      VARCHAR(64)  NOT NULL,
    label         VARCHAR(120) NOT NULL,
    field_type    VARCHAR(20)  NOT NULL,
    required      BOOLEAN      NOT NULL DEFAULT FALSE,
    options       JSON         DEFAULT NULL,
    help_text     VARCHAR(255) DEFAULT NULL,
    display_order INT          NOT NULL DEFAULT 0,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_shop_custom_attr_key UNIQUE (shop_id, key_name),
    CONSTRAINT fk_shop_custom_attr_shop FOREIGN KEY (shop_id) REFERENCES shop(id) ON DELETE CASCADE,
    INDEX idx_shop_custom_attr_shop_active_order (shop_id, active, display_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP PROCEDURE IF EXISTS add_col_if_missing_v78;
