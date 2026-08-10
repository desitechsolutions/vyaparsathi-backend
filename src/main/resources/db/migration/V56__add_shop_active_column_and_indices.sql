-- =============================================================================
-- V56: Add explicit active column to shop table and indexes (MySQL 8.x Safe)
-- =============================================================================

ALTER TABLE `shop` ADD COLUMN `active` TINYINT(1) NOT NULL DEFAULT 1 AFTER `code`;

CREATE INDEX `idx_shop_active` ON `shop`(`active`);
