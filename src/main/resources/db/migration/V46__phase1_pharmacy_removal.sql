-- =============================================================================
-- V46__phase1_pharmacy_removal.sql
-- Phase 1: Remove pharmacy-specific database columns
--
-- SAFETY RULE: Never drop batch_number, expiry_date, mrp, manufacturing_date
--              as these serve legitimate generic-retail purposes.
--
-- Removed (pure pharmacy, no generic value):
--   - sale.doctor_name, patient_name, prescription_number, doctor_registration_number
--   - item.drug_schedule, requires_prescription
--   - item_variant.is_loose_medicine, pack_size
--   - sale_item.loose_pack_size
--   - shop.drug_license_number
--   - supplier.drug_license_number
--
-- Renamed (generic retail value preserved):
--   - item.composition → item.specifications
-- =============================================================================

SET @dbname = DATABASE();

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. SALE table — remove pharmacy doctor/patient/prescription fields
-- ─────────────────────────────────────────────────────────────────────────────
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = 'sale' AND COLUMN_NAME = 'doctor_name') > 0,
  'ALTER TABLE `sale` DROP COLUMN `doctor_name`, DROP COLUMN `patient_name`, DROP COLUMN `prescription_number`, DROP COLUMN `doctor_registration_number`',
  'SELECT 1'
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. ITEM table — remove drug classification fields; rename composition
-- ─────────────────────────────────────────────────────────────────────────────
-- Drop composition index if present
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = 'item' AND INDEX_NAME = 'idx_item_composition') > 0,
  'DROP INDEX `idx_item_composition` ON `item`',
  'SELECT 1'
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Remove pure-pharmacy fields
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = 'item' AND COLUMN_NAME = 'drug_schedule') > 0,
  'ALTER TABLE `item` DROP COLUMN `drug_schedule`, DROP COLUMN `requires_prescription`',
  'SELECT 1'
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Rename composition → specifications if composition still exists
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = 'item' AND COLUMN_NAME = 'composition') > 0,
  'ALTER TABLE `item` CHANGE COLUMN `composition` `specifications` VARCHAR(500) DEFAULT NULL COMMENT \'Product specifications, ingredients, or key attributes. Generic field usable for all industry types.\'',
  'SELECT 1'
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Create index on specifications if not present
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = 'item' AND INDEX_NAME = 'idx_item_specifications') > 0,
  'SELECT 1',
  'CREATE INDEX `idx_item_specifications` ON `item` (`specifications`(100))'
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. ITEM_VARIANT table — remove loose-medicine fields
-- ─────────────────────────────────────────────────────────────────────────────
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = 'item_variant' AND COLUMN_NAME = 'is_loose_medicine') > 0,
  'ALTER TABLE `item_variant` DROP COLUMN `is_loose_medicine`, DROP COLUMN `pack_size`',
  'SELECT 1'
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ─────────────────────────────────────────────────────────────────────────────
-- 4. SALE_ITEM table — remove loose-medicine pack size
-- ─────────────────────────────────────────────────────────────────────────────
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = 'sale_item' AND COLUMN_NAME = 'loose_pack_size') > 0,
  'ALTER TABLE `sale_item` DROP COLUMN `loose_pack_size`',
  'SELECT 1'
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ─────────────────────────────────────────────────────────────────────────────
-- 5. SHOP table — remove drug license number
-- ─────────────────────────────────────────────────────────────────────────────
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = 'shop' AND COLUMN_NAME = 'drug_license_number') > 0,
  'ALTER TABLE `shop` DROP COLUMN `drug_license_number`',
  'SELECT 1'
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ─────────────────────────────────────────────────────────────────────────────
-- 6. SUPPLIER table — remove drug license number
-- ─────────────────────────────────────────────────────────────────────────────
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = 'supplier' AND COLUMN_NAME = 'drug_license_number') > 0,
  'ALTER TABLE `supplier` DROP COLUMN `drug_license_number`',
  'SELECT 1'
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
