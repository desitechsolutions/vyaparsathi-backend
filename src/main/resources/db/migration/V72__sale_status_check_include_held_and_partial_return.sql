-- The existing chk_sale_status constraint (V14) enforced the old value set
-- {DRAFT, COMPLETED, CANCELLED, RETURNED}. The Java SaleStatus enum has since
-- grown to include HELD (park/resume) and PARTIALLY_RETURNED (returns rewrite),
-- so any INSERT / UPDATE using those values throws "Check constraint
-- 'chk_sale_status' is violated." This migration widens the allowlist to match
-- the enum, idempotently so partial re-runs are safe.

-- Drop the existing constraint if present (schema history may vary across envs).
SET @stmt := IF(
  (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'sale'
       AND CONSTRAINT_NAME = 'chk_sale_status') > 0,
  'ALTER TABLE sale DROP CONSTRAINT chk_sale_status',
  'DO 0');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

-- Recreate with the current enum: DRAFT, HELD, COMPLETED, PARTIALLY_RETURNED,
-- RETURNED, CANCELLED. Keep the constraint present as a defence-in-depth guard
-- against buggy code writing junk statuses; the app-side @Enumerated(STRING) is
-- the primary contract.
ALTER TABLE sale ADD CONSTRAINT chk_sale_status
CHECK (status IN ('DRAFT', 'HELD', 'COMPLETED', 'PARTIALLY_RETURNED', 'RETURNED', 'CANCELLED'));
