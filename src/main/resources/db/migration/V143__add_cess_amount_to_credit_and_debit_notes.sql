-- =============================================================================
-- Migration V143: Add missing cess_amount column to credit_notes and debit_notes
-- =============================================================================
-- CreditNote and DebitNote entities map cessAmount to column cess_amount.
-- While credit_note_item and debit_note_item had cess columns added in V132,
-- the parent credit_notes and debit_notes tables were missing the aggregated cess_amount column.

SET @dbname = DATABASE();

-- 1. Add cess_amount to credit_notes if not present
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = 'credit_notes' AND COLUMN_NAME = 'cess_amount') > 0,
  'SELECT 1',
  'ALTER TABLE credit_notes ADD COLUMN cess_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT \'Aggregated compensation cess amount\' AFTER igst_amount'
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2. Add cess_amount to debit_notes if not present
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = 'debit_notes' AND COLUMN_NAME = 'cess_amount') > 0,
  'SELECT 1',
  'ALTER TABLE debit_notes ADD COLUMN cess_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT \'Aggregated compensation cess amount\' AFTER igst_amount'
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3. Backfill cess_amount from items if items exist
UPDATE credit_notes cn
SET cn.cess_amount = COALESCE((
    SELECT SUM(cni.cess_amt)
    FROM credit_note_item cni
    WHERE cni.credit_note_id = cn.id
), 0.00);

UPDATE debit_notes dn
SET dn.cess_amount = COALESCE((
    SELECT SUM(dni.cess_amt)
    FROM debit_note_item dni
    WHERE dni.debit_note_id = dn.id
), 0.00);
