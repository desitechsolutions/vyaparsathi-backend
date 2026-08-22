-- Phase 1: Staff Loans & Advance Amortization Engine

CREATE TABLE IF NOT EXISTS `staff_loans` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `shop_id` BIGINT NOT NULL,
    `employee_id` BIGINT NOT NULL,
    `loan_type` ENUM('SALARY_ADVANCE', 'EMERGENCY_LOAN', 'EQUIPMENT_LOAN', 'PERSONAL_LOAN') NOT NULL DEFAULT 'SALARY_ADVANCE',
    `loan_number` VARCHAR(50) NOT NULL,
    `principal_amount` DECIMAL(12, 2) NOT NULL,
    `interest_rate_annual` DECIMAL(5, 2) DEFAULT 0.00,
    `tenure_months` INT NOT NULL DEFAULT 1,
    `monthly_emi` DECIMAL(12, 2) NOT NULL,
    `total_repaid` DECIMAL(12, 2) DEFAULT 0.00,
    `remaining_balance` DECIMAL(12, 2) NOT NULL,
    `disbursement_date` DATE NOT NULL,
    `recovery_start_month` VARCHAR(20) NOT NULL,
    `recovery_end_month` VARCHAR(20),
    `status` ENUM('ACTIVE', 'CLOSED', 'DEFAULTED', 'WRITTEN_OFF') DEFAULT 'ACTIVE',
    `remarks` TEXT,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY `uk_loan_number` (`shop_id`, `loan_number`),
    INDEX `idx_employee` (`employee_id`),
    INDEX `idx_status` (`status`),
    CONSTRAINT `fk_loan_shop` FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_loan_employee` FOREIGN KEY (`employee_id`) REFERENCES `employees` (`id`) ON DELETE CASCADE

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Track individual loan repayments
CREATE TABLE IF NOT EXISTS `staff_loan_repayments` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `loan_id` BIGINT NOT NULL,
    `installment_number` INT NOT NULL,
    `due_date` DATE NOT NULL,
    `paid_date` DATE,
    `principal_amount` DECIMAL(12, 2) NOT NULL,
    `interest_amount` DECIMAL(10, 2) NOT NULL,
    `total_emi` DECIMAL(12, 2) NOT NULL,
    `payment_status` ENUM('PENDING', 'PAID', 'OVERDUE', 'WAIVED') DEFAULT 'PENDING',
    `payroll_slip_id` BIGINT,
    `remarks` TEXT,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY `uk_loan_installment` (`loan_id`, `installment_number`),
    INDEX `idx_status` (`payment_status`),
    CONSTRAINT `fk_repay_loan` FOREIGN KEY (`loan_id`) REFERENCES `staff_loans` (`id`) ON DELETE CASCADE

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
