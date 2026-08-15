-- Migration V90 (Phase 6.2 — enterprise gaps): GRN cancellation + status audit.
--
-- Two additions:
--   1) receiving.cancellation_reason column so a cancelled GRN carries the
--      reason inline (audit + supplier communication).
--   2) receiving_status_history table for a full audit trail of every status
--      transition (mirrors DeliveryStatusHistory pattern). Compliance + dispute
--      resolution both need this — the current @LastModifiedDate only captures
--      the latest change.
--
-- INFORMATION_SCHEMA-guarded per the MySQL migration pitfalls memo.

DELIMITER $$

DROP PROCEDURE IF EXISTS add_col_if_missing_v90 $$
CREATE PROCEDURE add_col_if_missing_v90(IN tbl VARCHAR(64), IN col VARCHAR(64), IN col_def VARCHAR(255))
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

CALL add_col_if_missing_v90('receiving', 'cancellation_reason', 'VARCHAR(500) NULL DEFAULT NULL');
CALL add_col_if_missing_v90('receiving', 'cancelled_at',        'TIMESTAMP NULL DEFAULT NULL');
CALL add_col_if_missing_v90('receiving', 'cancelled_by_user_id','BIGINT NULL DEFAULT NULL');

DROP PROCEDURE IF EXISTS add_col_if_missing_v90;

CREATE TABLE IF NOT EXISTS receiving_status_history (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    receiving_id  BIGINT       NOT NULL,
    shop_id       BIGINT       NOT NULL,
    from_status   VARCHAR(30)  NULL,
    to_status     VARCHAR(30)  NOT NULL,
    changed_by    VARCHAR(100) NULL,
    changed_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    note          VARCHAR(500) NULL,

    INDEX idx_rsh_receiving_id (receiving_id),
    INDEX idx_rsh_shop_id      (shop_id),
    CONSTRAINT fk_rsh_receiving FOREIGN KEY (receiving_id) REFERENCES receiving(id) ON DELETE CASCADE
);
