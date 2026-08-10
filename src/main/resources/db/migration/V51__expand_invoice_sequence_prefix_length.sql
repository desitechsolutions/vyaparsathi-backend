-- Migration V51: Expand prefix column length in invoice_sequence table to handle custom shop code prefixes

SET @dbname = DATABASE();

SET @preparedStatement = (SELECT IF(
  (SELECT CHARACTER_MAXIMUM_LENGTH FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = 'invoice_sequence' AND COLUMN_NAME = 'prefix') < 100,
  'ALTER TABLE invoice_sequence MODIFY COLUMN prefix VARCHAR(100) NOT NULL DEFAULT \'INV\'',
  'SELECT 1'
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
