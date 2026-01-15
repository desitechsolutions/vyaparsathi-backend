-- V1__baseline.sql
-- Cleaned schema-only baseline for Flyway (no INSERTs or data)

SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT;
SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS;
SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION;
SET NAMES utf8mb4;
SET @OLD_TIME_ZONE=@@TIME_ZONE;
SET TIME_ZONE='+00:00';
SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0;
SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0;
SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO';
SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0;

-- Table: refresh_token
DROP TABLE IF EXISTS `refresh_token`;
CREATE TABLE `refresh_token` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `token` varchar(255) NOT NULL,
  `username` varchar(255) NOT NULL,
  `expiry_date` datetime NOT NULL,
  `created_at` datetime NOT NULL,
  `revoked` tinyint(1) NOT NULL DEFAULT '0',
  `shop_id` bigint NOT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `token` (`token`),
  KEY `idx_refresh_token_shop_id` (`shop_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Table: delivery_status_history
DROP TABLE IF EXISTS `delivery_status_history`;
CREATE TABLE `delivery_status_history` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `delivery_id` bigint NOT NULL,
  `status` enum('PENDING','PACKED','OUT_FOR_DELIVERY','DELIVERED','CANCELLED') NOT NULL,
  `changed_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `changed_by` varchar(255) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `shop_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_delivery` (`delivery_id`),
  KEY `idx_delivery_status_history_id` (`shop_id`),
  CONSTRAINT `fk_delivery` FOREIGN KEY (`delivery_id`) REFERENCES `deliveries` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Table: receiving_ticket_attachment
DROP TABLE IF EXISTS `receiving_ticket_attachment`;
CREATE TABLE `receiving_ticket_attachment` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `receiving_ticket_id` bigint NOT NULL,
  `file_name` varchar(255) DEFAULT NULL,
  `file_type` varchar(255) DEFAULT NULL,
  `file_path` varchar(1024) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `shop_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_receiving_ticket_attachment_ticket_id` (`receiving_ticket_id`),
  KEY `idx_receiving_ticket_attachment_shop_id` (`shop_id`),
  CONSTRAINT `receiving_ticket_attachment_ibfk_1` FOREIGN KEY (`receiving_ticket_id`) REFERENCES `receiving_ticket` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Table: change_log
DROP TABLE IF EXISTS `change_log`;
CREATE TABLE `change_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `entity_type` varchar(255) NOT NULL,
  `entity_id` bigint NOT NULL,
  `operation` varchar(255) NOT NULL,
  `payload_json` text,
  `device_id` varchar(255) NOT NULL,
  `seq_no` bigint NOT NULL,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `shop_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_change_entity` (`entity_type`,`entity_id`),
  KEY `idx_change_log_shop_id` (`shop_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Table: customer_ledger
DROP TABLE IF EXISTS `customer_ledger`;
CREATE TABLE `customer_ledger` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `customer_id` bigint NOT NULL,
  `amount` decimal(10,2) NOT NULL,
  `type` varchar(255) NOT NULL,
  `description` text,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `shop_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_customer_ledger_customer_id` (`customer_id`),
  KEY `idx_customer_ledger_shop_id` (`shop_id`),
  CONSTRAINT `customer_ledger_ibfk_1` FOREIGN KEY (`customer_id`) REFERENCES `customer` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Table: sale_item
DROP TABLE IF EXISTS `sale_item`;
CREATE TABLE `sale_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `sale_id` bigint NOT NULL,
  `item_variant_id` bigint NOT NULL,
  `qty` decimal(10,2) NOT NULL,
  `unit_price` decimal(10,2) NOT NULL,
  `taxable_value` decimal(10,2) NOT NULL,
  `gst_type` varchar(255) NOT NULL,
  `cgst_amt` decimal(10,2) NOT NULL,
  `sgst_amt` decimal(10,2) NOT NULL,
  `igst_amt` decimal(10,2) NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `shop_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_sale_item_sale_id` (`sale_id`),
  KEY `idx_sale_item_variant_id` (`item_variant_id`),
  KEY `idx_sale_item_shop_id` (`shop_id`),
  CONSTRAINT `sale_item_ibfk_1` FOREIGN KEY (`sale_id`) REFERENCES `sale` (`id`),
  CONSTRAINT `sale_item_ibfk_2` FOREIGN KEY (`item_variant_id`) REFERENCES `item_variant` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Table: supplier
DROP TABLE IF EXISTS `supplier`;
CREATE TABLE `supplier` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(255) NOT NULL,
  `contact_person` varchar(255) DEFAULT NULL,
  `phone` varchar(255) DEFAULT NULL,
  `email` varchar(255) DEFAULT NULL,
  `address` varchar(255) DEFAULT NULL,
  `gstin` varchar(255) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `shop_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_supplier_phone` (`phone`),
  KEY `idx_supplier_shop_id` (`shop_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Table: users
DROP TABLE IF EXISTS `users`;
CREATE TABLE `users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `first_name` varchar(255) DEFAULT NULL,
  `last_name` varchar(255) DEFAULT NULL,
  `email` varchar(255) DEFAULT NULL,
  `username` varchar(255) NOT NULL,
  `pin_hash` varchar(255) NOT NULL,
  `role` varchar(255) DEFAULT NULL,
  `active` tinyint(1) NOT NULL DEFAULT '1',
  `shop_id` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `username` (`username`),
  UNIQUE KEY `UK_users_email` (`email`),
  KEY `idx_users_shop_id` (`shop_id`),
  CONSTRAINT `FK_users_shop` FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Table: item_variant
DROP TABLE IF EXISTS `item_variant`;
CREATE TABLE `item_variant` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `sku` varchar(255) NOT NULL,
  `unit` varchar(255) NOT NULL,
  `price_per_unit` decimal(10,2) NOT NULL,
  `hsn` varchar(255) DEFAULT NULL,
  `gst_rate` int DEFAULT NULL,
  `photo_path` varchar(255) DEFAULT NULL,
  `item_id` bigint NOT NULL,
  `color` varchar(255) DEFAULT NULL,
  `size` varchar(255) DEFAULT NULL,
  `design` varchar(255) DEFAULT NULL,
  `low_stock_threshold` decimal(10,2) DEFAULT NULL,
  `fit` varchar(255) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `shop_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `sku` (`sku`),
  KEY `idx_item_variant_item_id` (`item_id`),
  KEY `idx_item_variant_shop_id` (`shop_id`),
  CONSTRAINT `item_variant_ibfk_1` FOREIGN KEY (`item_id`) REFERENCES `item` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Table: audit_log
DROP TABLE IF EXISTS `audit_log`;
CREATE TABLE `audit_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(255) DEFAULT NULL,
  `action` varchar(255) DEFAULT NULL,
  `entity` varchar(255) DEFAULT NULL,
  `entity_id` varchar(255) DEFAULT NULL,
  `details` varchar(2000) DEFAULT NULL,
  `timestamp` datetime DEFAULT NULL,
  `shop_id` bigint NOT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_audit_entity` (`entity`,`entity_id`),
  KEY `idx_audit_log_shop_id` (`shop_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Table: deliveries
DROP TABLE IF EXISTS `deliveries`;
CREATE TABLE `deliveries` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `sale_id` bigint NOT NULL,
  `delivery_address` text,
  `delivery_charge` decimal(12,2) DEFAULT NULL,
  `delivery_paid_by` enum('CUSTOMER','SHOP') NOT NULL,
  `delivery_status` enum('PENDING','PACKED','OUT_FOR_DELIVERY','DELIVERED','CANCELLED') NOT NULL DEFAULT 'PENDING',
  `delivery_person_id` bigint DEFAULT NULL,
  `delivery_notes` text,
  `delivered_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `invoice_number` varchar(255) NOT NULL,
  `customer_name` varchar(255) NOT NULL,
  `shop_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_delivery_person` (`delivery_person_id`),
  KEY `idx_deliveries_shop_id` (`shop_id`),
  CONSTRAINT `fk_delivery_person` FOREIGN KEY (`delivery_person_id`) REFERENCES `delivery_persons` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Table: purchase_order_item
DROP TABLE IF EXISTS `purchase_order_item`;
CREATE TABLE `purchase_order_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `purchase_order_id` bigint NOT NULL,
  `item_variant_id` bigint NOT NULL,
  `quantity` int NOT NULL,
  `unit_cost` decimal(10,2) NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `shop_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_purchase_order_item_order_id` (`purchase_order_id`),
  KEY `idx_purchase_order_item_variant_id` (`item_variant_id`),
  KEY `idx_purchase_order_item_shop_id` (`shop_id`),
  CONSTRAINT `purchase_order_item_ibfk_1` FOREIGN KEY (`purchase_order_id`) REFERENCES `purchase_order` (`id`),
  CONSTRAINT `purchase_order_item_ibfk_2` FOREIGN KEY (`item_variant_id`) REFERENCES `item_variant` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Table: expense
DROP TABLE IF EXISTS `expense`;
CREATE TABLE `expense` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `shop_id` bigint NOT NULL,
  `type` varchar(255) NOT NULL,
  `amount` decimal(10,2) NOT NULL,
  `date` datetime NOT NULL,
  `notes` text,
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_expense_shop_id` (`shop_id`),
  KEY `idx_expense_id` (`shop_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Table: stock_movement
DROP TABLE IF EXISTS `stock_movement`;
CREATE TABLE `stock_movement` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `item_variant_id` bigint NOT NULL,
  `movement_type` enum('ADD','DEDUCT','ADJUST') NOT NULL,
  `quantity` decimal(10,2) NOT NULL,
  `cost_per_unit` decimal(10,2) DEFAULT NULL,
  `batch` varchar(255) DEFAULT NULL,
  `reason` varchar(255) DEFAULT NULL,
  `reference` varchar(255) DEFAULT NULL,
  `timestamp` datetime NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `shop_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_stock_movement_item_variant_id` (`item_variant_id`),
  KEY `idx_stock_movement_shop_id` (`shop_id`),
  CONSTRAINT `stock_movement_ibfk_1` FOREIGN KEY (`item_variant_id`) REFERENCES `item_variant` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Table: customer
DROP TABLE IF EXISTS `customer`;
CREATE TABLE `customer` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(255) NOT NULL,
  `phone` varchar(15) DEFAULT NULL,
  `email` varchar(255) DEFAULT NULL,
  `address_line1` varchar(255) DEFAULT NULL,
  `address_line2` varchar(255) DEFAULT NULL,
  `city` varchar(255) DEFAULT NULL,
  `state` varchar(255) DEFAULT NULL,
  `postal_code` varchar(255) DEFAULT NULL,
  `country` varchar(255) DEFAULT NULL,
  `gst_number` varchar(255) DEFAULT NULL,
  `pan_number` varchar(255) DEFAULT NULL,
  `notes` text,
  `credit_balance` decimal(10,2) NOT NULL,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  `shop_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `phone` (`phone`),
  KEY `idx_customer_phone` (`phone`),
  KEY `idx_customer_shop_id` (`shop_id`),
  CONSTRAINT `customer_ibfk_1` FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Table: reset_token
DROP TABLE IF EXISTS `reset_token`;
CREATE TABLE `reset_token` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `token` varchar(255) NOT NULL,
  `username` varchar(255) NOT NULL,
  `expiry` datetime NOT NULL,
  `used` tinyint(1) NOT NULL DEFAULT '0',
  `shop_id` bigint NOT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `token` (`token`),
  KEY `idx_reset_token_shop_id` (`shop_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Table: categories
DROP TABLE IF EXISTS `categories`;
CREATE TABLE `categories` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(255) NOT NULL,
  `parent_id` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `fk_category_parent` (`parent_id`),
  CONSTRAINT `fk_category_parent` FOREIGN KEY (`parent_id`) REFERENCES `categories` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Table: delivery_persons
DROP TABLE IF EXISTS `delivery_persons`;
CREATE TABLE `delivery_persons` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(255) NOT NULL,
  `phone` varchar(50) DEFAULT NULL,
  `notes` text,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `shop_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_delivery_persons_id` (`shop_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Table: sale
DROP TABLE IF EXISTS `sale`;
CREATE TABLE `sale` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `invoice_no` varchar(255) NOT NULL,
  `date` datetime NOT NULL,
  `shop_id` bigint NOT NULL,
  `customer_id` bigint DEFAULT NULL,
  `total_amount` decimal(10,2) NOT NULL,
  `payment_status` varchar(32) DEFAULT 'PENDING',
  `round_off` decimal(10,2) DEFAULT NULL,
  `synced_flag` tinyint(1) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `invoice_no` (`invoice_no`),
  KEY `customer_id` (`customer_id`),
  KEY `idx_sale_shop_customer` (`shop_id`,`customer_id`),
  KEY `idx_sale_shop_id` (`shop_id`),
  CONSTRAINT `sale_ibfk_1` FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `sale_ibfk_2` FOREIGN KEY (`customer_id`) REFERENCES `customer` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Table: shop
DROP TABLE IF EXISTS `shop`;
CREATE TABLE `shop` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(255) NOT NULL,
  `owner_name` varchar(255) DEFAULT NULL,
  `address` varchar(255) DEFAULT NULL,
  `state` varchar(255) NOT NULL,
  `gstin` varchar(255) DEFAULT NULL,
  `code` varchar(255) NOT NULL,
  `locale` varchar(255) DEFAULT NULL,
  `created_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Table: stock_entry
DROP TABLE IF EXISTS `stock_entry`;
CREATE TABLE `stock_entry` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `item_variant_id` bigint NOT NULL,
  `quantity` decimal(10,2) DEFAULT NULL,
  `cost_per_unit` decimal(10,2) NOT NULL,
  `shop_id` bigint DEFAULT NULL,
  `batch` varchar(255) DEFAULT NULL,
  `last_updated` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_stock_entry_item_variant_id` (`item_variant_id`),
  KEY `idx_stock_entry_shop_id` (`shop_id`),
  CONSTRAINT `fk_stock_entry_shop_id` FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `stock_entry_ibfk_1` FOREIGN KEY (`item_variant_id`) REFERENCES `item_variant` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Table: receiving_item
DROP TABLE IF EXISTS `receiving_item`;
CREATE TABLE `receiving_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `receiving_id` bigint NOT NULL,
  `po_item_id` bigint NOT NULL,
  `expected_qty` int NOT NULL DEFAULT '0',
  `status` varchar(255) DEFAULT NULL,
  `received_qty` int NOT NULL DEFAULT '0',
  `damaged_qty` int DEFAULT NULL,
  `damage_reason` varchar(255) DEFAULT NULL,
  `notes` text,
  `put_away_status` varchar(255) DEFAULT NULL,
  `putaway_qty` int DEFAULT NULL,
  `rejected_qty` int DEFAULT NULL,
  `reject_reason` varchar(255) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `shop_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_receiving_item_receiving_id` (`receiving_id`),
  KEY `idx_receiving_item_po_item_id` (`po_item_id`),
  KEY `idx_receiving_item_shop_id` (`shop_id`),
  CONSTRAINT `receiving_item_ibfk_1` FOREIGN KEY (`receiving_id`) REFERENCES `receiving` (`id`),
  CONSTRAINT `receiving_item_ibfk_2` FOREIGN KEY (`po_item_id`) REFERENCES `purchase_order_item` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Table: invoices
DROP TABLE IF EXISTS `invoices`;
CREATE TABLE `invoices` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `invoice_number` varchar(100) NOT NULL,
  `sale_id` bigint NOT NULL,
  `invoice_date` datetime DEFAULT NULL,
  `customer_name` varchar(255) DEFAULT NULL,
  `customer_phone` varchar(50) DEFAULT NULL,
  `total_amount` decimal(15,2) DEFAULT NULL,
  `paid_amount` decimal(15,2) DEFAULT NULL,
  `due_amount` decimal(15,2) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `invoice_number` (`invoice_number`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Table: receiving_ticket
DROP TABLE IF EXISTS `receiving_ticket`;
CREATE TABLE `receiving_ticket` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `receiving_id` bigint NOT NULL,
  `reason` varchar(255) DEFAULT NULL,
  `description` text,
  `status` varchar(255) DEFAULT NULL,
  `raised_at` datetime DEFAULT NULL,
  `raised_by` varchar(255) DEFAULT NULL,
  `last_updated_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `shop_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_receiving_ticket_receiving_id` (`receiving_id`),
  KEY `idx_receiving_ticket_shop_id` (`shop_id`),
  CONSTRAINT `receiving_ticket_ibfk_1` FOREIGN KEY (`receiving_id`) REFERENCES `receiving` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Table: purchase_order
DROP TABLE IF EXISTS `purchase_order`;
CREATE TABLE `purchase_order` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `po_number` varchar(255) NOT NULL,
  `supplier_id` bigint NOT NULL,
  `order_date` datetime NOT NULL,
  `expected_delivery_date` datetime DEFAULT NULL,
  `total_amount` decimal(10,2) NOT NULL,
  `payment_status` varchar(32) DEFAULT 'PENDING',
  `status` varchar(255) NOT NULL,
  `notes` text,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `shop_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `po_number` (`po_number`),
  UNIQUE KEY `uc_purchase_order_po_number` (`po_number`),
  KEY `idx_purchase_order_supplier_id` (`supplier_id`),
  KEY `idx_purchase_order_shop_id` (`shop_id`),
  CONSTRAINT `purchase_order_ibfk_1` FOREIGN KEY (`supplier_id`) REFERENCES `supplier` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Table: item
DROP TABLE IF EXISTS `item`;
CREATE TABLE `item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(255) NOT NULL,
  `description` text,
  `brand_name` varchar(255) DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  `fabric` varchar(255) DEFAULT NULL,
  `season` varchar(255) DEFAULT NULL,
  `category_id` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `shop_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `name` (`name`),
  KEY `fk_item_category` (`category_id`),
  KEY `idx_item_shop_id` (`shop_id`),
  CONSTRAINT `fk_item_category` FOREIGN KEY (`category_id`) REFERENCES `categories` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Table: receiving
DROP TABLE IF EXISTS `receiving`;
CREATE TABLE `receiving` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `shop_id` bigint NOT NULL,
  `purchase_order_id` bigint NOT NULL,
  `status` varchar(255) DEFAULT NULL,
  `received_at` datetime DEFAULT NULL,
  `received_by` varchar(255) DEFAULT NULL,
  `notes` text,
  `last_updated_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_receiving_purchase_order_id` (`purchase_order_id`),
  KEY `idx_receiving_shop_id` (`shop_id`),
  CONSTRAINT `fk_receiving_shop_id` FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `receiving_ibfk_1` FOREIGN KEY (`purchase_order_id`) REFERENCES `purchase_order` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Table: invoice_items
DROP TABLE IF EXISTS `invoice_items`;
CREATE TABLE `invoice_items` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `invoice_id` bigint DEFAULT NULL,
  `item_name` varchar(255) DEFAULT NULL,
  `sku` varchar(100) DEFAULT NULL,
  `quantity` int DEFAULT NULL,
  `price` decimal(15,2) DEFAULT NULL,
  `total` decimal(15,2) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_invoice` (`invoice_id`),
  CONSTRAINT `fk_invoice` FOREIGN KEY (`invoice_id`) REFERENCES `invoices` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Table: payment
DROP TABLE IF EXISTS `payment`;
CREATE TABLE `payment` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `transaction_id` varchar(255) DEFAULT NULL,
  `source_id` bigint DEFAULT NULL,
  `source_type` varchar(255) DEFAULT NULL,
  `supplier_id` bigint DEFAULT NULL,
  `customer_id` bigint DEFAULT NULL,
  `amount` decimal(10,2) DEFAULT NULL,
  `payment_date` datetime DEFAULT NULL,
  `payment_method` varchar(255) DEFAULT NULL,
  `reference` varchar(255) DEFAULT NULL,
  `notes` text,
  `status` varchar(255) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `shop_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uniq_transaction_id` (`transaction_id`),
  KEY `idx_payment_source` (`source_type`,`source_id`),
  KEY `idx_payment_supplier` (`supplier_id`),
  KEY `idx_payment_customer` (`customer_id`),
  KEY `idx_payment_shop_id` (`shop_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Table: notification
DROP TABLE IF EXISTS `notification`;
CREATE TABLE `notification` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `type` varchar(255) DEFAULT NULL,
  `message` varchar(255) DEFAULT NULL,
  `recipient` varchar(255) DEFAULT NULL,
  `read` tinyint(1) DEFAULT NULL,
  `link` varchar(255) DEFAULT NULL,
  `timestamp` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `shop_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_notification_shop_id` (`shop_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Restore original settings
SET TIME_ZONE=@OLD_TIME_ZONE;
SET SQL_MODE=@OLD_SQL_MODE;
SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS;
SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS;
SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT;
SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS;
SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION;
SET SQL_NOTES=@OLD_SQL_NOTES;