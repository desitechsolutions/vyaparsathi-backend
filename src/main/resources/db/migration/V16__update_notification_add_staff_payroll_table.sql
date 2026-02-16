-- 1. Rename the column (MySQL 8.0+ / 9.x syntax)
ALTER TABLE notification
RENAME COLUMN `read` TO is_read;

-- 2. Add Title and Priority (Positioned for logical grouping)
ALTER TABLE notification
ADD COLUMN title VARCHAR(255) AFTER type,
ADD COLUMN priority VARCHAR(50) DEFAULT 'medium' AFTER link;

-- 3. Ensure data types align with Spring Boot/Hibernate defaults
ALTER TABLE notification
MODIFY COLUMN is_read TINYINT(1) DEFAULT 0;

-- 4. Fill in missing data for old records so the UI looks clean
UPDATE notification SET title = 'Notification' WHERE title IS NULL;

-- 5. Performance Optimization
-- Adding a composite index makes the "Get All" and "Mark All Read" queries lightning fast
CREATE INDEX idx_recipient_status ON notification (recipient, is_read);

-- 6. Create Staff Table
CREATE TABLE IF NOT EXISTS `staff` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `shop_id` BIGINT NOT NULL,
    `name` VARCHAR(100) NOT NULL,
    `phone` VARCHAR(15) NOT NULL,
    `role` VARCHAR(50) NOT NULL,
    `base_salary` DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    `advance_balance` DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    `joining_date` DATE NOT NULL,
    `active` BOOLEAN NOT NULL DEFAULT TRUE,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    -- Index for tenant filtering and active status
    INDEX `idx_staff_shop_active` (`shop_id`, `active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 7. Create Payroll Record Table
CREATE TABLE IF NOT EXISTS `payroll_record` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `shop_id` BIGINT NOT NULL,
    `staff_id` BIGINT NOT NULL,
    `salary_month` VARCHAR(20) NOT NULL, -- e.g., "January"
    `salary_year` INT NOT NULL,
    `payment_date` DATE,
    `base_salary_at_time` DECIMAL(10, 2) NOT NULL,
    `bonus` DECIMAL(10, 2) DEFAULT 0.00,
    `deductions` DECIMAL(10, 2) DEFAULT 0.00,
    `advance_deduction` DECIMAL(10, 2) DEFAULT 0.00,
    `net_amount` DECIMAL(10, 2) NOT NULL,
    `status` VARCHAR(20) NOT NULL, -- Enum: PENDING, PAID, etc.
    `payment_mode` VARCHAR(20), -- CASH, UPI, BANK
    `remarks` TEXT,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    -- Foreign Key Constraint
    CONSTRAINT `fk_payroll_staff` FOREIGN KEY (`staff_id`) REFERENCES `staff` (`id`) ON DELETE CASCADE,

    -- BUSINESS RULE: Prevents duplicate salary for same staff/month/year/shop
    CONSTRAINT `uk_staff_period_shop` UNIQUE (`staff_id`, `salary_month`, `salary_year`, `shop_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 8. Optimization Indexes
-- Faster lookups for payroll history reports
CREATE INDEX `idx_payroll_lookup` ON `payroll_record` (`shop_id`, `salary_year`, `salary_month`);
-- Faster lookup for specific staff history
CREATE INDEX `idx_payroll_staff_history` ON `payroll_record` (`staff_id`, `payment_date`);