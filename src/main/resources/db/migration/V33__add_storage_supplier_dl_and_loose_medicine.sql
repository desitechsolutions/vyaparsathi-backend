-- V33: Add storage_requirement to item, drug_license_number to supplier,
--       and loose-medicine fields (is_loose_medicine, pack_size) to item_variant.

-- 1. item table: storage requirement (e.g., "Refrigerated 2–8°C")
ALTER TABLE `item`
    ADD COLUMN `storage_requirement` VARCHAR(100) NULL
        COMMENT 'Storage condition for pharmacy items (e.g., Refrigerated 2–8°C, Room Temperature)';

-- 2. supplier table: drug license number for pharmacy purchase register compliance
ALTER TABLE `supplier`
    ADD COLUMN `drug_license_number` VARCHAR(100) NULL
        COMMENT 'Drug License number of the supplier (Drugs & Cosmetics Act compliance)';

-- 3. item_variant table: loose medicine support
--    is_loose_medicine: whether this variant can be dispensed in sub-unit quantities
--    pack_size:         number of dispensing units (e.g., tablets) per stock unit (e.g., strip)
ALTER TABLE `item_variant`
    ADD COLUMN `is_loose_medicine` TINYINT(1) NOT NULL DEFAULT 0
        COMMENT 'True if this medicine can be sold loose (e.g., individual tablets from a strip)',
    ADD COLUMN `pack_size` DECIMAL(10, 3) NULL
        COMMENT 'Number of dispensing units per stock unit (e.g., 15 tablets per strip). Used when is_loose_medicine = 1';
