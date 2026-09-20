-- =============================================================================
-- V138 — Data-aware migration of legacy supply_type values to SupplyType enum
-- =============================================================================
-- The SupplyType enum was redesigned from GSTR-1 table-routing labels
-- (B2B, B2C, B2CS, B2CL, EXPWP…) to GST supply-nature constants
-- (INTRASTATE, INTERSTATE, SEZ_WITH_PAYMENT…).
-- Rows written by the old app carry the old strings and cause
--   IllegalArgumentException: No enum constant SupplyType.<old_value>
-- at Hibernate query time.
--
-- MIGRATION STRATEGY
-- ──────────────────
-- 1. Statutory direct mappings (no data needed):
--      B2CL   → INTERSTATE          (by definition: inter-state unregistered >₹2.5L)
--      EXPWP  → EXPORT_WITH_PAYMENT
--      EXPWOP → EXPORT_WITHOUT_PAYMENT
--      SEZWP  → SEZ_WITH_PAYMENT
--      SEZWOP → SEZ_WITHOUT_PAYMENT
--      DEXP   → DEEMED_EXPORT
--
-- 2. Ambiguous domestic (B2B / B2C / B2CS) — derive from recorded tax:
--      Tables WITH item-level IGST columns: look at SUM(igst_amt) on child items.
--        IGST > 0  → INTERSTATE
--        IGST = 0  → INTRASTATE
--      Tables WITH place_of_supply_state_code: compare to shop.state_code.
--        POS state_code != shop state_code AND both non-null → INTERSTATE
--        Otherwise                                           → INTRASTATE
--      Tables WITHOUT IGST or state-code signals:
--        Default to INTRASTATE (safest conservative assumption; operator can
--        correct individual outliers via the UI).
--
-- 3. Already-valid enum constants are left unchanged (no-op CASE arms).
--
-- 4. Any other unrecognised legacy value → INTRASTATE (safe fallback).
--
-- TABLES COVERED (all received supply_type in V99):
--   sale, purchase_invoices, credit_notes, debit_notes,
--   purchase_order, receiving, purchase_return, deliveries
-- =============================================================================

-- ── Helper: a reusable CASE block for the "domestic-ambiguous" labels ─────────
-- Factored into inline comments only; repeated per table for MySQL compatibility.
-- MySQL does not support CTEs in UPDATE statements in all 8.0.x builds.

-- =============================================================================
-- 1. sale
--    IGST signal: SUM(sale_item.igst_amt) correlated subquery per sale row.
-- =============================================================================
UPDATE sale s
SET s.supply_type = CASE

    -- ── Statutory direct mappings ─────────────────────────────────────────
    WHEN s.supply_type = 'B2CL'   THEN 'INTERSTATE'
    WHEN s.supply_type = 'EXPWP'  THEN 'EXPORT_WITH_PAYMENT'
    WHEN s.supply_type = 'EXPWOP' THEN 'EXPORT_WITHOUT_PAYMENT'
    WHEN s.supply_type = 'SEZWP'  THEN 'SEZ_WITH_PAYMENT'
    WHEN s.supply_type = 'SEZWOP' THEN 'SEZ_WITHOUT_PAYMENT'
    WHEN s.supply_type = 'DEXP'   THEN 'DEEMED_EXPORT'

    -- ── Domestic ambiguous: B2B / B2C / B2CS — IGST-derived ──────────────
    WHEN s.supply_type IN ('B2B', 'B2C', 'B2CS') THEN
        CASE
            WHEN COALESCE(
                    (SELECT SUM(si.igst_amt)
                     FROM sale_item si
                     WHERE si.sale_id = s.id), 0) > 0
            THEN 'INTERSTATE'
            ELSE 'INTRASTATE'
        END

    -- ── Already-valid enum constants (no-op) ─────────────────────────────
    WHEN s.supply_type = 'INTRASTATE'             THEN 'INTRASTATE'
    WHEN s.supply_type = 'INTERSTATE'             THEN 'INTERSTATE'
    WHEN s.supply_type = 'SEZ_WITH_PAYMENT'       THEN 'SEZ_WITH_PAYMENT'
    WHEN s.supply_type = 'SEZ_WITHOUT_PAYMENT'    THEN 'SEZ_WITHOUT_PAYMENT'
    WHEN s.supply_type = 'EXPORT_WITH_PAYMENT'    THEN 'EXPORT_WITH_PAYMENT'
    WHEN s.supply_type = 'EXPORT_WITHOUT_PAYMENT' THEN 'EXPORT_WITHOUT_PAYMENT'
    WHEN s.supply_type = 'DEEMED_EXPORT'          THEN 'DEEMED_EXPORT'
    WHEN s.supply_type = 'COMPOSITION'            THEN 'COMPOSITION'
    WHEN s.supply_type = 'NON_GST'                THEN 'NON_GST'

    -- ── Catch-all for any other unrecognised legacy value ─────────────────
    ELSE 'INTRASTATE'
