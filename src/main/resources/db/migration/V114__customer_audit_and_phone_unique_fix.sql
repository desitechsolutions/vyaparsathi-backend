-- V114: Customer module Phase 1 fixes
--
-- Two blocker bugs found in the pre-redesign audit:
--
-- 1. `customer_audit` entity exists and is written on every customer
--    CRUD op, but no migration ever created the table. Every customer
--    mutation currently throws SQL exception on a fresh install.
--
-- 2. `customer.phone` has a GLOBAL UNIQUE constraint from the entity
--    annotation `@Column(length = 15, unique = true)` on the same
--    Customer.java field. In a multi-tenant schema this means shop A
--    and shop B cannot share a phone number — obviously wrong. The
--    correct constraint is UNIQUE(shop_id, phone) so uniqueness is
--    scoped to a tenant.

-- ─── Helper procedures (idempotent — safe to rerun) ─────────────────
DROP PROCEDURE IF EXISTS v114_drop_index;
DELIMITER $$
CREATE PROCEDURE v114_drop_index(IN tbl VARCHAR(64), IN idx VARCHAR(64))
BEGIN
    IF EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = tbl AND INDEX_NAME = idx
    ) THEN
        SET @s = CONCAT('DROP INDEX `', idx, '` ON `', tbl, '`');
        PREPARE stmt FROM @s;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

DROP PROCEDURE IF EXISTS v114_ensure_index;
DELIMITER $$
CREATE PROCEDURE v114_ensure_index(IN tbl VARCHAR(64), IN idx VARCHAR(64), IN cols VARCHAR(200), IN unique_flag INT)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = tbl AND INDEX_NAME = idx
    ) THEN
        IF unique_flag = 1 THEN
            SET @s = CONCAT('CREATE UNIQUE INDEX `', idx, '` ON `', tbl, '` (', cols, ')');
        ELSE
            SET @s = CONCAT('CREATE INDEX `', idx, '` ON `', tbl, '` (', cols, ')');
        END IF;
        PREPARE stmt FROM @s;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

-- ─── 1. Create customer_audit table ─────────────────────────────────
CREATE TABLE IF NOT EXISTS customer_audit (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    shop_id      BIGINT NOT NULL,
    customer_id  BIGINT NOT NULL,
    action       VARCHAR(30) NOT NULL,
    performed_by VARCHAR(100),
    changes      TEXT,
    summary      VARCHAR(500),
    created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_customer_audit_customer (customer_id),
    KEY idx_customer_audit_shop (shop_id),
    KEY idx_customer_audit_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ─── 2. Fix customer.phone uniqueness scope ─────────────────────────
-- Drop whatever the current global-unique index is called. Hibernate
-- names entity-generated unique constraints `UK<hash>` — we can't rely
-- on a fixed name, so scan INFORMATION_SCHEMA for any UNIQUE index on
-- just the phone column and drop it.
DROP PROCEDURE IF EXISTS v114_drop_phone_unique;
DELIMITER $$
CREATE PROCEDURE v114_drop_phone_unique()
BEGIN
    DECLARE done INT DEFAULT 0;
    DECLARE idx_name VARCHAR(128);
    DECLARE cur CURSOR FOR
        SELECT s.INDEX_NAME
          FROM INFORMATION_SCHEMA.STATISTICS s
         WHERE s.TABLE_SCHEMA = DATABASE()
           AND s.TABLE_NAME = 'customer'
           AND s.NON_UNIQUE = 0
           AND s.INDEX_NAME <> 'PRIMARY'
           AND s.COLUMN_NAME = 'phone'
           -- Only indexes that are (phone) alone, not composite ones
           AND NOT EXISTS (
               SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS s2
                WHERE s2.TABLE_SCHEMA = s.TABLE_SCHEMA
                  AND s2.TABLE_NAME  = s.TABLE_NAME
                  AND s2.INDEX_NAME  = s.INDEX_NAME
                  AND s2.COLUMN_NAME <> 'phone'
           );
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;
    OPEN cur;
    read_loop: LOOP
        FETCH cur INTO idx_name;
        IF done = 1 THEN LEAVE read_loop; END IF;
        SET @s = CONCAT('DROP INDEX `', idx_name, '` ON customer');
        PREPARE stmt FROM @s;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END LOOP;
    CLOSE cur;
END$$
DELIMITER ;

CALL v114_drop_phone_unique();

-- Add the correct composite index. Guard for idempotency.
CALL v114_ensure_index('customer', 'uk_customer_shop_phone', 'shop_id, phone', 1);

-- ─── Cleanup ─────────────────────────────────────────────────────────
DROP PROCEDURE IF EXISTS v114_drop_index;
DROP PROCEDURE IF EXISTS v114_ensure_index;
DROP PROCEDURE IF EXISTS v114_drop_phone_unique;
