-- Migration V129: Optimistic-lock version column on pricing_plan_configs
-- Prevents two admins editing the same tier back-to-back from silently
-- clobbering each other's changes (lost update).

SET @dbname = DATABASE();

SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = 'pricing_plan_configs' AND COLUMN_NAME = 'version') > 0,
  'SELECT 1',
  'ALTER TABLE pricing_plan_configs ADD COLUMN version BIGINT NOT NULL DEFAULT 0'
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
