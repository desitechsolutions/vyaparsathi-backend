-- ═══════════════════════════════════════════════════════════════════
-- V68 — Add total_utgst column to purchase_invoices header table.
--
-- V48 added utgst_amount to purchase_invoice_items (line-level) but the
-- header table's total_cgst / total_sgst / total_igst columns were never
-- extended with a matching total_utgst. This gap prevented GSTR-3B ITC
-- (Section 4) from surfacing UTGST paid on inward supplies to shops
-- located in Union Territories.
--
-- Idempotent — safe to re-run.
-- ═══════════════════════════════════════════════════════════════════

SET @dbname = DATABASE();

SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = @dbname
      AND TABLE_NAME = 'purchase_invoices'
      AND COLUMN_NAME = 'total_utgst') > 0,
  'SELECT 1',
  'ALTER TABLE purchase_invoices ADD COLUMN total_utgst DECIMAL(12, 2) NOT NULL DEFAULT 0.00 AFTER total_sgst'
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
