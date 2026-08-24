-- =====================================================================
-- V126__payroll_enterprise_phase2_tables.sql
-- Enterprise Payroll Phase 2: GL Journal Entries, extended slip tracking, maker-checker
-- =====================================================================

-- ─── 1. GL Journal Entry Header ──────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS payroll_journal_entries (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    shop_id             BIGINT NOT NULL,
    payroll_run_id      BIGINT NOT NULL,
    transaction_date    DATE NOT NULL,
    reference_number    VARCHAR(100) NOT NULL UNIQUE,
    description         TEXT,
    total_debit         DECIMAL(16, 2) NOT NULL DEFAULT 0.00,
    total_credit        DECIMAL(16, 2) NOT NULL DEFAULT 0.00,
    is_balanced         BOOLEAN NOT NULL DEFAULT FALSE,
    posted_at           DATETIME,
    posted_by_user_id   BIGINT,
    created_at          DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_jl_payroll_run (payroll_run_id),
    INDEX idx_jl_shop_date (shop_id, transaction_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── 2. GL Journal Entry Lines ────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS payroll_journal_entry_lines (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    journal_entry_id    BIGINT NOT NULL,
    account_code        VARCHAR(20) NOT NULL,
    account_name        VARCHAR(100) NOT NULL,
    debit               DECIMAL(16, 2) NOT NULL DEFAULT 0.00,
    credit              DECIMAL(16, 2) NOT NULL DEFAULT 0.00,
    cost_center         VARCHAR(100),
    narration           VARCHAR(255),
    created_at          DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_jel_entry FOREIGN KEY (journal_entry_id) REFERENCES payroll_journal_entries (id) ON DELETE CASCADE,
    INDEX idx_jel_entry (journal_entry_id),
    INDEX idx_jel_account (account_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── 3. Procedures for Idempotent DDL Column & Index Additions ───────────────
DROP PROCEDURE IF EXISTS V126_add_col;
DROP PROCEDURE IF EXISTS V126_add_index;

DELIMITER $$

CREATE PROCEDURE V126_add_col(
    IN p_table  VARCHAR(64),
    IN p_column VARCHAR(64),
    IN p_ddl    VARCHAR(1024)
)
BEGIN
    IF EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table
    )
    AND NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = p_table
          AND COLUMN_NAME = p_column
    )
    THEN
        SET @sql = CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN ', p_ddl);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

CREATE PROCEDURE V126_add_index(
    IN p_table VARCHAR(64),
    IN p_index VARCHAR(64),
    IN p_cols  VARCHAR(256)
)
BEGIN
    IF EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table
    )
    AND NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = p_table
          AND INDEX_NAME = p_index
    )
    THEN
        SET @sql = CONCAT('CREATE UNIQUE INDEX `', p_index, '` ON `', p_table, '` (', p_cols, ')');
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

DELIMITER ;

-- ─── 4. Payroll Slip Columns ─────────────────────────────────────────────────
CALL V126_add_col('payroll_slips', 'payout_status', '`payout_status` VARCHAR(30) DEFAULT ''PENDING''');
CALL V126_add_col('payroll_slips', 'bank_utr_reference', '`bank_utr_reference` VARCHAR(100) NULL');
CALL V126_add_col('payroll_slips', 'disbursed_on', '`disbursed_on` DATE NULL');
CALL V126_add_col('payroll_slips', 'payment_mode', '`payment_mode` VARCHAR(30) DEFAULT ''BANK''');

-- ─── 5. Payroll Run Maker-Checker Columns ────────────────────────────────────
CALL V126_add_col('payroll_runs', 'prepared_by_user_id', '`prepared_by_user_id` BIGINT NULL');
CALL V126_add_col('payroll_runs', 'disbursed_by_user_id', '`disbursed_by_user_id` BIGINT NULL');
CALL V126_add_col('payroll_runs', 'voided_at', '`voided_at` DATETIME NULL');
CALL V126_add_col('payroll_runs', 'voided_by_user_id', '`voided_by_user_id` BIGINT NULL');
CALL V126_add_col('payroll_runs', 'void_reason', '`void_reason` VARCHAR(500) NULL');

-- ─── 6. Employee Extended Columns (AES PII & Statutory IDs) ───────────────────
CALL V126_add_col('employees', 'aadhaar_number_enc', '`aadhaar_number_enc` VARCHAR(512) NULL COMMENT ''AES-256-GCM encrypted Aadhaar number''');
CALL V126_add_col('employees', 'uan_number', '`uan_number` VARCHAR(12) NULL');
CALL V126_add_col('employees', 'esic_number', '`esic_number` VARCHAR(20) NULL');
CALL V126_add_col('employees', 'esic_enrolled', '`esic_enrolled` BOOLEAN DEFAULT FALSE');
CALL V126_add_col('employees', 'pf_enrolled', '`pf_enrolled` BOOLEAN DEFAULT TRUE');
CALL V126_add_col('employees', 'tax_regime', '`tax_regime` VARCHAR(20) DEFAULT ''NEW_REGIME''');
CALL V126_add_col('employees', 'employee_code', '`employee_code` VARCHAR(50) NULL');
CALL V126_add_col('employees', 'monthly_ctc', '`monthly_ctc` DECIMAL(16, 2) NULL');

-- ─── 7. Payslip Dispatch Log Tracking Columns ────────────────────────────────
CALL V126_add_col('payslip_dispatch_logs', 'dispatch_method', '`dispatch_method` VARCHAR(20) NULL');
CALL V126_add_col('payslip_dispatch_logs', 'recipient_address', '`recipient_address` VARCHAR(255) NULL');
CALL V126_add_col('payslip_dispatch_logs', 'sent_at', '`sent_at` DATE NULL');
CALL V126_add_col('payslip_dispatch_logs', 'delivery_status', '`delivery_status` VARCHAR(30) NULL');
CALL V126_add_col('payslip_dispatch_logs', 'provider_reference', '`provider_reference` VARCHAR(100) NULL');
CALL V126_add_col('payslip_dispatch_logs', 'error_message', '`error_message` TEXT NULL');

-- ─── 8. Statutory Config API Columns ─────────────────────────────────────────
CALL V126_add_col('statutory_config', 'razorpayx_api_key', '`razorpayx_api_key` VARCHAR(200) NULL');
CALL V126_add_col('statutory_config', 'razorpayx_api_secret', '`razorpayx_api_secret` VARCHAR(200) NULL');
CALL V126_add_col('statutory_config', 'razorpayx_account_id', '`razorpayx_account_id` VARCHAR(100) NULL');
CALL V126_add_col('statutory_config', 'bank_name', '`bank_name` VARCHAR(100) NULL');
CALL V126_add_col('statutory_config', 'bank_ifsc', '`bank_ifsc` VARCHAR(11) NULL');
CALL V126_add_col('statutory_config', 'esic_code', '`esic_code` VARCHAR(30) NULL');
CALL V126_add_col('statutory_config', 'pf_uan', '`pf_uan` VARCHAR(30) NULL');

-- ─── 9. Unique Index on Employee PAN Per Shop ────────────────────────────────
CALL V126_add_index('employees', 'uidx_emp_shop_pan', '`shop_id`, `pan_number`');

-- ─── 10. Clean Up Procedures ──────────────────────────────────────────────────
DROP PROCEDURE IF EXISTS V126_add_col;
DROP PROCEDURE IF EXISTS V126_add_index;
