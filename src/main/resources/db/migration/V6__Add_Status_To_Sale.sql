-- 1. Add the status column with a temporary default to avoid null errors
ALTER TABLE `sale`
ADD COLUMN `status` VARCHAR(32) NOT NULL DEFAULT 'COMPLETED' AFTER `payment_status`;

-- 2. Ensure all existing records are explicitly set to COMPLETED
UPDATE `sale` SET `status` = 'COMPLETED' WHERE `status` IS NULL OR `status` = '';

-- 3. (Optional) If you want to enforce specific values at the DB level like your Java Enum
-- MySQL 9 supports CHECK constraints
ALTER TABLE `sale`
ADD CONSTRAINT `chk_sale_status` CHECK (`status` IN ('DRAFT', 'COMPLETED', 'CANCELLED'));