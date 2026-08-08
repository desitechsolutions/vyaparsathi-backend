-- V40__create_purchase_return_tables.sql
-- Migration to add Purchase Return (Debit Note) tracking tables

CREATE TABLE IF NOT EXISTS `purchase_return` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `return_no` varchar(50) NOT NULL,
  `supplier_id` bigint NOT NULL,
  `purchase_order_id` bigint DEFAULT NULL,
  `receiving_id` bigint DEFAULT NULL,
  `return_date` datetime NOT NULL,
  `total_amount` decimal(12,2) NOT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'DRAFT',
  `notes` text,
  `shop_id` bigint NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_return_no_shop` (`return_no`, `shop_id`),
  KEY `idx_purchase_return_supplier` (`supplier_id`),
  KEY `idx_purchase_return_po` (`purchase_order_id`),
  KEY `idx_purchase_return_receiving` (`receiving_id`),
  KEY `idx_purchase_return_shop` (`shop_id`),
  CONSTRAINT `fk_pr_supplier` FOREIGN KEY (`supplier_id`) REFERENCES `supplier` (`id`),
  CONSTRAINT `fk_pr_po` FOREIGN KEY (`purchase_order_id`) REFERENCES `purchase_order` (`id`),
  CONSTRAINT `fk_pr_receiving` FOREIGN KEY (`receiving_id`) REFERENCES `receiving` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `purchase_return_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `purchase_return_id` bigint NOT NULL,
  `item_variant_id` bigint NOT NULL,
  `batch_number` varchar(255) DEFAULT NULL,
  `quantity` int NOT NULL,
  `unit_cost` decimal(10,2) NOT NULL,
  `total_cost` decimal(12,2) NOT NULL,
  `reason` varchar(255) DEFAULT NULL,
  `shop_id` bigint NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_pri_return_id` (`purchase_return_id`),
  KEY `idx_pri_variant_id` (`item_variant_id`),
  KEY `idx_pri_shop_id` (`shop_id`),
  CONSTRAINT `fk_pri_return` FOREIGN KEY (`purchase_return_id`) REFERENCES `purchase_return` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_pri_variant` FOREIGN KEY (`item_variant_id`) REFERENCES `item_variant` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
