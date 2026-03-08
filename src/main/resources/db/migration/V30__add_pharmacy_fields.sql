-- V30: Add pharmacy-specific fields to item, item_variant, receiving_item, and stock_movement tables

-- item table: drug schedule classification and prescription flag
ALTER TABLE `item`
    ADD COLUMN `drug_schedule`        VARCHAR(20)  NULL COMMENT 'Drug scheduling: SCHEDULE_X, SCHEDULE_H, SCHEDULE_H1, NON_SCHEDULED, OTC',
    ADD COLUMN `requires_prescription` TINYINT(1)  NOT NULL DEFAULT 0 COMMENT 'Whether a prescription is required to sell this item';

-- item_variant table: batch number, manufacturing date, expiry date, MRP
ALTER TABLE `item_variant`
    ADD COLUMN `batch_number`       VARCHAR(100) NULL COMMENT 'Manufacturer batch/lot number',
    ADD COLUMN `manufacturing_date` DATE         NULL COMMENT 'Date of manufacture printed on the packaging',
    ADD COLUMN `expiry_date`        DATE         NULL COMMENT 'Expiry date printed on the packaging',
    ADD COLUMN `mrp`                DECIMAL(12, 2) NULL COMMENT 'Maximum Retail Price';

-- receiving_item table: batch number, manufacturing date, expiry date captured at receipt
ALTER TABLE `receiving_item`
    ADD COLUMN `batch_number`       VARCHAR(100) NULL COMMENT 'Batch number received from supplier',
    ADD COLUMN `manufacturing_date` DATE         NULL COMMENT 'Manufacturing date from supplier packaging',
    ADD COLUMN `expiry_date`        DATE         NULL COMMENT 'Expiry date from supplier packaging';

-- stock_movement table: expiry date for batch-level stock tracking
ALTER TABLE `stock_movement`
    ADD COLUMN `expiry_date` DATE NULL COMMENT 'Expiry date associated with this stock batch movement';
