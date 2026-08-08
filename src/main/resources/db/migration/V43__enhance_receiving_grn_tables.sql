-- V43__enhance_receiving_grn_tables.sql
-- Extend receiving (GRN) table with invoice, transport, and user approval references

ALTER TABLE `receiving`
  ADD COLUMN `supplier_invoice_no` VARCHAR(100) DEFAULT NULL AFTER `gr_number`,
  ADD COLUMN `supplier_invoice_date` DATE DEFAULT NULL AFTER `supplier_invoice_no`,
  ADD COLUMN `vehicle_no` VARCHAR(50) DEFAULT NULL AFTER `supplier_invoice_date`,
  ADD COLUMN `delivery_challan_no` VARCHAR(100) DEFAULT NULL AFTER `vehicle_no`,
  ADD COLUMN `approved_by_user_id` BIGINT DEFAULT NULL AFTER `received_by`,
  ADD COLUMN `approved_at` DATETIME DEFAULT NULL AFTER `approved_by_user_id`;

ALTER TABLE `receiving`
  ADD CONSTRAINT `fk_receiving_approved_by_user` FOREIGN KEY (`approved_by_user_id`) REFERENCES `users` (`id`);

ALTER TABLE `receiving_item`
  ADD COLUMN `unit_cost` DECIMAL(12,2) DEFAULT NULL AFTER `notes`;

CREATE INDEX `idx_receiving_gr_number` ON `receiving` (`shop_id`, `gr_number`);
