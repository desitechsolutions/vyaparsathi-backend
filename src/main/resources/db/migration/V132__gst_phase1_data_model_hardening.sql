-- =============================================================================
-- V132 — GST Phase 1: Data Model Hardening
-- =============================================================================
-- Adds the foundational columns required for GST compliance.
-- Every ALTER TABLE uses column-level DEFAULT values so the migration is
-- fully non-breaking: existing rows are backfilled at schema-change time and
-- the application never sees NULL for these new fields.
--
-- MySQL 8.0.16+ CHECK constraint syntax is used for domain constraints.
-- Tested against MySQL 8.0.28 and above.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. sale_item — cess, line classification, GSTN Unit Quantity Code
-- -----------------------------------------------------------------------------

ALTER TABLE sale_item
    -- Compensation Cess rate (%). Applicable to: tobacco, aerated drinks,
    -- coal, luxury/mid-size cars. Default 0.00 covers the vast majority of
    -- non-cess goods so no data migration is required.
    ADD COLUMN cess_rate   DECIMAL(5,2)  NOT NULL DEFAULT 0.00
        COMMENT 'Compensation cess rate %. 0 for non-cess items.',
    ADD COLUMN cess_amt    DECIMAL(12,2) NOT NULL DEFAULT 0.00
        COMMENT 'Compensation cess amount = taxable_value * cess_rate / 100.',

    -- Goods vs. Services distinction. Required for GSTN HSN Summary UQC column
    -- and for SAC-vs-HSN routing. Default GOODS covers existing inventory lines;
    -- service lines (custom_hsn_sac with no item_variant_id) should be
    -- backfilled by a separate data-hygiene job.
    ADD COLUMN line_type   VARCHAR(10)   NOT NULL DEFAULT 'GOODS'
        COMMENT 'GOODS or SERVICES — determines HSN vs SAC treatment.',

    -- GSTN-recognised Unit Quantity Code for HSN Summary Table 12.
    -- Maps to the GSTN UQC master list. OTH is the safe catch-all default;
    -- batch-update per HSN family is recommended post-migration.
    ADD COLUMN uqc         VARCHAR(10)   NOT NULL DEFAULT 'OTH'
        COMMENT 'GSTN Unit Quantity Code (NOS, KGS, LTR, MTR, OTH …).';

-- Domain constraint: only valid line_type values allowed.
-- Requires MySQL 8.0.16+. On older MySQL versions, enforcement is at the
-- application layer via the LineType enum; this is belt-and-suspenders.
ALTER TABLE sale_item
    ADD CONSTRAINT chk_sale_item_line_type
        CHECK (line_type IN ('GOODS', 'SERVICES'));

-- -----------------------------------------------------------------------------
-- 2. purchase_invoice_items — cess, line classification, UQC
-- -----------------------------------------------------------------------------

ALTER TABLE purchase_invoice_items
    ADD COLUMN cess_rate   DECIMAL(5,2)  NOT NULL DEFAULT 0.00
        COMMENT 'Compensation cess rate % on this purchase line.',
    ADD COLUMN cess_amt    DECIMAL(12,2) NOT NULL DEFAULT 0.00
        COMMENT 'Compensation cess amount = taxable_amount * cess_rate / 100.',
    ADD COLUMN line_type   VARCHAR(10)   NOT NULL DEFAULT 'GOODS'
        COMMENT 'GOODS or SERVICES.',
    ADD COLUMN uqc         VARCHAR(10)   NOT NULL DEFAULT 'OTH'
        COMMENT 'GSTN Unit Quantity Code.';

ALTER TABLE purchase_invoice_items
    ADD CONSTRAINT chk_purchase_item_line_type
        CHECK (line_type IN ('GOODS', 'SERVICES'));

-- -----------------------------------------------------------------------------
-- 3. credit_note_item — cess + GSTType enum
-- -----------------------------------------------------------------------------

