-- Migration V80: Per-shop low-stock email alert opt-in + per-variant
-- last-notified timestamp so the digest is deduped across daily runs.
--
-- The scheduler iterates only shops with low_stock_alerts_enabled = true
-- AND a non-null email; per variant it only re-notifies when
-- alert_last_notified_at is null or older than the resend window (default
-- 24h — see LowStockAlertNotificationScheduler).
--
-- Guarded via INFORMATION_SCHEMA so the migration is re-runnable (see the
-- MySQL migration pitfalls memory note).

DELIMITER $$

DROP PROCEDURE IF EXISTS add_col_if_missing_v80 $$
CREATE PROCEDURE add_col_if_missing_v80(IN tbl VARCHAR(64), IN col VARCHAR(64), IN col_def VARCHAR(255))
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

-- Shop-level opt-in (email + SMS split — SMS infra is a follow-up but the
-- flag lands now so the Shop Settings UI can render both toggles).
CALL add_col_if_missing_v80('shop', 'low_stock_alerts_enabled',      'BOOLEAN NOT NULL DEFAULT FALSE');
CALL add_col_if_missing_v80('shop', 'low_stock_sms_alerts_enabled',  'BOOLEAN NOT NULL DEFAULT FALSE');

-- Per-variant dedupe stamp for the scheduler
CALL add_col_if_missing_v80('item_variant', 'alert_last_notified_at', 'TIMESTAMP NULL DEFAULT NULL');

DROP PROCEDURE IF EXISTS add_col_if_missing_v80;
