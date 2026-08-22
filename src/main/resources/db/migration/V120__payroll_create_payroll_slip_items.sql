-- Phase 2: Payroll Slip Items (Component-level breakdown)

CREATE TABLE IF NOT EXISTS `payroll_slip_items` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `payroll_slip_id` BIGINT NOT NULL,
    `component_name` VARCHAR(100) NOT NULL,
    `component_code` VARCHAR(50) NOT NULL,
    `component_type` ENUM('EARNING', 'DEDUCTION', 'EMPLOYER_CONTRIBUTION') NOT NULL,
    `amount` DECIMAL(10, 2) NOT NULL,

    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    INDEX `idx_slip` (`payroll_slip_id`),
    INDEX `idx_component_code` (`component_code`),
    CONSTRAINT `fk_item_slip` FOREIGN KEY (`payroll_slip_id`) REFERENCES `payroll_slips` (`id`) ON DELETE CASCADE

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Track daily/monthly attendance for payroll calculation
CREATE TABLE IF NOT EXISTS `attendance_records` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `shop_id` BIGINT NOT NULL,
    `employee_id` BIGINT NOT NULL,
    `attendance_date` DATE NOT NULL,
    `attendance_type` ENUM('PRESENT', 'ABSENT', 'HALF_DAY', 'PAID_LEAVE', 'UNPAID_LEAVE', 'HOLIDAY', 'WEEKEND') DEFAULT 'PRESENT',

    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY `uk_emp_date` (`employee_id`, `attendance_date`),
    INDEX `idx_employee_month` (`employee_id`, `attendance_date`),
    INDEX `idx_shop_date` (`shop_id`, `attendance_date`),
    CONSTRAINT `fk_att_shop` FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_att_emp` FOREIGN KEY (`employee_id`) REFERENCES `employees` (`id`) ON DELETE CASCADE

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
