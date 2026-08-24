-- Employee Tax Declaration (80C, 80D, etc.)
CREATE TABLE IF NOT EXISTS tax_declarations (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  employee_id BIGINT NOT NULL,
  shop_id BIGINT NOT NULL,
  financial_year VARCHAR(10) NOT NULL COMMENT 'Format: 2024-25',
  tax_regime ENUM('NEW_REGIME', 'OLD_REGIME') NOT NULL,
  
  -- Deductions
  life_insurance_premium DECIMAL(12,2),
  medical_insurance_premium DECIMAL(12,2),
  education_expenses DECIMAL(12,2),
  home_loan_principal DECIMAL(12,2),
  home_loan_interest DECIMAL(12,2),
  nps_contribution DECIMAL(12,2),
  other_80c_deductions DECIMAL(12,2),
  
  -- Exemptions
  house_rent_allowance_claimed DECIMAL(12,2),
  leave_encashment_claimed DECIMAL(12,2),
  medical_reimbursement_claimed DECIMAL(12,2),
  
  total_taxable_income DECIMAL(15,2),
  submitted_at TIMESTAMP,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  
  KEY idx_employee_fy (employee_id, financial_year),
  FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE CASCADE,
  FOREIGN KEY (shop_id) REFERENCES shop(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Salary Advance Requests (Employee -> Admin)
CREATE TABLE IF NOT EXISTS advance_requests (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  employee_id BIGINT NOT NULL,
  shop_id BIGINT NOT NULL,
  amount DECIMAL(12,2) NOT NULL,
  reason VARCHAR(255),
  status VARCHAR(50) DEFAULT 'PENDING' COMMENT 'PENDING, APPROVED, REJECTED, DISBURSED',
  requested_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  approved_by_user_id BIGINT,
  approved_at TIMESTAMP,
  approval_remarks TEXT,
  disbursed_at TIMESTAMP,
  recovery_deduction_amount DECIMAL(12,2),
  recovery_start_month INT,
  recovery_months INT,
  is_active BOOLEAN DEFAULT TRUE,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  
  KEY idx_employee_status (employee_id, status),
  KEY idx_shop_status (shop_id, status),
  FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE CASCADE,
  FOREIGN KEY (shop_id) REFERENCES shop(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Payslip Dispatch Log (tracking email/WhatsApp delivery)
CREATE TABLE IF NOT EXISTS payslip_dispatch_logs (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  payroll_slip_id BIGINT NOT NULL,
  employee_id BIGINT NOT NULL,
  dispatch_method VARCHAR(50) NOT NULL COMMENT 'EMAIL, WHATSAPP, SMS',
  recipient_address VARCHAR(255) NOT NULL,
  sent_at TIMESTAMP,
  delivery_status VARCHAR(50) DEFAULT 'PENDING' COMMENT 'PENDING, SENT, DELIVERED, FAILED',
  provider_reference VARCHAR(100) COMMENT 'Email message ID, WhatsApp message ID, etc.',
  error_message TEXT,
  retry_count INT DEFAULT 0,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  
  KEY idx_slip_method (payroll_slip_id, dispatch_method),
  KEY idx_employee (employee_id),
  KEY idx_status (delivery_status),
  FOREIGN KEY (payroll_slip_id) REFERENCES payroll_slips(id) ON DELETE CASCADE,
  FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Employee Self-Service Settings (preferences)
CREATE TABLE IF NOT EXISTS ess_preferences (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  employee_id BIGINT NOT NULL,
  payslip_dispatch_email BOOLEAN DEFAULT TRUE,
  payslip_dispatch_whatsapp BOOLEAN DEFAULT FALSE,
  payslip_dispatch_sms BOOLEAN DEFAULT FALSE,
  auto_tax_calculation BOOLEAN DEFAULT TRUE,
  notification_opt_in BOOLEAN DEFAULT TRUE,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  
  UNIQUE KEY uk_employee_id (employee_id),
  FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Form16 Data (auto-generated from payroll)
CREATE TABLE IF NOT EXISTS form16_data (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  employee_id BIGINT NOT NULL,
  shop_id BIGINT NOT NULL,
  financial_year VARCHAR(10) NOT NULL,
  
  -- Part A: Employee Info
  pan_number VARCHAR(10),
  name VARCHAR(100),
  address VARCHAR(255),
  
  -- Part B: Employer Info & Deductions
  employer_pan VARCHAR(10),
  employer_name VARCHAR(100),
  total_salary DECIMAL(15,2),
  standard_deduction DECIMAL(12,2),
  
  -- Tax Details
  gross_total_income DECIMAL(15,2),
  pf_contribution DECIMAL(12,2),
  esi_contribution DECIMAL(12,2),
  professional_tax DECIMAL(12,2),
  tds_payable DECIMAL(12,2),
  tax_paid_this_year DECIMAL(15,2),
  
  form16_part_a_generated_at TIMESTAMP,
  form16_part_b_generated_at TIMESTAMP,
  pdf_path VARCHAR(255),
  is_verified BOOLEAN DEFAULT FALSE,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  
  KEY idx_employee_fy (employee_id, financial_year),
  FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE CASCADE,
  FOREIGN KEY (shop_id) REFERENCES shop(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
