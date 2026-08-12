-- Enterprise sale fields — idempotent: every ADD COLUMN / CREATE INDEX is
-- guarded by an information_schema lookup so a partial re-run is safe.
-- Uses inline PREPARE/EXECUTE (no stored procedures) so Flyway's default
-- statement splitter handles it cleanly.

-- ── Columns on `sale` ─────────────────────────────────────────────────
SET @stmt := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sale' AND COLUMN_NAME = 'idempotency_key') = 0,
  'ALTER TABLE sale ADD COLUMN idempotency_key VARCHAR(80) NULL',
  'DO 0');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

SET @stmt := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sale' AND COLUMN_NAME = 'salesperson_id') = 0,
  'ALTER TABLE sale ADD COLUMN salesperson_id BIGINT NULL',
  'DO 0');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

SET @stmt := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sale' AND COLUMN_NAME = 'notes') = 0,
  'ALTER TABLE sale ADD COLUMN notes TEXT NULL',
  'DO 0');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

-- ── Indexes on `sale` ─────────────────────────────────────────────────
SET @stmt := IF(
  (SELECT COUNT(*) FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sale' AND INDEX_NAME = 'idx_sale_shop_idempotency') = 0,
  'CREATE UNIQUE INDEX idx_sale_shop_idempotency ON sale(shop_id, idempotency_key)',
  'DO 0');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

SET @stmt := IF(
  (SELECT COUNT(*) FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sale' AND INDEX_NAME = 'idx_sale_shop_date') = 0,
  'CREATE INDEX idx_sale_shop_date ON sale(shop_id, date)',
  'DO 0');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

SET @stmt := IF(
  (SELECT COUNT(*) FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sale' AND INDEX_NAME = 'idx_sale_shop_status') = 0,
  'CREATE INDEX idx_sale_shop_status ON sale(shop_id, status)',
  'DO 0');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

SET @stmt := IF(
  (SELECT COUNT(*) FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sale' AND INDEX_NAME = 'idx_sale_shop_customer') = 0,
  'CREATE INDEX idx_sale_shop_customer ON sale(shop_id, customer_id)',
  'DO 0');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

SET @stmt := IF(
  (SELECT COUNT(*) FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sale' AND INDEX_NAME = 'idx_sale_shop_paystatus') = 0,
  'CREATE INDEX idx_sale_shop_paystatus ON sale(shop_id, payment_status)',
  'DO 0');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

-- ── Column + index on `sale_item` ─────────────────────────────────────
SET @stmt := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sale_item' AND COLUMN_NAME = 'salesperson_id') = 0,
  'ALTER TABLE sale_item ADD COLUMN salesperson_id BIGINT NULL',
  'DO 0');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

SET @stmt := IF(
  (SELECT COUNT(*) FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sale_item' AND INDEX_NAME = 'idx_sale_item_salesperson') = 0,
  'CREATE INDEX idx_sale_item_salesperson ON sale_item(salesperson_id)',
  'DO 0');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;
