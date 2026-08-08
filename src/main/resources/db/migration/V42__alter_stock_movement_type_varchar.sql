-- V42__alter_stock_movement_type_varchar.sql
-- Alter movement_type column from ENUM to VARCHAR(50) in stock_movement table to support PURCHASE_RETURN and future movement types

ALTER TABLE `stock_movement` MODIFY COLUMN `movement_type` VARCHAR(50) NOT NULL;
