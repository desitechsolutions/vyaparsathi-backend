-- V35: Add doctor_registration_number and is_gst_required to the sale table.
-- doctor_registration_number stores the prescribing doctor's registration no. for
-- Schedule H1 / X drug compliance (sent by the frontend for pharmacy sales).
-- is_gst_required captures whether GST was intended at sale creation time so that
-- invoices generated AFTER a shop toggles the composition-scheme flag still render
-- the correct GST columns for historical sales.

ALTER TABLE sale
    ADD COLUMN IF NOT EXISTS doctor_registration_number VARCHAR(100),
    ADD COLUMN IF NOT EXISTS is_gst_required BOOLEAN NOT NULL DEFAULT FALSE;

-- Backfill historical sales: mark is_gst_required=TRUE for any sale that has at
-- least one sale_item with non-zero GST (cgst_amt, sgst_amt, or igst_amt).
-- This ensures pre-migration invoices continue to render GST columns correctly.
UPDATE sale s
SET    is_gst_required = TRUE
WHERE  EXISTS (
    SELECT 1
    FROM   sale_item si
    WHERE  si.sale_id = s.id
      AND  (si.cgst_amt > 0 OR si.sgst_amt > 0 OR si.igst_amt > 0)
);
