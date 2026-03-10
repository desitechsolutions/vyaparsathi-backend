-- V37: Add electronics and automobile industry fields to receiving_item.
-- serial_number    — serial / IMEI numbers for electronics (comma-sep for multi-unit lines)
-- warranty_start_date — warranty start date for electronics
-- part_reference   — OEM part reference no. for automobile parts

ALTER TABLE receiving_item
    ADD COLUMN IF NOT EXISTS serial_number       VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS warranty_start_date DATE,
    ADD COLUMN IF NOT EXISTS part_reference      VARCHAR(100);
