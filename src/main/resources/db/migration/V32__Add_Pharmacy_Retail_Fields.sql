-- Migration to add Pharmacy-specific functionality for retail compliance
-- Target: MySQL 9.4+

-- 1. Adding 'composition' to the Item table for salt/chemical tracking
ALTER TABLE item
ADD COLUMN composition VARCHAR(500) DEFAULT NULL AFTER name;

-- 2. Adding compliance fields to the Sale table for Schedule H1/X drugs
ALTER TABLE sale
ADD COLUMN doctor_name VARCHAR(200) DEFAULT NULL,
ADD COLUMN patient_name VARCHAR(200) DEFAULT NULL,
ADD COLUMN prescription_number VARCHAR(100) DEFAULT NULL;

-- 3. Optional: Add an index on composition to speed up 'salt-based' substitute searches
CREATE INDEX idx_item_composition ON item(composition(100));