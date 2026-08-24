-- Comprehensive PF, ESI, PT, TDS seed data for FY 2024-25

-- PF Slabs (India Standard) - All Shops
INSERT IGNORE INTO pf_slabs (shop_id, effective_from, wage_limit, employee_contribution_rate, employer_contribution_rate, epf_contribution_rate, eps_contribution_rate, is_active, created_at)
SELECT id, '2024-04-01', 50000.00, 12.00, 12.00, 3.67, 8.33, TRUE, CURRENT_TIMESTAMP FROM shop;

-- ESI Slabs - All Shops
INSERT IGNORE INTO esi_slabs (shop_id, effective_from, wage_ceiling, employee_rate, employer_rate, is_active, created_at)
SELECT id, '2024-04-01', 21000.00, 0.75, 3.25, TRUE, CURRENT_TIMESTAMP FROM shop;

-- PT Slabs by State (Sample for major states)
-- Maharashtra
INSERT IGNORE INTO pt_slabs (shop_id, pt_state, effective_from, salary_from, salary_to, pt_amount, is_active, created_at) VALUES
(1, 'MH', '2024-04-01', 0.00, 10000.00, 0.00, TRUE, CURRENT_TIMESTAMP),
(1, 'MH', '2024-04-01', 10001.00, 20000.00, 150.00, TRUE, CURRENT_TIMESTAMP),
(1, 'MH', '2024-04-01', 20001.00, 30000.00, 200.00, TRUE, CURRENT_TIMESTAMP),
(1, 'MH', '2024-04-01', 30001.00, 999999.00, 250.00, TRUE, CURRENT_TIMESTAMP);

-- Delhi
INSERT IGNORE INTO pt_slabs (shop_id, pt_state, effective_from, salary_from, salary_to, pt_amount, is_active, created_at) VALUES
(1, 'DL', '2024-04-01', 0.00, 15000.00, 0.00, TRUE, CURRENT_TIMESTAMP),
(1, 'DL', '2024-04-01', 15001.00, 25000.00, 100.00, TRUE, CURRENT_TIMESTAMP),
(1, 'DL', '2024-04-01', 25001.00, 999999.00, 200.00, TRUE, CURRENT_TIMESTAMP);

-- Karnataka
INSERT IGNORE INTO pt_slabs (shop_id, pt_state, effective_from, salary_from, salary_to, pt_amount, is_active, created_at) VALUES
(1, 'KA', '2024-04-01', 0.00, 12000.00, 0.00, TRUE, CURRENT_TIMESTAMP),
(1, 'KA', '2024-04-01', 12001.00, 22000.00, 100.00, TRUE, CURRENT_TIMESTAMP),
(1, 'KA', '2024-04-01', 22001.00, 999999.00, 200.00, TRUE, CURRENT_TIMESTAMP);

-- Tamil Nadu
INSERT IGNORE INTO pt_slabs (shop_id, pt_state, effective_from, salary_from, salary_to, pt_amount, is_active, created_at) VALUES
(1, 'TN', '2024-04-01', 0.00, 11000.00, 0.00, TRUE, CURRENT_TIMESTAMP),
(1, 'TN', '2024-04-01', 11001.00, 21000.00, 100.00, TRUE, CURRENT_TIMESTAMP),
(1, 'TN', '2024-04-01', 21001.00, 999999.00, 200.00, TRUE, CURRENT_TIMESTAMP);

-- TDS Slabs - New Regime FY 2024-25 (All Shops)
INSERT IGNORE INTO tds_slabs (shop_id, tax_regime, effective_from, income_from, income_to, tax_rate, is_active, created_at)
SELECT shop.id, 'NEW_REGIME', '2024-04-01', rates.income_from, rates.income_to, rates.tax_rate, TRUE, CURRENT_TIMESTAMP
FROM shop CROSS JOIN (
  SELECT 0.00 as income_from, 300000.00 as income_to, 0.00 as tax_rate UNION ALL
  SELECT 300001.00, 400000.00, 5.00 UNION ALL
  SELECT 400001.00, 500000.00, 10.00 UNION ALL
  SELECT 500001.00, 600000.00, 15.00 UNION ALL
  SELECT 600001.00, 900000.00, 20.00 UNION ALL
  SELECT 900001.00, 1200000.00, 30.00 UNION ALL
  SELECT 1200001.00, 9999999.00, 30.00
) rates;

-- TDS Slabs - Old Regime FY 2024-25 (All Shops)
INSERT IGNORE INTO tds_slabs (shop_id, tax_regime, effective_from, income_from, income_to, tax_rate, is_active, created_at)
SELECT shop.id, 'OLD_REGIME', '2024-04-01', rates.income_from, rates.income_to, rates.tax_rate, TRUE, CURRENT_TIMESTAMP
FROM shop CROSS JOIN (
  SELECT 0.00 as income_from, 250000.00 as income_to, 0.00 as tax_rate UNION ALL
  SELECT 250001.00, 500000.00, 5.00 UNION ALL
  SELECT 500001.00, 1000000.00, 20.00 UNION ALL
  SELECT 1000001.00, 9999999.00, 30.00
) rates;

-- ESS Preferences (default for new employees)
INSERT IGNORE INTO ess_preferences (employee_id, payslip_dispatch_email, payslip_dispatch_whatsapp, payslip_dispatch_sms, auto_tax_calculation, notification_opt_in, created_at)
SELECT id, TRUE, FALSE, FALSE, TRUE, TRUE, CURRENT_TIMESTAMP FROM employees WHERE id IS NOT NULL LIMIT 100;

-- Default Statutory Config (All Shops)
INSERT IGNORE INTO statutory_config (shop_id, pf_uan, esic_code, pt_state, tax_regime, bank_name, created_at)
SELECT id, CONCAT('UP/DBN/2024/', LPAD(id, 5, '0')), CONCAT('AP', LPAD(id, 14, '0')), 'MH', 'NEW_REGIME', 'ICICI Bank', CURRENT_TIMESTAMP FROM shop;
