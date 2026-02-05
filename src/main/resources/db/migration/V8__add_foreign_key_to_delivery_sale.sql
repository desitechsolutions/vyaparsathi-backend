-- V9__ensure_delivery_sale_fk.sql
-- Ensures FK exists without touching index (Flyway + MySQL safe)

SET @fk_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.REFERENTIAL_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE()
      AND TABLE_NAME = 'deliveries'
      AND REFERENCED_TABLE_NAME = 'sale'
);

SET @sql := IF(
    @fk_exists = 0,
    'ALTER TABLE deliveries
        ADD CONSTRAINT fk_delivery_sale
        FOREIGN KEY (sale_id)
        REFERENCES sale(id)
        ON DELETE CASCADE
        ON UPDATE CASCADE',
    'SELECT 1'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
