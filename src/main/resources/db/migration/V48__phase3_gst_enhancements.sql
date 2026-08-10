-- Migration V48: Phase 3 GST Enhancements & Indian Tax Compliance
-- UTGST support, GST product tax classification, Input Tax Credit (ITC) tracking

SET @dbname = DATABASE();

SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = 'sale_item' AND COLUMN_NAME = 'utgst_amt') > 0,
  'SELECT 1',
  'ALTER TABLE sale_item ADD COLUMN utgst_amt DECIMAL(12, 2) NOT NULL DEFAULT 0.00 AFTER sgst_amt'
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = 'item_variant' AND COLUMN_NAME = 'gst_category') > 0,
  'SELECT 1',
  'ALTER TABLE item_variant ADD COLUMN gst_category VARCHAR(30) NOT NULL DEFAULT \'TAXABLE\' AFTER gst_rate'
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = 'purchase_invoices' AND COLUMN_NAME = 'itc_eligibility') > 0,
  'SELECT 1',
  'ALTER TABLE purchase_invoices ADD COLUMN itc_eligibility VARCHAR(30) NOT NULL DEFAULT \'INPUTS\' AFTER payment_status, ADD COLUMN is_itc_eligible BOOLEAN NOT NULL DEFAULT TRUE AFTER itc_eligibility'
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = 'purchase_invoice_items' AND COLUMN_NAME = 'utgst_amount') > 0,
  'SELECT 1',
  'ALTER TABLE purchase_invoice_items ADD COLUMN utgst_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00 AFTER sgst_amount'
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
