-- Migration V144: Fix sale invoice_no unique constraint to be tenant-scoped (shop_id, invoice_no)
--
-- ROOT CAUSE:
-- In V1__baseline.sql, the `sale` table was created with `UNIQUE KEY invoice_no (invoice_no)`.
-- This enforced invoice numbers to be globally unique across ALL shops in the platform.
-- However, InvoiceNumberService generates per-shop sequences (e.g. Shop A starts at INV/26-27/00001,
-- and Shop B also starts at INV/26-27/00001). When Shop B created its first sale, the insert failed with:
--   Duplicate entry 'INV/26-27/00001' for key 'sale.invoice_no'
--
-- FIX:
-- Drop the global unique index on `invoice_no` and create a composite unique index on (`shop_id`, `invoice_no`).
-- This ensures invoice uniqueness within each shop while allowing different shops to have independent sequences.

DROP PROCEDURE IF EXISTS v144_fix_sale_invoice_no_unique;
DELIMITER $$
CREATE PROCEDURE v144_fix_sale_invoice_no_unique()
BEGIN
    -- 1. Drop the legacy global unique index on invoice_no if present
    IF EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sale' AND INDEX_NAME = 'invoice_no'
    ) THEN
        ALTER TABLE `sale` DROP INDEX `invoice_no`;
    END IF;

    -- Also check for uq_sale_invoice_no or uk_sale_invoice_no in case named differently
    IF EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sale' AND INDEX_NAME = 'uq_sale_invoice_no'
    ) THEN
        ALTER TABLE `sale` DROP INDEX `uq_sale_invoice_no`;
    END IF;

    -- 2. Create tenant-scoped unique index on (shop_id, invoice_no) if not already existing
    IF NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sale' AND INDEX_NAME = 'uq_sale_shop_invoice_no'
    ) THEN
        ALTER TABLE `sale` ADD UNIQUE KEY `uq_sale_shop_invoice_no` (`shop_id`, `invoice_no`);
    END IF;
END$$
DELIMITER ;

CALL v144_fix_sale_invoice_no_unique();
DROP PROCEDURE IF EXISTS v144_fix_sale_invoice_no_unique;
