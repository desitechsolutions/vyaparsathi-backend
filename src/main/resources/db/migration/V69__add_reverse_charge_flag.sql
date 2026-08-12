-- ═══════════════════════════════════════════════════════════════════
-- V69 — Reverse charge flag on outward and inward supplies.
--
-- Under CGST §9(3)/§9(4), certain transactions shift the tax-collection
-- burden from supplier to recipient. Examples:
--   • Services from unregistered persons (§9(4) — narrow list)
--   • Goods Transport Agency (GTA) services (§9(3))
--   • Legal services from advocates (§9(3))
--   • Import of services
--
-- When the flag is TRUE:
--   • On a sale (outward supply): the supplier issues an invoice showing
--     the tax amount but does NOT collect it from the buyer. The line
--     "Tax payable under reverse charge" appears on the invoice.
--     GSTR-1 reports the invoice with rchrg = "Y".
--   • On a purchase (inward supply): the buyer accrues the tax as a
--     liability AND claims it as ITC (if eligible). GSTR-3B splits
--     inward supplies liable to reverse charge into a dedicated bucket.
--
-- The flag drives reporting and PDF rendering only in this migration.
-- Payment/ledger accounting for reverse-charge transactions is deferred
-- to a follow-up (reverse-charge journal entries + auto-ITC claim).
--
-- Idempotent — safe to re-run.
-- ═══════════════════════════════════════════════════════════════════

SET @dbname = DATABASE();

SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = @dbname
      AND TABLE_NAME = 'sale'
      AND COLUMN_NAME = 'reverse_charge') > 0,
  'SELECT 1',
  'ALTER TABLE sale ADD COLUMN reverse_charge BOOLEAN NOT NULL DEFAULT FALSE'
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = @dbname
      AND TABLE_NAME = 'purchase_invoices'
      AND COLUMN_NAME = 'reverse_charge') > 0,
  'SELECT 1',
  'ALTER TABLE purchase_invoices ADD COLUMN reverse_charge BOOLEAN NOT NULL DEFAULT FALSE'
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
