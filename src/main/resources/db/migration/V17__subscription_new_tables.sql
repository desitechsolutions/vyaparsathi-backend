-- Flyway Migration Script
-- Created for VyaparSathi Subscription Module

-- 1. Table for Manual Payment Verifications (UTR Queue)
CREATE TABLE IF NOT EXISTS payment_verifications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT,
    shop_id BIGINT,
    utr_number VARCHAR(12) NOT NULL,
    amount DOUBLE,
    plan_requested VARCHAR(50),
    status VARCHAR(50) NOT NULL,
    submitted_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    verified_at DATETIME(6),
    verified_by VARCHAR(255),
    UNIQUE KEY uk_utr_number (utr_number),
    INDEX idx_pv_shop (shop_id),
    INDEX idx_pv_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. Table for Shop Subscriptions
CREATE TABLE IF NOT EXISTS subscriptions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    shop_id BIGINT NOT NULL,
    tier VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    start_date DATETIME(6),
    end_date DATETIME(6),
    trial_end_date DATETIME(6),
    last_utr VARCHAR(12),
    last_updated_by_user_id BIGINT,
    UNIQUE KEY uk_shop_id (shop_id),
    CONSTRAINT fk_subscription_shop FOREIGN KEY (shop_id) REFERENCES shop(id) ON DELETE CASCADE,
    INDEX idx_sub_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;