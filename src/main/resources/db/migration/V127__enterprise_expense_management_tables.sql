-- V127: Enterprise Expense Management Tables and Column Extensions
-- Tables:
--   1. expense_category (hierarchical categories)
--   2. expense_approval (multi-level workflow tracking)
--   3. expense_policy   (rules & policy enforcement)
-- Extended columns on `expense` table.

-- ─── Helper procedure for idempotent column addition ─────────────────
DROP PROCEDURE IF EXISTS v127_ensure_column;
DELIMITER $$
CREATE PROCEDURE v127_ensure_column(IN tbl VARCHAR(64), IN col VARCHAR(64), IN ddl TEXT)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = tbl AND COLUMN_NAME = col
    ) THEN
        SET @s = CONCAT('ALTER TABLE `', tbl, '` ADD COLUMN `', col, '` ', ddl);
        PREPARE stmt FROM @s;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

-- ─── 1. Create expense_category table ────────────────────────────────
CREATE TABLE IF NOT EXISTS expense_category (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    shop_id                 BIGINT NOT NULL,
    name                    VARCHAR(100) NOT NULL,
    parent_id               BIGINT DEFAULT NULL,
    icon_code               VARCHAR(50) DEFAULT NULL,
    color_code              VARCHAR(7) DEFAULT NULL,
    budget_threshold        DECIMAL(12, 2) DEFAULT NULL,
    requires_receipt        BOOLEAN NOT NULL DEFAULT FALSE,
    allowed_payment_methods JSON DEFAULT NULL,
    default_tags            JSON DEFAULT NULL,
    description             VARCHAR(255) DEFAULT NULL,
    sort_order              INT DEFAULT 0,
    is_active               BOOLEAN NOT NULL DEFAULT TRUE,
    created_at              DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_exp_cat_shop_id (shop_id),
    INDEX idx_exp_cat_parent_id (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── 2. Create expense_approval table ────────────────────────────────
CREATE TABLE IF NOT EXISTS expense_approval (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    expense_id              BIGINT NOT NULL,
    approver_id             VARCHAR(255) NOT NULL,
    approval_level          INT NOT NULL,
    status                  VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    is_current_level        BOOLEAN NOT NULL DEFAULT TRUE,
    action_date             DATETIME DEFAULT NULL,
    comment                 TEXT DEFAULT NULL,
    rejection_reason        VARCHAR(500) DEFAULT NULL,
    created_at              DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_exp_appr_expense_id (expense_id),
    INDEX idx_exp_appr_approver_id (approver_id),
    INDEX idx_exp_appr_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── 3. Create expense_policy table ──────────────────────────────────
CREATE TABLE IF NOT EXISTS expense_policy (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    shop_id                 BIGINT NOT NULL,
    name                    VARCHAR(200) NOT NULL,
    description             TEXT DEFAULT NULL,
    rule_type               VARCHAR(50) DEFAULT NULL,
    affected_categories     JSON DEFAULT NULL,
    limit_value             DECIMAL(12, 2) DEFAULT NULL,
    frequency               VARCHAR(50) DEFAULT NULL,
    enforcement_action      VARCHAR(50) DEFAULT NULL,
    is_active               BOOLEAN NOT NULL DEFAULT TRUE,
    created_by              VARCHAR(100) DEFAULT NULL,
    created_at              DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_exp_pol_shop_id (shop_id),
    INDEX idx_exp_pol_is_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── 4. Extend existing expense table ─────────────────────────────────
CALL v127_ensure_column('expense', 'employee_id', 'VARCHAR(255) DEFAULT NULL');
CALL v127_ensure_column('expense', 'expense_category_id', 'BIGINT DEFAULT NULL');
CALL v127_ensure_column('expense', 'vendor_name', 'VARCHAR(255) DEFAULT NULL');
CALL v127_ensure_column('expense', 'vendor_id', 'BIGINT DEFAULT NULL');
CALL v127_ensure_column('expense', 'expense_date', 'DATE DEFAULT NULL');
CALL v127_ensure_column('expense', 'payment_method', 'VARCHAR(50) DEFAULT NULL');
CALL v127_ensure_column('expense', 'currency', "VARCHAR(3) DEFAULT 'INR'");
CALL v127_ensure_column('expense', 'description', 'TEXT DEFAULT NULL');
CALL v127_ensure_column('expense', 'cost_center', 'VARCHAR(100) DEFAULT NULL');
CALL v127_ensure_column('expense', 'tags', 'JSON DEFAULT NULL');
CALL v127_ensure_column('expense', 'receipt_id', 'BIGINT DEFAULT NULL');
CALL v127_ensure_column('expense', 'receipt_path', 'VARCHAR(500) DEFAULT NULL');
CALL v127_ensure_column('expense', 'status', "VARCHAR(30) DEFAULT 'DRAFT'");
CALL v127_ensure_column('expense', 'approval_chain_id', 'BIGINT DEFAULT NULL');
CALL v127_ensure_column('expense', 'submission_date', 'DATETIME DEFAULT NULL');
CALL v127_ensure_column('expense', 'approved_date', 'DATETIME DEFAULT NULL');
CALL v127_ensure_column('expense', 'rejection_reason', 'TEXT DEFAULT NULL');
CALL v127_ensure_column('expense', 'reimbursement_date', 'DATETIME DEFAULT NULL');
CALL v127_ensure_column('expense', 'policy_violations', 'JSON DEFAULT NULL');
CALL v127_ensure_column('expense', 'requires_escalation', 'BOOLEAN DEFAULT FALSE');
CALL v127_ensure_column('expense', 'escalation_reason', 'VARCHAR(500) DEFAULT NULL');
CALL v127_ensure_column('expense', 'is_recurring', 'BOOLEAN DEFAULT FALSE');
CALL v127_ensure_column('expense', 'recurring_expense_id', 'BIGINT DEFAULT NULL');
CALL v127_ensure_column('expense', 'is_deleted', 'BOOLEAN DEFAULT FALSE');
CALL v127_ensure_column('expense', 'created_by', 'VARCHAR(100) DEFAULT NULL');
CALL v127_ensure_column('expense', 'updated_by', 'VARCHAR(100) DEFAULT NULL');

-- Clean up helper procedure
DROP PROCEDURE IF EXISTS v127_ensure_column;

-- ─── 5. Seed Default Expense Categories (All Shops) ─────────────────
INSERT IGNORE INTO expense_category (shop_id, name, icon_code, color_code, is_active, sort_order, created_at)
SELECT s.id, cats.name, cats.icon_code, cats.color_code, TRUE, cats.sort_order, CURRENT_TIMESTAMP
FROM shop s
CROSS JOIN (
  SELECT 'Office Supplies' AS name, 'inventory' AS icon_code, '#1976d2' AS color_code, 1 AS sort_order UNION ALL
  SELECT 'Travel & Conveyance', 'flight', '#388e3c', 2 UNION ALL
  SELECT 'Meals & Entertainment', 'restaurant', '#f57c00', 3 UNION ALL
  SELECT 'Utilities & Internet', 'power', '#7b1fa2', 4 UNION ALL
  SELECT 'Rent & Maintenance', 'home', '#d32f2f', 5 UNION ALL
  SELECT 'Professional Services', 'work', '#0288d1', 6 UNION ALL
  SELECT 'Marketing & Advertising', 'campaign', '#e64a19', 7 UNION ALL
  SELECT 'Miscellaneous', 'category', '#616161', 8
) cats;
