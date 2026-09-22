-- Migration V142: Fix item_variant SKU unique constraint to be tenant-scoped (shop_id, sku)
-- Allows different shops to use the same SKU while enforcing uniqueness within each shop.

DROP PROCEDURE IF EXISTS v142_fix_item_variant_sku_unique;
DELIMITER $$
CREATE PROCEDURE v142_fix_item_variant_sku_unique()
BEGIN
    -- 1. Drop the legacy global unique index on sku if present
    IF EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'item_variant' AND INDEX_NAME = 'sku'
    ) THEN
        ALTER TABLE `item_variant` DROP INDEX `sku`;
    END IF;

    -- 2. Create tenant-scoped unique index on (shop_id, sku) if not already existing
    IF NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'item_variant' AND INDEX_NAME = 'uk_item_variant_shop_sku'
    ) THEN
        ALTER TABLE `item_variant` ADD UNIQUE KEY `uk_item_variant_shop_sku` (`shop_id`, `sku`);
    END IF;
END$$
DELIMITER ;

CALL v142_fix_item_variant_sku_unique();
DROP PROCEDURE IF EXISTS v142_fix_item_variant_sku_unique;