END
WHERE s.supply_type IS NOT NULL;

-- Also fix NULL rows that would cause NullPointerException if the field is
-- accessed without null-check. Only touch rows with known-bad old values;
-- rows that were legitimately NULL (no supply_type ever set) stay NULL.
-- (Nothing to do — the WHERE clause above already excludes NULLs.)

-- =============================================================================
-- 2. purchase_invoices
--    IGST signal: SUM(purchase_invoice_items.igst_amount) per invoice.
--    Note the column name is igst_amount (not igst_amt) — confirmed from entity.
-- =============================================================================
UPDATE purchase_invoices pi
SET pi.supply_type = CASE

    WHEN pi.supply_type = 'B2CL'   THEN 'INTERSTATE'
    WHEN pi.supply_type = 'EXPWP'  THEN 'EXPORT_WITH_PAYMENT'
    WHEN pi.supply_type = 'EXPWOP' THEN 'EXPORT_WITHOUT_PAYMENT'
    WHEN pi.supply_type = 'SEZWP'  THEN 'SEZ_WITH_PAYMENT'
    WHEN pi.supply_type = 'SEZWOP' THEN 'SEZ_WITHOUT_PAYMENT'
    WHEN pi.supply_type = 'DEXP'   THEN 'DEEMED_EXPORT'

    WHEN pi.supply_type IN ('B2B', 'B2C', 'B2CS') THEN
        CASE
            WHEN COALESCE(
                    (SELECT SUM(pii.igst_amount)
                     FROM purchase_invoice_items pii
                     WHERE pii.purchase_invoice_id = pi.id), 0) > 0
            THEN 'INTERSTATE'
            ELSE 'INTRASTATE'
        END

    WHEN pi.supply_type = 'INTRASTATE'             THEN 'INTRASTATE'
    WHEN pi.supply_type = 'INTERSTATE'             THEN 'INTERSTATE'
    WHEN pi.supply_type = 'SEZ_WITH_PAYMENT'       THEN 'SEZ_WITH_PAYMENT'
    WHEN pi.supply_type = 'SEZ_WITHOUT_PAYMENT'    THEN 'SEZ_WITHOUT_PAYMENT'
    WHEN pi.supply_type = 'EXPORT_WITH_PAYMENT'    THEN 'EXPORT_WITH_PAYMENT'
    WHEN pi.supply_type = 'EXPORT_WITHOUT_PAYMENT' THEN 'EXPORT_WITHOUT_PAYMENT'
    WHEN pi.supply_type = 'DEEMED_EXPORT'          THEN 'DEEMED_EXPORT'
    WHEN pi.supply_type = 'COMPOSITION'            THEN 'COMPOSITION'
    WHEN pi.supply_type = 'NON_GST'                THEN 'NON_GST'

    ELSE 'INTRASTATE'
END
WHERE pi.supply_type IS NOT NULL;

-- =============================================================================
-- 3. credit_notes
--    IGST signal: SUM(credit_note_item.igst_amt) per credit note.
-- =============================================================================
UPDATE credit_notes cn
SET cn.supply_type = CASE

    WHEN cn.supply_type = 'B2CL'   THEN 'INTERSTATE'
    WHEN cn.supply_type = 'EXPWP'  THEN 'EXPORT_WITH_PAYMENT'
    WHEN cn.supply_type = 'EXPWOP' THEN 'EXPORT_WITHOUT_PAYMENT'
    WHEN cn.supply_type = 'SEZWP'  THEN 'SEZ_WITH_PAYMENT'
    WHEN cn.supply_type = 'SEZWOP' THEN 'SEZ_WITHOUT_PAYMENT'
    WHEN cn.supply_type = 'DEXP'   THEN 'DEEMED_EXPORT'

    WHEN cn.supply_type IN ('B2B', 'B2C', 'B2CS') THEN
        CASE
            WHEN COALESCE(
                    (SELECT SUM(cni.igst_amt)
                     FROM credit_note_item cni
                     WHERE cni.credit_note_id = cn.id), 0) > 0
            THEN 'INTERSTATE'
            ELSE 'INTRASTATE'
        END

    WHEN cn.supply_type = 'INTRASTATE'             THEN 'INTRASTATE'
    WHEN cn.supply_type = 'INTERSTATE'             THEN 'INTERSTATE'
    WHEN cn.supply_type = 'SEZ_WITH_PAYMENT'       THEN 'SEZ_WITH_PAYMENT'
    WHEN cn.supply_type = 'SEZ_WITHOUT_PAYMENT'    THEN 'SEZ_WITHOUT_PAYMENT'
    WHEN cn.supply_type = 'EXPORT_WITH_PAYMENT'    THEN 'EXPORT_WITH_PAYMENT'
    WHEN cn.supply_type = 'EXPORT_WITHOUT_PAYMENT' THEN 'EXPORT_WITHOUT_PAYMENT'
    WHEN cn.supply_type = 'DEEMED_EXPORT'          THEN 'DEEMED_EXPORT'
    WHEN cn.supply_type = 'COMPOSITION'            THEN 'COMPOSITION'
    WHEN cn.supply_type = 'NON_GST'                THEN 'NON_GST'

    ELSE 'INTRASTATE'
