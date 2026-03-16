-- V34: Add loose_pack_size to sale_item.
-- Stores the pack size (dispensing units per stock unit) used at the time of a
-- loose-medicine sale (e.g. 15 tablets per strip).  NULL means a full-pack sale.
-- This column is needed so that returns can reverse exactly the same fractional
-- stock quantity that was deducted when the sale was created.

ALTER TABLE `sale_item`
    ADD COLUMN `loose_pack_size` DECIMAL(10, 3) NULL
        COMMENT 'Pack size used at sale time for loose-medicine lines (e.g. 15 = 15 tablets/strip). NULL for full-pack sales.';
