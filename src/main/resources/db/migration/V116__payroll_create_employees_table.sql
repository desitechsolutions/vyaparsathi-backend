-- Phase 1: Create Enhanced Employee Master Table (Extends Staff with HRMS Features)

CREATE TABLE IF NOT EXISTS `employees` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `shop_id` BIGINT NOT NULL,
    `employee_code` VARCHAR(50) NOT NULL,
    `first_name` VARCHAR(100) NOT NULL,
    `last_name` VARCHAR(100),
    `email` VARCHAR(150),
    `phone` VARCHAR(20) NOT NULL,
    `gender` ENUM('MALE', 'FEMALE', 'OTHER') DEFAULT 'MALE',
    `date_of_birth` DATE,
    `joining_date` DATE NOT NULL,
    `exit_date` DATE,
    `employment_status` ENUM('ACTIVE', 'PROBATION', 'NOTICE_PERIOD', 'TERMINATED', 'RESIGNED') DEFAULT 'ACTIVE',
    `employment_type` ENUM('FULL_TIME', 'PART_TIME', 'CONTRACTOR', 'INTERN') DEFAULT 'FULL_TIME',
    `department_id` BIGINT,
    `designation` VARCHAR(100),
    `reporting_to` BIGINT,

    -- Banking & Payout Details
    `bank_account_number` VARCHAR(50),
    `bank_ifsc_code` VARCHAR(20),
    `bank_name` VARCHAR(100),
    `bank_branch` VARCHAR(100),
    `bank_beneficiary_name` VARCHAR(100),
    `upi_id` VARCHAR(100),
    `payment_preference` ENUM('BANK_TRANSFER', 'UPI', 'CASH', 'CHEQUE') DEFAULT 'BANK_TRANSFER',

    -- Statutory & Tax KYC
    `pan_number` VARCHAR(10),
    `aadhaar_number` VARCHAR(12),
    `uan_number` VARCHAR(12),
    `pf_enrolled` BOOLEAN DEFAULT FALSE,
    `esic_number` VARCHAR(17),
    `esic_enrolled` BOOLEAN DEFAULT FALSE,
    `pt_state` VARCHAR(50),
    `tax_regime` ENUM('NEW_REGIME', 'OLD_REGIME') DEFAULT 'NEW_REGIME',

    -- Compensation
    `salary_structure_id` BIGINT,
    `monthly_ctc` DECIMAL(12, 2) NOT NULL DEFAULT 0.00,

    -- Audit & Status
    `is_active` BOOLEAN DEFAULT TRUE,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    -- Constraints
    UNIQUE KEY `uk_shop_emp_code` (`shop_id`, `employee_code`),
    UNIQUE KEY `uk_shop_pan` (`shop_id`, `pan_number`),
    INDEX `idx_shop_status` (`shop_id`, `employment_status`),
    INDEX `idx_shop_active` (`shop_id`, `is_active`),
    CONSTRAINT `fk_emp_shop` FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`) ON DELETE CASCADE

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