END
WHERE cn.supply_type IS NOT NULL;

-- =============================================================================
-- 4. debit_notes
--    IGST signal: SUM(debit_note_item.igst_amt) per debit note.
-- =============================================================================
UPDATE debit_notes dn
SET dn.supply_type = CASE

    WHEN dn.supply_type = 'B2CL'   THEN 'INTERSTATE'
    WHEN dn.supply_type = 'EXPWP'  THEN 'EXPORT_WITH_PAYMENT'
    WHEN dn.supply_type = 'EXPWOP' THEN 'EXPORT_WITHOUT_PAYMENT'
    WHEN dn.supply_type = 'SEZWP'  THEN 'SEZ_WITH_PAYMENT'
    WHEN dn.supply_type = 'SEZWOP' THEN 'SEZ_WITHOUT_PAYMENT'
    WHEN dn.supply_type = 'DEXP'   THEN 'DEEMED_EXPORT'

    WHEN dn.supply_type IN ('B2B', 'B2C', 'B2CS') THEN
        CASE
            WHEN COALESCE(
                    (SELECT SUM(dni.igst_amt)
                     FROM debit_note_item dni
                     WHERE dni.debit_note_id = dn.id), 0) > 0
            THEN 'INTERSTATE'
            ELSE 'INTRASTATE'
        END

    WHEN dn.supply_type = 'INTRASTATE'             THEN 'INTRASTATE'
    WHEN dn.supply_type = 'INTERSTATE'             THEN 'INTERSTATE'
    WHEN dn.supply_type = 'SEZ_WITH_PAYMENT'       THEN 'SEZ_WITH_PAYMENT'
    WHEN dn.supply_type = 'SEZ_WITHOUT_PAYMENT'    THEN 'SEZ_WITHOUT_PAYMENT'
    WHEN dn.supply_type = 'EXPORT_WITH_PAYMENT'    THEN 'EXPORT_WITH_PAYMENT'
    WHEN dn.supply_type = 'EXPORT_WITHOUT_PAYMENT' THEN 'EXPORT_WITHOUT_PAYMENT'
    WHEN dn.supply_type = 'DEEMED_EXPORT'          THEN 'DEEMED_EXPORT'
    WHEN dn.supply_type = 'COMPOSITION'            THEN 'COMPOSITION'
    WHEN dn.supply_type = 'NON_GST'                THEN 'NON_GST'

    ELSE 'INTRASTATE'
END
WHERE dn.supply_type IS NOT NULL;

