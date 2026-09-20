-- Dashboard and report queries filter sales by shop_id + date range very frequently.
-- This composite index covers those WHERE clauses and avoids full-table scans.
-- MySQL-safe: guarded with INFORMATION_SCHEMA check before creation.
DROP PROCEDURE IF EXISTS add_sale_shop_date_index;

DELIMITER $$
CREATE PROCEDURE add_sale_shop_date_index()
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name   = 'sale'
          AND index_name   = 'idx_sale_shop_date_status'
    ) THEN
        CREATE INDEX idx_sale_shop_date_status ON sale(shop_id, date, status);
    END IF;
END$$
DELIMITER ;

CALL add_sale_shop_date_index();
DROP PROCEDURE IF EXISTS add_sale_shop_date_index;
