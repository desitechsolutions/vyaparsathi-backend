-- V124: Create Leave Management Tables
-- Includes: leave_types, leave_balances, leave_applications

CREATE TABLE IF NOT EXISTS leave_types (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    shop_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL COMMENT 'ANNUAL, SICK, CASUAL, UNPAID, etc.',
    max_days_per_year INT NOT NULL DEFAULT 20,
    carry_forward_days INT NOT NULL DEFAULT 0,
    is_prorated BOOLEAN NOT NULL DEFAULT TRUE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE CASCADE,
    UNIQUE KEY uk_shop_name (shop_id, name),
    CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci,
    ENGINE=InnoDB
) COMMENT='Master leave type definitions (Annual, Sick, Casual, etc.)';

CREATE TABLE IF NOT EXISTS leave_balances (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    shop_id BIGINT NOT NULL,
    employee_id BIGINT NOT NULL,
    leave_type_id BIGINT NOT NULL,
    year INT NOT NULL,
    opening_balance DECIMAL(10,2) DEFAULT 0,
    allocated DECIMAL(10,2) DEFAULT 0,
    used DECIMAL(10,2) DEFAULT 0,
    carried_forward DECIMAL(10,2) DEFAULT 0,
    closing_balance DECIMAL(10,2) DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE CASCADE,
    FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE CASCADE,
    FOREIGN KEY (leave_type_id) REFERENCES leave_types(id) ON DELETE CASCADE,
    UNIQUE KEY uk_employee_type_year (employee_id, leave_type_id, year),
    INDEX idx_shop_employee (shop_id, employee_id),
    CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci,
    ENGINE=InnoDB
) COMMENT='Leave balance tracking per employee per year';

CREATE TABLE IF NOT EXISTS leave_applications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    shop_id BIGINT NOT NULL,
    employee_id BIGINT NOT NULL,
    leave_type_id BIGINT NOT NULL,
    from_date DATE NOT NULL,
    to_date DATE NOT NULL,
    days DECIMAL(10,2) NOT NULL,
    reason TEXT,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING' COMMENT 'DRAFT, PENDING, APPROVED, REJECTED, CANCELLED',
    approver_id BIGINT,
    approval_comments TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE CASCADE,
    FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE CASCADE,
    FOREIGN KEY (leave_type_id) REFERENCES leave_types(id) ON DELETE CASCADE,
    FOREIGN KEY (approver_id) REFERENCES employees(id) ON DELETE SET NULL,
    INDEX idx_shop_employee_status (shop_id, employee_id, status),
    INDEX idx_dates (from_date, to_date),
    CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci,
    ENGINE=InnoDB
) COMMENT='Leave application requests with approval workflow';