-- =============================================================================
-- 5. purchase_order
--    State-code signal: compare purchase_order.place_of_supply_state_code
--    to shop.state_code via the shop_id FK (all POs belong to a shop).
--    Fallback: SUM(purchase_order_item.igst_amt) if state codes are both NULL.
-- =============================================================================
UPDATE purchase_order po
JOIN shop sh ON sh.id = po.shop_id
SET po.supply_type = CASE

    WHEN po.supply_type = 'B2CL'   THEN 'INTERSTATE'
    WHEN po.supply_type = 'EXPWP'  THEN 'EXPORT_WITH_PAYMENT'
    WHEN po.supply_type = 'EXPWOP' THEN 'EXPORT_WITHOUT_PAYMENT'
    WHEN po.supply_type = 'SEZWP'  THEN 'SEZ_WITH_PAYMENT'
    WHEN po.supply_type = 'SEZWOP' THEN 'SEZ_WITHOUT_PAYMENT'
    WHEN po.supply_type = 'DEXP'   THEN 'DEEMED_EXPORT'

    WHEN po.supply_type IN ('B2B', 'B2C', 'B2CS') THEN
        CASE
            -- Primary: state-code comparison (most reliable when populated)
            WHEN po.place_of_supply_state_code IS NOT NULL
                 AND sh.state_code IS NOT NULL
                 AND po.place_of_supply_state_code <> sh.state_code
            THEN 'INTERSTATE'

            -- Secondary: IGST on line items (catches cases where state codes absent)
            WHEN po.place_of_supply_state_code IS NULL
                 OR sh.state_code IS NULL
            THEN
                CASE
                    WHEN COALESCE(
                            (SELECT SUM(poi.igst_amt)
                             FROM purchase_order_item poi
                             WHERE poi.purchase_order_id = po.id), 0) > 0
                    THEN 'INTERSTATE'
                    ELSE 'INTRASTATE'
                END

            ELSE 'INTRASTATE'
        END

    WHEN po.supply_type = 'INTRASTATE'             THEN 'INTRASTATE'
    WHEN po.supply_type = 'INTERSTATE'             THEN 'INTERSTATE'
    WHEN po.supply_type = 'SEZ_WITH_PAYMENT'       THEN 'SEZ_WITH_PAYMENT'
    WHEN po.supply_type = 'SEZ_WITHOUT_PAYMENT'    THEN 'SEZ_WITHOUT_PAYMENT'
    WHEN po.supply_type = 'EXPORT_WITH_PAYMENT'    THEN 'EXPORT_WITH_PAYMENT'
    WHEN po.supply_type = 'EXPORT_WITHOUT_PAYMENT' THEN 'EXPORT_WITHOUT_PAYMENT'
    WHEN po.supply_type = 'DEEMED_EXPORT'          THEN 'DEEMED_EXPORT'
    WHEN po.supply_type = 'COMPOSITION'            THEN 'COMPOSITION'
    WHEN po.supply_type = 'NON_GST'                THEN 'NON_GST'

    ELSE 'INTRASTATE'
END
WHERE po.supply_type IS NOT NULL;

-- =============================================================================
-- 6. receiving
--    State-code signal: receiving.place_of_supply_state_code vs shop.state_code.
--    No item-level IGST column exists on receiving rows — state code is the
--    only reliable signal; default INTRASTATE when both are absent.
-- =============================================================================
UPDATE receiving r
JOIN shop sh ON sh.id = r.shop_id
SET r.supply_type = CASE

    WHEN r.supply_type = 'B2CL'   THEN 'INTERSTATE'
    WHEN r.supply_type = 'EXPWP'  THEN 'EXPORT_WITH_PAYMENT'
    WHEN r.supply_type = 'EXPWOP' THEN 'EXPORT_WITHOUT_PAYMENT'
    WHEN r.supply_type = 'SEZWP'  THEN 'SEZ_WITH_PAYMENT'
    WHEN r.supply_type = 'SEZWOP' THEN 'SEZ_WITHOUT_PAYMENT'
    WHEN r.supply_type = 'DEXP'   THEN 'DEEMED_EXPORT'

    WHEN r.supply_type IN ('B2B', 'B2C', 'B2CS') THEN
        CASE
            WHEN r.place_of_supply_state_code IS NOT NULL
                 AND sh.state_code IS NOT NULL
                 AND r.place_of_supply_state_code <> sh.state_code
            THEN 'INTERSTATE'
            ELSE 'INTRASTATE'
        END

    WHEN r.supply_type = 'INTRASTATE'             THEN 'INTRASTATE'
    WHEN r.supply_type = 'INTERSTATE'             THEN 'INTERSTATE'
    WHEN r.supply_type = 'SEZ_WITH_PAYMENT'       THEN 'SEZ_WITH_PAYMENT'
    WHEN r.supply_type = 'SEZ_WITHOUT_PAYMENT'    THEN 'SEZ_WITHOUT_PAYMENT'
    WHEN r.supply_type = 'EXPORT_WITH_PAYMENT'    THEN 'EXPORT_WITH_PAYMENT'
    WHEN r.supply_type = 'EXPORT_WITHOUT_PAYMENT' THEN 'EXPORT_WITHOUT_PAYMENT'
    WHEN r.supply_type = 'DEEMED_EXPORT'          THEN 'DEEMED_EXPORT'
    WHEN r.supply_type = 'COMPOSITION'            THEN 'COMPOSITION'
    WHEN r.supply_type = 'NON_GST'                THEN 'NON_GST'

    ELSE 'INTRASTATE'
END
WHERE r.supply_type IS NOT NULL;

