-- Migration V89 (Phase 6.1): approval note on Receiving.
-- The approved_by_user_id + approved_at columns already exist (see the
-- receiving entity). Adding an optional note so an OWNER/ADMIN can log a
-- reason ("QC verified, batch #1234 matches PO") when approving a GRN.
-- INFORMATION_SCHEMA-guarded per the MySQL migration pitfalls memo.

DELIMITER $$

DROP PROCEDURE IF EXISTS add_col_if_missing_v89 $$
CREATE PROCEDURE add_col_if_missing_v89(IN tbl VARCHAR(64), IN col VARCHAR(64), IN col_def VARCHAR(255))
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

CALL add_col_if_missing_v89('receiving', 'approval_note', 'VARCHAR(500) NULL DEFAULT NULL');

DROP PROCEDURE IF EXISTS add_col_if_missing_v89;