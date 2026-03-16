-- V31: Add drug_license_number to shop table for pharmacy invoice compliance
ALTER TABLE `shop`
    ADD COLUMN `drug_license_number` VARCHAR(100) NULL COMMENT 'Drug license number displayed on pharmacy invoices';
