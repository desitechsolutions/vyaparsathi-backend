-- Migration V88 (Phase 6.1): receiving_ticket lifecycle.
-- The status column stays VARCHAR (Java @Enumerated STRING) — we're only
-- adding resolution audit columns so a ticket can be worked through
-- OPEN → IN_PROGRESS → RESOLVED/CLOSED. Legacy string values keep working
-- (the enum's fromString is lenient).
--
-- Also opportunistically widens status to accommodate the enum names.

DELIMITER $$

DROP PROCEDURE IF EXISTS add_col_if_missing_v88 $$
CREATE PROCEDURE add_col_if_missing_v88(IN tbl VARCHAR(64), IN col VARCHAR(64), IN col_def VARCHAR(255))
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = tbl AND COLUMN_NAME = col
  ) THEN
    SET @s = CONCAT('ALTER TABLE `', tbl, '` ADD COLUMN `', col, '` ', col_def);
    PREPARE stmt FROM @s;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END $$

DELIMITER ;

-- Resolution audit — populated when a ticket moves to RESOLVED / CLOSED.
CALL add_col_if_missing_v88('receiving_ticket', 'resolved_by',     'BIGINT NULL DEFAULT NULL');
CALL add_col_if_missing_v88('receiving_ticket', 'resolved_at',     'TIMESTAMP NULL DEFAULT NULL');
CALL add_col_if_missing_v88('receiving_ticket', 'resolution_note', 'VARCHAR(1000) NULL DEFAULT NULL');

-- Retire orphan status values. Historical rows may have had null or free-text
-- values; normalize to OPEN so the new enum can deserialize them.
UPDATE receiving_ticket
   SET status = 'OPEN'
 WHERE status IS NULL OR status = '' OR status NOT IN ('OPEN','IN_PROGRESS','RESOLVED','CLOSED');

DROP PROCEDURE IF EXISTS add_col_if_missing_v88;