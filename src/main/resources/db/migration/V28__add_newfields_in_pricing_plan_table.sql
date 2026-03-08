ALTER TABLE pricing_plan_configs
ADD COLUMN max_sales_per_month INT DEFAULT 25,
ADD COLUMN max_items INT DEFAULT 25,
ADD COLUMN max_staff_users INT DEFAULT 1;