-- =============================================================================
-- 7. purchase_return
--    No item-level IGST column and no place-of-supply state code confirmed.
--    purchase_return is a goods-return document that mirrors the originating PO;
--    the old app was not GST-strict for returns. Default INTRASTATE is the
--    safest choice — operators can correct individual outliers via the UI.
-- =============================================================================
UPDATE purchase_return pr
SET pr.supply_type = CASE

    WHEN pr.supply_type = 'B2CL'   THEN 'INTERSTATE'
    WHEN pr.supply_type = 'EXPWP'  THEN 'EXPORT_WITH_PAYMENT'
    WHEN pr.supply_type = 'EXPWOP' THEN 'EXPORT_WITHOUT_PAYMENT'
    WHEN pr.supply_type = 'SEZWP'  THEN 'SEZ_WITH_PAYMENT'
    WHEN pr.supply_type = 'SEZWOP' THEN 'SEZ_WITHOUT_PAYMENT'
    WHEN pr.supply_type = 'DEXP'   THEN 'DEEMED_EXPORT'

    -- B2CL is already mapped above; for B2B/B2C/B2CS no reliable signal exists.
    WHEN pr.supply_type IN ('B2B', 'B2C', 'B2CS') THEN 'INTRASTATE'

    WHEN pr.supply_type = 'INTRASTATE'             THEN 'INTRASTATE'
    WHEN pr.supply_type = 'INTERSTATE'             THEN 'INTERSTATE'
    WHEN pr.supply_type = 'SEZ_WITH_PAYMENT'       THEN 'SEZ_WITH_PAYMENT'
    WHEN pr.supply_type = 'SEZ_WITHOUT_PAYMENT'    THEN 'SEZ_WITHOUT_PAYMENT'
    WHEN pr.supply_type = 'EXPORT_WITH_PAYMENT'    THEN 'EXPORT_WITH_PAYMENT'
    WHEN pr.supply_type = 'EXPORT_WITHOUT_PAYMENT' THEN 'EXPORT_WITHOUT_PAYMENT'
    WHEN pr.supply_type = 'DEEMED_EXPORT'          THEN 'DEEMED_EXPORT'
    WHEN pr.supply_type = 'COMPOSITION'            THEN 'COMPOSITION'
    WHEN pr.supply_type = 'NON_GST'                THEN 'NON_GST'

    ELSE 'INTRASTATE'
END
WHERE pr.supply_type IS NOT NULL;

-- =============================================================================
-- 8. deliveries
--    No item-level IGST column and no place-of-supply state code confirmed.
--    Deliveries are logistics records, not tax documents; the supply_type here
--    mirrors the originating sale. Default INTRASTATE as safe fallback.
-- =============================================================================
UPDATE deliveries d
SET d.supply_type = CASE

    WHEN d.supply_type = 'B2CL'   THEN 'INTERSTATE'
    WHEN d.supply_type = 'EXPWP'  THEN 'EXPORT_WITH_PAYMENT'
    WHEN d.supply_type = 'EXPWOP' THEN 'EXPORT_WITHOUT_PAYMENT'
    WHEN d.supply_type = 'SEZWP'  THEN 'SEZ_WITH_PAYMENT'
    WHEN d.supply_type = 'SEZWOP' THEN 'SEZ_WITHOUT_PAYMENT'
    WHEN d.supply_type = 'DEXP'   THEN 'DEEMED_EXPORT'

    WHEN d.supply_type IN ('B2B', 'B2C', 'B2CS') THEN 'INTRASTATE'

    WHEN d.supply_type = 'INTRASTATE'             THEN 'INTRASTATE'
    WHEN d.supply_type = 'INTERSTATE'             THEN 'INTERSTATE'
    WHEN d.supply_type = 'SEZ_WITH_PAYMENT'       THEN 'SEZ_WITH_PAYMENT'
    WHEN d.supply_type = 'SEZ_WITHOUT_PAYMENT'    THEN 'SEZ_WITHOUT_PAYMENT'
    WHEN d.supply_type = 'EXPORT_WITH_PAYMENT'    THEN 'EXPORT_WITH_PAYMENT'
    WHEN d.supply_type = 'EXPORT_WITHOUT_PAYMENT' THEN 'EXPORT_WITHOUT_PAYMENT'
    WHEN d.supply_type = 'DEEMED_EXPORT'          THEN 'DEEMED_EXPORT'
    WHEN d.supply_type = 'COMPOSITION'            THEN 'COMPOSITION'
    WHEN d.supply_type = 'NON_GST'                THEN 'NON_GST'

    ELSE 'INTRASTATE'
END
WHERE d.supply_type IS NOT NULL;
