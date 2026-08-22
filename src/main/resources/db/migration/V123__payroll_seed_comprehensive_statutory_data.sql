-- Comprehensive PF, ESI, PT, TDS seed data for FY 2024-25

-- PF Slabs (India Standard)
INSERT INTO pf_slabs (shop_id, effective_from, wage_limit, employee_contribution_rate, employer_contribution_rate, epf_contribution_rate, eps_contribution_rate, is_active, created_at)
SELECT id, '2024-04-01', 50000.00, 12.00, 12.00, 3.67, 8.33, TRUE, CURRENT_TIMESTAMP FROM shops WHERE id = 1
ON DUPLICATE KEY UPDATE created_at = created_at;

-- ESI Slabs
INSERT INTO esi_slabs (shop_id, effective_from, wage_ceiling, employee_rate, employer_rate, is_active, created_at)
SELECT id, '2024-04-01', 21000.00, 0.75, 3.25, TRUE, CURRENT_TIMESTAMP FROM shops WHERE id = 1
ON DUPLICATE KEY UPDATE created_at = created_at;

-- PT Slabs by State (Sample for major states)
-- Maharashtra
INSERT INTO pt_slabs (shop_id, pt_state, effective_from, salary_from, salary_to, pt_amount, is_active, created_at) VALUES
(1, 'MH', '2024-04-01', 0.00, 10000.00, 0.00, TRUE, CURRENT_TIMESTAMP),
(1, 'MH', '2024-04-01', 10001.00, 20000.00, 150.00, TRUE, CURRENT_TIMESTAMP),
(1, 'MH', '2024-04-01', 20001.00, 30000.00, 200.00, TRUE, CURRENT_TIMESTAMP),
(1, 'MH', '2024-04-01', 30001.00, 999999.00, 250.00, TRUE, CURRENT_TIMESTAMP)
ON DUPLICATE KEY UPDATE created_at = created_at;

-- Delhi
INSERT INTO pt_slabs (shop_id, pt_state, effective_from, salary_from, salary_to, pt_amount, is_active, created_at) VALUES
(1, 'DL', '2024-04-01', 0.00, 15000.00, 0.00, TRUE, CURRENT_TIMESTAMP),
(1, 'DL', '2024-04-01', 15001.00, 25000.00, 100.00, TRUE, CURRENT_TIMESTAMP),
(1, 'DL', '2024-04-01', 25001.00, 999999.00, 200.00, TRUE, CURRENT_TIMESTAMP)
ON DUPLICATE KEY UPDATE created_at = created_at;

-- Karnataka
INSERT INTO pt_slabs (shop_id, pt_state, effective_from, salary_from, salary_to, pt_amount, is_active, created_at) VALUES
(1, 'KA', '2024-04-01', 0.00, 12000.00, 0.00, TRUE, CURRENT_TIMESTAMP),
(1, 'KA', '2024-04-01', 12001.00, 22000.00, 100.00, TRUE, CURRENT_TIMESTAMP),
(1, 'KA', '2024-04-01', 22001.00, 999999.00, 200.00, TRUE, CURRENT_TIMESTAMP)
ON DUPLICATE KEY UPDATE created_at = created_at;

-- Tamil Nadu
INSERT INTO pt_slabs (shop_id, pt_state, effective_from, salary_from, salary_to, pt_amount, is_active, created_at) VALUES
(1, 'TN', '2024-04-01', 0.00, 11000.00, 0.00, TRUE, CURRENT_TIMESTAMP),
(1, 'TN', '2024-04-01', 11001.00, 21000.00, 100.00, TRUE, CURRENT_TIMESTAMP),
(1, 'TN', '2024-04-01', 21001.00, 999999.00, 200.00, TRUE, CURRENT_TIMESTAMP)
ON DUPLICATE KEY UPDATE created_at = created_at;

-- TDS Slabs - New Regime FY 2024-25
INSERT INTO tds_slabs (shop_id, tax_regime, effective_from, income_from, income_to, tax_rate, is_active, created_at) VALUES
(1, 'NEW_REGIME', '2024-04-01', 0.00, 300000.00, 0.00, TRUE, CURRENT_TIMESTAMP),
(1, 'NEW_REGIME', '2024-04-01', 300001.00, 400000.00, 5.00, TRUE, CURRENT_TIMESTAMP),
(1, 'NEW_REGIME', '2024-04-01', 400001.00, 500000.00, 10.00, TRUE, CURRENT_TIMESTAMP),
(1, 'NEW_REGIME', '2024-04-01', 500001.00, 600000.00, 15.00, TRUE, CURRENT_TIMESTAMP),
(1, 'NEW_REGIME', '2024-04-01', 600001.00, 900000.00, 20.00, TRUE, CURRENT_TIMESTAMP),
(1, 'NEW_REGIME', '2024-04-01', 900001.00, 1200000.00, 30.00, TRUE, CURRENT_TIMESTAMP),
(1, 'NEW_REGIME', '2024-04-01', 1200001.00, 9999999.00, 30.00, TRUE, CURRENT_TIMESTAMP)
ON DUPLICATE KEY UPDATE created_at = created_at;

-- TDS Slabs - Old Regime FY 2024-25
INSERT INTO tds_slabs (shop_id, tax_regime, effective_from, income_from, income_to, tax_rate, is_active, created_at) VALUES
(1, 'OLD_REGIME', '2024-04-01', 0.00, 250000.00, 0.00, TRUE, CURRENT_TIMESTAMP),
(1, 'OLD_REGIME', '2024-04-01', 250001.00, 500000.00, 5.00, TRUE, CURRENT_TIMESTAMP),
(1, 'OLD_REGIME', '2024-04-01', 500001.00, 1000000.00, 20.00, TRUE, CURRENT_TIMESTAMP),
(1, 'OLD_REGIME', '2024-04-01', 1000001.00, 9999999.00, 30.00, TRUE, CURRENT_TIMESTAMP)
ON DUPLICATE KEY UPDATE created_at = created_at;

-- ESS Preferences (default for new employees)
INSERT IGNORE INTO ess_preferences (employee_id, payslip_dispatch_email, payslip_dispatch_whatsapp, payslip_dispatch_sms, auto_tax_calculation, notification_opt_in, created_at)
SELECT id, TRUE, FALSE, FALSE, TRUE, TRUE, CURRENT_TIMESTAMP FROM employees WHERE id IS NOT NULL LIMIT 100;

-- Default Statutory Config
INSERT INTO statutory_config (shop_id, pf_uan, esic_code, pt_state, tax_regime, bank_name, created_at)
SELECT id, 'UP/DBN/2024/00001', 'AP1234567800000', 'MH', 'NEW_REGIME', 'ICICI Bank', CURRENT_TIMESTAMP FROM shops WHERE id = 1
ON DUPLICATE KEY UPDATE created_at = created_at;
