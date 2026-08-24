-- Statutory configuration per shop
CREATE TABLE IF NOT EXISTS statutory_config (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  shop_id BIGINT NOT NULL,
  pf_uan VARCHAR(50),
  esic_code VARCHAR(50),
  pt_state VARCHAR(2),
  tax_regime ENUM('NEW_REGIME', 'OLD_REGIME') DEFAULT 'NEW_REGIME',
  bank_name VARCHAR(100),
  bank_account_number VARCHAR(20),
  bank_ifsc VARCHAR(11),
  bank_branch VARCHAR(100),
  razorpayx_api_key VARCHAR(255),
  razorpayx_api_secret VARCHAR(255),
  razorpayx_account_id VARCHAR(100),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_shop_id (shop_id),
  KEY idx_shop_id (shop_id),
  FOREIGN KEY (shop_id) REFERENCES shop(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- PF contribution slabs (configurable per shop)
CREATE TABLE IF NOT EXISTS pf_slabs (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  shop_id BIGINT NOT NULL,
  effective_from DATE NOT NULL,
  wage_limit DECIMAL(12,2) NOT NULL COMMENT 'Wage ceiling for PF',
  employee_contribution_rate DECIMAL(5,2) NOT NULL COMMENT 'Employee PF rate percentage',
  employer_contribution_rate DECIMAL(5,2) NOT NULL COMMENT 'Employer PF rate percentage',
  epf_contribution_rate DECIMAL(5,2) NOT NULL COMMENT 'EPF rate within employer contribution',
  eps_contribution_rate DECIMAL(5,2) NOT NULL COMMENT 'EPS rate within employer contribution',
  is_active BOOLEAN DEFAULT TRUE,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  KEY idx_shop_date (shop_id, effective_from),
  FOREIGN KEY (shop_id) REFERENCES shop(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ESI slabs (configurable per shop)
CREATE TABLE IF NOT EXISTS esi_slabs (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  shop_id BIGINT NOT NULL,
  effective_from DATE NOT NULL,
  wage_ceiling DECIMAL(12,2) NOT NULL COMMENT 'ESI wage ceiling',
  employee_rate DECIMAL(5,2) NOT NULL COMMENT 'Employee ESI rate percentage',
  employer_rate DECIMAL(5,2) NOT NULL COMMENT 'Employer ESI rate percentage',
  is_active BOOLEAN DEFAULT TRUE,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  KEY idx_shop_date (shop_id, effective_from),
  FOREIGN KEY (shop_id) REFERENCES shop(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Professional Tax slabs (state-wise)
CREATE TABLE IF NOT EXISTS pt_slabs (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  shop_id BIGINT NOT NULL,
  pt_state VARCHAR(2) NOT NULL,
  effective_from DATE NOT NULL,
  salary_from DECIMAL(12,2) NOT NULL,
  salary_to DECIMAL(12,2) NOT NULL,
  pt_amount DECIMAL(12,2) NOT NULL,
  is_active BOOLEAN DEFAULT TRUE,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  KEY idx_shop_state_date (shop_id, pt_state, effective_from),
  FOREIGN KEY (shop_id) REFERENCES shop(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- TDS slabs (annual income based)
CREATE TABLE IF NOT EXISTS tds_slabs (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  shop_id BIGINT NOT NULL,
  tax_regime ENUM('NEW_REGIME', 'OLD_REGIME') NOT NULL,
  effective_from DATE NOT NULL,
  income_from DECIMAL(15,2) NOT NULL,
  income_to DECIMAL(15,2) NOT NULL,
  tax_rate DECIMAL(5,2) NOT NULL COMMENT 'Tax rate percentage',
  is_active BOOLEAN DEFAULT TRUE,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  KEY idx_shop_regime_date (shop_id, tax_regime, effective_from),
  FOREIGN KEY (shop_id) REFERENCES shop(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Banking transaction logs (for reconciliation)
CREATE TABLE IF NOT EXISTS bank_transactions (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  shop_id BIGINT NOT NULL,
  payroll_run_id BIGINT,
  employee_id BIGINT,
  transaction_type VARCHAR(50) NOT NULL COMMENT 'SALARY, ADVANCE, LOAN_RECOVERY',
  amount DECIMAL(15,2) NOT NULL,
  payment_method VARCHAR(50) NOT NULL COMMENT 'RAZORPAYX, NEFT, NACH, CASH',
  reference_number VARCHAR(100),
  utr_number VARCHAR(50) COMMENT 'Unique Transaction Reference (for bank)',
  status VARCHAR(50) DEFAULT 'PENDING' COMMENT 'PENDING, INITIATED, COMPLETED, FAILED',
  bank_response_code VARCHAR(20),
  bank_response_message TEXT,
  initiated_at TIMESTAMP,
  completed_at TIMESTAMP,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_shop_status (shop_id, status),
  KEY idx_payroll_run (payroll_run_id),
  KEY idx_employee (employee_id),
  FOREIGN KEY (shop_id) REFERENCES shop(id) ON DELETE CASCADE,
  FOREIGN KEY (payroll_run_id) REFERENCES payroll_runs(id) ON DELETE SET NULL,
  FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Compliance submissions (ECR, ESIC, returns)
CREATE TABLE IF NOT EXISTS compliance_submissions (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  shop_id BIGINT NOT NULL,
  payroll_run_id BIGINT,
  submission_type VARCHAR(50) NOT NULL COMMENT 'ECR, ESIC_RETURN, PT_RETURN, TDS_RETURN',
  filing_period VARCHAR(50) NOT NULL COMMENT 'Format: YYYY-MM',
  file_path VARCHAR(255),
  submission_status VARCHAR(50) DEFAULT 'PENDING' COMMENT 'PENDING, SUBMITTED, ACKNOWLEDGED, REJECTED',
  submission_reference VARCHAR(100),
  submitted_by_user_id BIGINT,
  submitted_at TIMESTAMP,
  acknowledgment_received_at TIMESTAMP,
  notes TEXT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  KEY idx_shop_period (shop_id, filing_period),
  FOREIGN KEY (shop_id) REFERENCES shop(id) ON DELETE CASCADE,
  FOREIGN KEY (payroll_run_id) REFERENCES payroll_runs(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Insert default slabs (India FY 2024-25)
INSERT INTO pf_slabs (shop_id, effective_from, wage_limit, employee_contribution_rate, employer_contribution_rate, epf_contribution_rate, eps_contribution_rate)
SELECT id, '2024-04-01', 50000.00, 12.00, 12.00, 3.67, 8.33 FROM shop LIMIT 1;

INSERT INTO esi_slabs (shop_id, effective_from, wage_ceiling, employee_rate, employer_rate)
SELECT id, '2024-04-01', 21000.00, 0.75, 3.25 FROM shop LIMIT 1;

INSERT INTO pt_slabs (shop_id, pt_state, effective_from, salary_from, salary_to, pt_amount)
SELECT id, 'MH', '2024-04-01', 0.00, 10000.00, 0.00 FROM shop LIMIT 1;

INSERT INTO tds_slabs (shop_id, tax_regime, effective_from, income_from, income_to, tax_rate)
SELECT id, 'NEW_REGIME', '2024-04-01', 0.00, 300000.00, 0.00 FROM shop LIMIT 1;
