-- Add can_process_sale column to pricing_plan_configs table
ALTER TABLE pricing_plan_configs
ADD COLUMN can_process_sale BOOLEAN DEFAULT TRUE;

-- Ensure FREE tier row exists in pricing_plan_configs table
INSERT INTO pricing_plan_configs (tier, display_name, monthly_price, yearly_price, discount_percentage, is_popular, is_active, sort_order, max_sales_per_month, max_items, max_staff_users, can_process_sale)
VALUES ('FREE', 'Free Plan', 0.00, 0.00, 0, FALSE, TRUE, 0, 25, 25, 1, TRUE)
ON DUPLICATE KEY UPDATE can_process_sale = VALUES(can_process_sale);
