-- Migration V50: Phase 6 Barcode Support & Multi-Location Stock Transfer System

SET @dbname = DATABASE();

SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = 'item_variant' AND COLUMN_NAME = 'barcode') > 0,
  'SELECT 1',
  'ALTER TABLE item_variant ADD COLUMN barcode VARCHAR(100) DEFAULT NULL'
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Multi-location Stock Transfer Tables
CREATE TABLE IF NOT EXISTS stock_transfer (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    transfer_number VARCHAR(50) NOT NULL UNIQUE,
    from_shop_id BIGINT NOT NULL,
    to_shop_id BIGINT NOT NULL,
    transfer_date DATETIME NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING', -- PENDING, COMPLETED, CANCELLED
    notes VARCHAR(500) DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (from_shop_id) REFERENCES shop(id),
    FOREIGN KEY (to_shop_id) REFERENCES shop(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS stock_transfer_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    stock_transfer_id BIGINT NOT NULL,
    item_variant_id BIGINT NOT NULL,
    quantity DECIMAL(10, 2) NOT NULL,
    batch_number VARCHAR(50) DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (stock_transfer_id) REFERENCES stock_transfer(id) ON DELETE CASCADE,
    FOREIGN KEY (item_variant_id) REFERENCES item_variant(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