ALTER TABLE credit_note_item
    ADD COLUMN cess_rate   DECIMAL(5,2)  NOT NULL DEFAULT 0.00
        COMMENT 'Compensation cess rate % on this credit note line.',
    ADD COLUMN cess_amt    DECIMAL(12,2) NOT NULL DEFAULT 0.00
        COMMENT 'Compensation cess amount on this credit note line.',

    -- Stores the GST slab as a typed enum string (GST_0, GST_5, GST_12 …).
    -- Previously absent, forcing GstTaxService to back-derive the rate from
    -- the amounts ratio — a lossy and nil-safe-unsafe computation. Adding a
    -- safe default of 'GST_0' so existing rows resolve without crashing.
    ADD COLUMN gst_type    VARCHAR(20)   NOT NULL DEFAULT 'GST_0'
        COMMENT 'GSTType enum string — eliminates back-derivation from amounts ratio.';

-- -----------------------------------------------------------------------------
-- 4. debit_note_item — cess
-- -----------------------------------------------------------------------------

ALTER TABLE debit_note_item
    ADD COLUMN cess_rate   DECIMAL(5,2)  NOT NULL DEFAULT 0.00
        COMMENT 'Compensation cess rate % on this debit note line.',
    ADD COLUMN cess_amt    DECIMAL(12,2) NOT NULL DEFAULT 0.00
        COMMENT 'Compensation cess amount on this debit note line.';

-- -----------------------------------------------------------------------------
-- 5. credit_notes — filing reference + note type
-- -----------------------------------------------------------------------------

ALTER TABLE credit_notes
    -- Free-text copy of the original invoice number at credit-note creation time.
    -- This is the authoritative string written to GSTR-1 CDNR/CDNUR field "inum".
    -- It MUST survive even if the source sale row is deleted or sale_id FK is
    -- set NULL by a cascade. Never update this after creation.
    ADD COLUMN reference_invoice_number VARCHAR(50)  NULL
        COMMENT 'Original invoice number snapshot — GSTR-1 CDNR inum field. Immutable after creation.',

    -- Distinguishes CDNR (B2B registered customer) from CDNUR (B2C unregistered).
    -- Drives GSTR-1 Table 9 vs Table 10 routing. Default CDNR is safe because
    -- the existing auto-generated credit notes were all against customers with
    -- GSTINs in the original implementation.
    ADD COLUMN note_type   VARCHAR(10)   NOT NULL DEFAULT 'CDNR'
        COMMENT 'CDNR = B2B credit note (GSTR-1 Table 9). CDNUR = B2C (Table 10).';

ALTER TABLE credit_notes
    ADD CONSTRAINT chk_credit_note_type
        CHECK (note_type IN ('CDNR', 'CDNUR'));

-- Backfill reference_invoice_number from the joined sale row for all existing
-- credit notes that still have a valid sale_id FK. New credit notes will have
-- this populated by the service layer at creation time.
UPDATE credit_notes cn
    INNER JOIN sale s ON s.id = cn.sale_id
SET cn.reference_invoice_number = s.invoice_no
WHERE cn.reference_invoice_number IS NULL
  AND cn.sale_id IS NOT NULL;

-- -----------------------------------------------------------------------------
-- 6. debit_notes — filing reference
-- -----------------------------------------------------------------------------

ALTER TABLE debit_notes
    -- Free-text copy of the original purchase invoice number.
    -- Written to GSTR-2 / purchase register for the debit note.
    ADD COLUMN reference_invoice_number VARCHAR(50)  NULL
        COMMENT 'Original purchase invoice number snapshot. Immutable after creation.';

-- Backfill from joined purchase invoice for existing rows.
UPDATE debit_notes dn
    INNER JOIN purchase_invoices pi ON pi.id = dn.purchase_invoice_id
SET dn.reference_invoice_number = pi.invoice_number
WHERE dn.reference_invoice_number IS NULL
  AND dn.purchase_invoice_id IS NOT NULL;

-- -----------------------------------------------------------------------------
-- 7. item_variant — UQC + line classification at the catalog level
-- -----------------------------------------------------------------------------

