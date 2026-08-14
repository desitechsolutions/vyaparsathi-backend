-- V75: Correct per-tier monthly sales quotas.
--
-- The V28 migration added `max_sales_per_month INT DEFAULT 25` and every seeded
-- row (FREE / STARTER / PRO / ENTERPRISE) inherited that 25 default. That made
-- ENTERPRISE customers hit "Monthly limit reached" on the 26th sale of each
-- month with no way through the front door — the only workaround was the
-- draft-then-complete bypass (now closed by @CheckSubscriptionLimit on
-- completeDraft and convertProformaToInvoice).
--
-- Standard SaaS ladder: FREE 25 · STARTER 250 · PRO 2500 · ENTERPRISE unlimited.
-- The aspect (`SubscriptionLimitAspect`) treats NULL/0 as "no limit".
--
-- Idempotent: uses tier as the WHERE key so re-running against an already-
-- fixed row is a no-op.

UPDATE pricing_plan_configs SET max_sales_per_month = 25   WHERE tier = 'FREE';
UPDATE pricing_plan_configs SET max_sales_per_month = 250  WHERE tier = 'STARTER';
UPDATE pricing_plan_configs SET max_sales_per_month = 2500 WHERE tier = 'PRO';
UPDATE pricing_plan_configs SET max_sales_per_month = NULL WHERE tier = 'ENTERPRISE';
