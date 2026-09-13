-- Migration V131: Time-bound promotional pricing on pricing_plan_configs
-- Super admin / tech admin configurable per-tier promo price + label + window.
-- Resolved via PricingPlanConfig.resolveEffectivePrice(), the single source of
-- truth shared by Razorpay charge-amount resolution and admin/frontend display.

SET @dbname = DATABASE();

SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = 'pricing_plan_configs' AND COLUMN_NAME = 'promo_price_monthly') > 0,
  'SELECT 1',
  'ALTER TABLE pricing_plan_configs ADD COLUMN promo_price_monthly DECIMAL(12, 2) NULL, ADD COLUMN promo_price_yearly DECIMAL(12, 2) NULL, ADD COLUMN promo_label VARCHAR(100) NULL, ADD COLUMN promo_starts_at DATETIME NULL, ADD COLUMN promo_ends_at DATETIME NULL'
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
