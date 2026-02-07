-- 1. Add new columns (nullable first to avoid migration failure on existing rows)
ALTER TABLE `audit_log`
ADD COLUMN `ip_address` VARCHAR(45) NULL AFTER `details`,
ADD COLUMN `user_agent` VARCHAR(512) NULL AFTER `ip_address`;

-- 2. (Optional) Backfill existing rows with default values if you want non-null later
-- UPDATE `audit_log`
-- SET
--   `ip_address` = 'UNKNOWN',
--   `user_agent` = 'UNKNOWN'
-- WHERE `ip_address` IS NULL OR `user_agent` IS NULL;

-- 3. (Optional) If later you want NOT NULL enforcement, run separate migration
-- ALTER TABLE `audit_log`
-- MODIFY COLUMN `ip_address` VARCHAR(45) NOT NULL,
-- MODIFY COLUMN `user_agent` VARCHAR(512) NOT NULL;
