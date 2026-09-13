-- Migration V130: Durable Razorpay Plan-ID cache on pricing_plan_configs
-- Replaces the old in-memory ConcurrentHashMap cache in RazorpaySubscriptionService,
-- which was wiped on every app restart and could cause duplicate Razorpay Plan
-- objects to be created for a price point that hadn't actually changed.

SET @dbname = DATABASE();

SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = 'pricing_plan_configs' AND COLUMN_NAME = 'razorpay_plan_id_monthly') > 0,
  'SELECT 1',
  'ALTER TABLE pricing_plan_configs ADD COLUMN razorpay_plan_id_monthly VARCHAR(64) NULL, ADD COLUMN razorpay_plan_id_yearly VARCHAR(64) NULL, ADD COLUMN razorpay_plan_price_monthly DECIMAL(12, 2) NULL, ADD COLUMN razorpay_plan_price_yearly DECIMAL(12, 2) NULL'
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
