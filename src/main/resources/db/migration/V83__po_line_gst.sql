-- Migration V83 (Phase 4 of the Purchase Order enterprise redesign):
-- line-level GST / discount / HSN on purchase_order_item plus header
-- totals on purchase_order. Mirrors the shape of SaleItem / Sale so both
-- transactional surfaces round-trip through the same accounting logic.
--
-- Additions to `purchase_order_item`:
--   * `discount`        — flat discount amount per line (₹)
--   * `discount_pct`    — optional pct if the shop prefers % over flat
--   * `taxable_value`   — quantity × unit_cost − discount
--   * `gst_rate`        — resolved rate for this line (falls back to variant)
--   * `cgst_amt`        — intra-state half
--   * `sgst_amt`        — intra-state half
--   * `igst_amt`        — inter-state full
--   * `hsn_code`        — copied from parent item when the caller doesn't set it
--   * `line_total`      — taxable_value + (cgst + sgst + igst)
--
-- Additions to `purchase_order`:
--   * `subtotal`        — Σ taxable_value across lines
--   * `total_discount`  — Σ line discount
--   * `total_tax`       — Σ (cgst + sgst + igst) across lines
--   * `freight_charges` — one-off shipping cost added to grand total
--   * `round_off`       — signed adjustment so grand total is a whole rupee
--
-- The existing `total_amount` column stays authoritative. Backfill below
-- keeps it in sync with the newly-derived subtotal / tax / freight math.
--
-- All ALTERs are INFORMATION_SCHEMA-guarded per the MySQL migration
-- pitfalls memo — re-running V83 is safe.

DELIMITER $$

DROP PROCEDURE IF EXISTS add_col_if_missing_v83 $$
CREATE PROCEDURE add_col_if_missing_v83(IN tbl VARCHAR(64), IN col VARCHAR(64), IN col_def VARCHAR(255))
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

-- ─── purchase_order_item line-level fields ────────────────────────────
CALL add_col_if_missing_v83('purchase_order_item', 'discount',       'DECIMAL(12,2) NOT NULL DEFAULT 0');
CALL add_col_if_missing_v83('purchase_order_item', 'discount_pct',   'DECIMAL(5,2) NULL DEFAULT NULL');
CALL add_col_if_missing_v83('purchase_order_item', 'taxable_value',  'DECIMAL(12,2) NOT NULL DEFAULT 0');
CALL add_col_if_missing_v83('purchase_order_item', 'gst_rate',       'INT NULL DEFAULT NULL');
CALL add_col_if_missing_v83('purchase_order_item', 'cgst_amt',       'DECIMAL(12,2) NOT NULL DEFAULT 0');
CALL add_col_if_missing_v83('purchase_order_item', 'sgst_amt',       'DECIMAL(12,2) NOT NULL DEFAULT 0');
CALL add_col_if_missing_v83('purchase_order_item', 'igst_amt',       'DECIMAL(12,2) NOT NULL DEFAULT 0');
CALL add_col_if_missing_v83('purchase_order_item', 'hsn_code',       'VARCHAR(20) NULL DEFAULT NULL');
CALL add_col_if_missing_v83('purchase_order_item', 'line_total',     'DECIMAL(12,2) NOT NULL DEFAULT 0');

-- ─── purchase_order header totals ────────────────────────────────────
CALL add_col_if_missing_v83('purchase_order', 'subtotal',        'DECIMAL(12,2) NOT NULL DEFAULT 0');
CALL add_col_if_missing_v83('purchase_order', 'total_discount',  'DECIMAL(12,2) NOT NULL DEFAULT 0');
CALL add_col_if_missing_v83('purchase_order', 'total_tax',       'DECIMAL(12,2) NOT NULL DEFAULT 0');
CALL add_col_if_missing_v83('purchase_order', 'freight_charges', 'DECIMAL(12,2) NOT NULL DEFAULT 0');
CALL add_col_if_missing_v83('purchase_order', 'round_off',       'DECIMAL(6,2) NOT NULL DEFAULT 0');

-- ─── Backfill for pre-V83 rows ────────────────────────────────────────
-- Existing lines: taxable_value = quantity × unit_cost, line_total same
-- (no per-line GST captured historically). gst_rate + hsn_code pulled
-- from the parent variant / item where present. Header subtotal / tax
-- reconciled from lines so pre-V83 POs render correctly on the new UI.

-- Only backfill lines whose taxable_value is still the default zero —
-- protects against re-running the migration after new data lands.
UPDATE purchase_order_item poi
SET
  taxable_value = quantity * unit_cost - COALESCE(discount, 0),
  line_total    = quantity * unit_cost - COALESCE(discount, 0)
WHERE taxable_value = 0;

-- Copy gst_rate and hsn_code from the catalog when not already set. HSN
-- lives on `item_variant` (not `item`) — the LEFT JOIN survives lines
-- whose variant has been deleted (rare, but possible on soft-deleted
-- variants under V77); those lines keep NULLs which the service treats
-- as "no GST captured".
UPDATE purchase_order_item poi
LEFT JOIN item_variant iv ON iv.id = poi.item_variant_id
SET
  poi.gst_rate = COALESCE(poi.gst_rate, iv.gst_rate),
  poi.hsn_code = COALESCE(poi.hsn_code, iv.hsn);

-- Reconcile header subtotal / total_tax / total_discount from the newly
-- populated line values. Runs only on rows where these headers are still
-- the default zero — same re-run protection as above.
UPDATE purchase_order po
JOIN (
  SELECT
    purchase_order_id,
    SUM(taxable_value)                                   AS sum_taxable,
    SUM(COALESCE(discount, 0))                           AS sum_discount,
    SUM(COALESCE(cgst_amt, 0) + COALESCE(sgst_amt, 0) + COALESCE(igst_amt, 0)) AS sum_tax
  FROM purchase_order_item
  GROUP BY purchase_order_id
) agg ON agg.purchase_order_id = po.id
SET
  po.subtotal       = agg.sum_taxable,
  po.total_discount = agg.sum_discount,
  po.total_tax      = agg.sum_tax
WHERE po.subtotal = 0;

DROP PROCEDURE IF EXISTS add_col_if_missing_v83;
