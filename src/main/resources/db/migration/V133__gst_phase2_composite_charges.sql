-- V133: GST Phase 2 — Composite Supply Charge GST columns
--
-- When a sale includes shipping charges or other charges, the GST on those
-- amounts is computed at the composite supply rate (highest GST rate among
-- invoice items, per Section 8(a) CGST Act). These four columns store the
-- resulting GST split for reporting in GSTR-1 and GSTR-3B.
--
-- All columns are nullable and default to 0 so that:
--   (a) existing rows (pre-Phase 2) read as zero composite charge GST — correct
--       because those rows have no shipping/other charges billed separately.
--   (b) rows where shippingCharges = otherCharges = 0 also remain zero.

ALTER TABLE sale
    ADD COLUMN composite_charge_cgst   DECIMAL(12,2) NULL DEFAULT 0.00,
    ADD COLUMN composite_charge_sgst   DECIMAL(12,2) NULL DEFAULT 0.00,
    ADD COLUMN composite_charge_igst   DECIMAL(12,2) NULL DEFAULT 0.00,
    ADD COLUMN composite_charge_utgst  DECIMAL(12,2) NULL DEFAULT 0.00;
