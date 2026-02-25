-- 1. Main Pricing Table
CREATE TABLE pricing_plan_configs (
    tier VARCHAR(50) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    monthly_price DECIMAL(10, 2) NOT NULL,
    yearly_price DECIMAL(10, 2) NOT NULL,
    discount_percentage INT DEFAULT 0,
    is_popular BOOLEAN DEFAULT FALSE,
    is_active BOOLEAN DEFAULT TRUE,
    PRIMARY KEY (tier)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 2. Features Table (ElementCollection)
CREATE TABLE plan_features (
    tier VARCHAR(50) NOT NULL,
    feature VARCHAR(255) NOT NULL,
    CONSTRAINT fk_plan_features_tier FOREIGN KEY (tier)
        REFERENCES pricing_plan_configs (tier) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 3. Update Subscriptions Table (Fixed Syntax)
ALTER TABLE subscriptions
    ADD COLUMN last_upgrade_bonus_days INT DEFAULT 0 COMMENT 'Extra days added during tier conversion',
    ADD COLUMN previous_tier VARCHAR(50) DEFAULT NULL COMMENT 'Stores the tier the user upgraded from',
    -- Only include the below if they don't already exist in your table
    ADD COLUMN created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

-- 4. Seed Data
INSERT INTO pricing_plan_configs (tier, display_name, monthly_price, yearly_price, discount_percentage, is_popular)
VALUES
('STARTER', 'Starter Pack', 499.00, 4999.00, 16, FALSE),
('PRO', 'Business Pro', 1299.00, 12999.00, 16, TRUE),
('ENTERPRISE', 'Enterprise', 2499.00, 24999.00, 16, FALSE);

INSERT INTO plan_features (tier, feature) VALUES
('PRO', 'Unlimited Invoices'),
('PRO', 'Advanced GST Reports'),
('PRO', 'Bulk Data Export'),
('PRO', 'Priority Support');