ALTER TABLE item_variant
    -- Default UQC for all sale_item lines created from this variant.
    -- SaleService reads this and propagates to sale_item.uqc at line creation.
    ADD COLUMN uqc         VARCHAR(10)   NOT NULL DEFAULT 'OTH'
        COMMENT 'Default GSTN UQC for items of this SKU (NOS, KGS, LTR …).',

    -- Whether this item is a physical good or a service. Defaults to GOODS
    -- since the entire existing catalog is product-based. Service-line variants
    -- (labour, consultation, freight) should be updated via a data migration job.
    ADD COLUMN line_type   VARCHAR(10)   NOT NULL DEFAULT 'GOODS'
        COMMENT 'GOODS or SERVICES — propagated to sale_item.line_type on line creation.';

ALTER TABLE item_variant
    ADD CONSTRAINT chk_item_variant_line_type
        CHECK (line_type IN ('GOODS', 'SERVICES'));

-- -----------------------------------------------------------------------------
-- 8. sale — immutable financial snapshot for cancelled invoice MT-2 fix
-- -----------------------------------------------------------------------------
-- When a sale is cancelled, SaleService currently sets totalAmount = ZERO,
-- permanently erasing the original tax figures. These columns store the
-- point-in-time snapshot at the moment the invoice was COMPLETED, so the
-- cancelled invoice can still be reported correctly in GSTR-1.
--
-- All columns are nullable because:
--   a) Pre-existing rows did not have a snapshot taken at completion time.
--      A later data-hygiene job can backfill from AuditLog.previousValue if needed.
--   b) DRAFT / PROFORMA invoices that were never completed legitimately have no snapshot.
--
-- SaleService.cancelSale() must write these columns BEFORE zeroing totalAmount.
-- SaleService.completeDraft() / createSale() must populate them at finalisation.
-- -----------------------------------------------------------------------------

ALTER TABLE sale
    ADD COLUMN original_total_amount    DECIMAL(12,2) NULL
        COMMENT 'Grand total at invoice completion — immutable, never zeroed on cancel.',
    ADD COLUMN original_taxable_value   DECIMAL(12,2) NULL
        COMMENT 'Sum of all line taxable_value at completion — for GSTR-1 val field.',
    ADD COLUMN original_cgst            DECIMAL(12,2) NULL
        COMMENT 'Sum of line cgst_amt at completion — for GSTR-1/3B reporting.',
    ADD COLUMN original_sgst            DECIMAL(12,2) NULL
        COMMENT 'Sum of line sgst_amt at completion — for GSTR-1/3B reporting.',
    ADD COLUMN original_igst            DECIMAL(12,2) NULL
        COMMENT 'Sum of line igst_amt at completion — for GSTR-1/3B reporting.',
    ADD COLUMN original_utgst           DECIMAL(12,2) NULL
        COMMENT 'Sum of line utgst_amt at completion — for UT supplies.',
    ADD COLUMN original_cess            DECIMAL(12,2) NULL
        COMMENT 'Sum of line cess_amt at completion — once cess columns are populated.';

-- Backfill original_total_amount for all non-cancelled COMPLETED sales where
-- total_amount is still intact (i.e. not zeroed). For CANCELLED rows the
-- original amount is already lost — those rows will have NULL snapshots and
-- must rely on AuditLog.previousValue for manual reconciliation.
UPDATE sale
SET  original_total_amount  = total_amount,
     -- taxable_value, CGST etc. must be aggregated from sale_item.
     -- We do this in a subquery join to avoid a heavy cursor loop.
     original_taxable_value = (
         SELECT COALESCE(SUM(si.taxable_value), 0)
         FROM sale_item si WHERE si.sale_id = sale.id
     ),
     original_cgst = (
         SELECT COALESCE(SUM(si.cgst_amt), 0)
         FROM sale_item si WHERE si.sale_id = sale.id
     ),
     original_sgst = (
         SELECT COALESCE(SUM(si.sgst_amt), 0)
         FROM sale_item si WHERE si.sale_id = sale.id
     ),
     original_igst = (
         SELECT COALESCE(SUM(si.igst_amt), 0)
         FROM sale_item si WHERE si.sale_id = sale.id
     ),
     original_utgst = (
         SELECT COALESCE(SUM(si.utgst_amt), 0)
         FROM sale_item si WHERE si.sale_id = sale.id
     ),
     original_cess = 0  -- cess columns being added in this same migration; always 0 for old data
WHERE status != 'CANCELLED'
  AND total_amount > 0;
