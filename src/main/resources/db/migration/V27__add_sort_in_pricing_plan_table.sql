ALTER TABLE pricing_plan_configs
ADD COLUMN sort_order INT DEFAULT 0;

UPDATE pricing_plan_configs SET sort_order = 1 WHERE tier = 'STARTER';
UPDATE pricing_plan_configs SET sort_order = 2 WHERE tier = 'PRO';
UPDATE pricing_plan_configs SET sort_order = 3 WHERE tier = 'ENTERPRISE';