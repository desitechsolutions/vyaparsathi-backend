-- V44__enhance_purchase_return_tables.sql
-- Extend purchase_return table with user approval references

ALTER TABLE `purchase_return`
  ADD COLUMN `approved_by_user_id` BIGINT DEFAULT NULL AFTER `notes`,
  ADD COLUMN `approved_at` DATETIME DEFAULT NULL AFTER `approved_by_user_id`;

ALTER TABLE `purchase_return`
  ADD CONSTRAINT `fk_pr_approved_by_user` FOREIGN KEY (`approved_by_user_id`) REFERENCES `users` (`id`);

CREATE INDEX `idx_pr_return_no` ON `purchase_return` (`shop_id`, `return_no`);
