-- V41__add_gr_number_to_receiving.sql
-- Add gr_number column to receiving table for formatted GRN tracking

ALTER TABLE `receiving` ADD COLUMN `gr_number` VARCHAR(50) DEFAULT NULL AFTER `id`;
