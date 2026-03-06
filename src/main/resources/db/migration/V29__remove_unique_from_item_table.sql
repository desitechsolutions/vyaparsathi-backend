-- 1. Remove the restrictive single-name constraint
ALTER TABLE `item` DROP INDEX `name`;
ALTER TABLE `item` ADD UNIQUE KEY `uk_item_name_brand_shop` (`name`, `brand_name`, `shop_id`);