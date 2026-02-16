-- 1. Remove the restrictive old constraint
ALTER TABLE sale DROP CONSTRAINT chk_sale_status;

-- 2. Add the updated constraint including 'RETURNED'
ALTER TABLE sale ADD CONSTRAINT chk_sale_status
CHECK (status IN ('DRAFT', 'COMPLETED', 'CANCELLED', 'RETURNED'));