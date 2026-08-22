-- Phase 2: Payroll Runs & Slips (Monthly Batch Processing)

CREATE TABLE IF NOT EXISTS `payroll_runs` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `shop_id` BIGINT NOT NULL,
    `run_number` VARCHAR(50) NOT NULL,
    `payroll_month` VARCHAR(20) NOT NULL,
    `payroll_year` INT NOT NULL,
    `start_date` DATE NOT NULL,
    `end_date` DATE NOT NULL,
    `calendar_days` INT NOT NULL DEFAULT 30,

    -- Status Lifecycle: DRAFT -> PROCESSING -> PENDING_APPROVAL -> APPROVED -> DISBURSED -> VOID
    `status` ENUM('DRAFT', 'PROCESSING', 'PENDING_APPROVAL', 'APPROVED', 'DISBURSED', 'VOID') DEFAULT 'DRAFT',

    -- Financial Aggregates
    `total_employees` INT DEFAULT 0,
    `total_gross_earnings` DECIMAL(14, 2) DEFAULT 0.00,
    `total_employee_deductions` DECIMAL(14, 2) DEFAULT 0.00,
    `total_net_payable` DECIMAL(14, 2) DEFAULT 0.00,
    `total_employer_contributions` DECIMAL(14, 2) DEFAULT 0.00,
    `total_company_cost` DECIMAL(14, 2) DEFAULT 0.00,

    -- Approvals & Execution
    `prepared_by_user_id` BIGINT,
    `approved_by_user_id` BIGINT,
    `approved_at` TIMESTAMP NULL,
    `disbursed_at` TIMESTAMP NULL,
    `shop_bank_account_id` BIGINT,

    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY `uk_shop_month_year` (`shop_id`, `payroll_month`, `payroll_year`),
    INDEX `idx_status` (`status`),
    INDEX `idx_shop_period` (`shop_id`, `payroll_year`, `payroll_month`),
    CONSTRAINT `fk_run_shop` FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`) ON DELETE CASCADE

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Individual payslips per employee for a payroll run
CREATE TABLE IF NOT EXISTS `payroll_slips` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `payroll_run_id` BIGINT NOT NULL,
    `shop_id` BIGINT NOT NULL,
    `employee_id` BIGINT NOT NULL,
    `slip_number` VARCHAR(50) NOT NULL,

    -- Attendance Breakdown
    `total_days` INT NOT NULL,
    `working_days` INT NOT NULL,
    `present_days` DECIMAL(4, 1) NOT NULL,
    `paid_leaves` DECIMAL(4, 1) DEFAULT 0.0,
    `loss_of_pay_days` DECIMAL(4, 1) DEFAULT 0.0,
    `overtime_hours` DECIMAL(5, 2) DEFAULT 0.0,

    -- Financial Breakdown
    `monthly_base_salary` DECIMAL(12, 2) NOT NULL,
    `gross_earnings` DECIMAL(12, 2) NOT NULL,
    `total_deductions` DECIMAL(12, 2) NOT NULL,
    `net_salary` DECIMAL(12, 2) NOT NULL,
    `employer_contributions` DECIMAL(12, 2) NOT NULL,
    `total_ctc` DECIMAL(12, 2) NOT NULL,

    -- Statutory Split
    `epf_employee` DECIMAL(10, 2) DEFAULT 0.00,
    `epf_employer` DECIMAL(10, 2) DEFAULT 0.00,
    `esi_employee` DECIMAL(10, 2) DEFAULT 0.00,
    `esi_employer` DECIMAL(10, 2) DEFAULT 0.00,
    `professional_tax` DECIMAL(10, 2) DEFAULT 0.00,
    `tds_tax` DECIMAL(10, 2) DEFAULT 0.00,
    `loan_advance_deduction` DECIMAL(10, 2) DEFAULT 0.00,

    -- Payout Tracking
    `payout_status` ENUM('UNPAID', 'QUEUED', 'PAID', 'FAILED') DEFAULT 'UNPAID',
    `payment_mode` VARCHAR(30),
    `bank_utr_reference` VARCHAR(100),
    `disbursed_on` DATE,
    `pdf_document_url` VARCHAR(255),

    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY `uk_run_employee` (`payroll_run_id`, `employee_id`),
    INDEX `idx_employee` (`employee_id`),
    INDEX `idx_payout_status` (`payout_status`),
    CONSTRAINT `fk_slip_run` FOREIGN KEY (`payroll_run_id`) REFERENCES `payroll_runs` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_slip_shop` FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_slip_emp` FOREIGN KEY (`employee_id`) REFERENCES `employees` (`id`) ON DELETE CASCADE

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
