-- 1️⃣ Add columns (safe defaults for existing rows)

ALTER TABLE `receiving_item`
ADD COLUMN `is_overaged` TINYINT(1) NOT NULL DEFAULT 0 AFTER `expected_qty`,
ADD COLUMN `overage_reason` VARCHAR(255) NULL AFTER `is_overaged`,
ADD COLUMN `overage_notes` VARCHAR(500) NULL AFTER `overage_reason`;


-- 2️⃣ Ensure existing records are safe (extra safety, usually not required because of default)
UPDATE `receiving_item`
SET `is_overaged` = 0
WHERE `is_overaged` IS NULL;


-- 3️⃣ Optional: Add CHECK constraint if you want strict boolean enforcement
-- MySQL 8.0.16+ supports CHECK constraints (MySQL 9 definitely supports)

ALTER TABLE `receiving_item`
ADD CONSTRAINT `chk_receiving_item_is_overaged`
CHECK (`is_overaged` IN (0,1));
