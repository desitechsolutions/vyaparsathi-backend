-- Migration V81 (Phase 1 of the Purchase Order enterprise redesign):
-- clean state-machine metadata + per-line received quantity.
--
-- Additions to `purchase_order`:
--   * `cancelled_at`         — when a PO transitioned to CANCELLED (nullable)
--   * `cancelled_by`         — user id who cancelled (nullable)
--   * `cancellation_reason`  — free-text audit note (nullable)
--   * `sent_at`              — when the PO was emailed to the supplier
--                              (Phase 5 wires the email; the column ships now
--                              so the "Send" button in Phase 2 can already
--                              stamp it)
--   * `received_at`          — when the PO was fully received (RECEIVED)
--
-- Addition to `purchase_order_item`:
--   * `received_quantity`    — cumulative received qty per PO line. Powers the
--                              on-order calc (ordered − received) and the
--                              receipt-progress bar in the redesigned FE.
--
-- Data migration:
--   * Any legacy `PENDING` PO becomes `SUBMITTED` (the audit found this
--     status was orphaned — no service method writes it).
--   * Any legacy `IN_PROGRESS` PO becomes `PARTIALLY_RECEIVED`.
--
-- Everything is guarded via INFORMATION_SCHEMA per the MySQL migration
-- pitfalls memory note, so re-running is safe.

DELIMITER $$

DROP PROCEDURE IF EXISTS add_col_if_missing_v81 $$
CREATE PROCEDURE add_col_if_missing_v81(IN tbl VARCHAR(64), IN col VARCHAR(64), IN col_def VARCHAR(255))
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

-- Header-level state fields
CALL add_col_if_missing_v81('purchase_order', 'cancelled_at',        'TIMESTAMP NULL DEFAULT NULL');
CALL add_col_if_missing_v81('purchase_order', 'cancelled_by',        'BIGINT NULL DEFAULT NULL');
CALL add_col_if_missing_v81('purchase_order', 'cancellation_reason', 'VARCHAR(500) NULL DEFAULT NULL');
CALL add_col_if_missing_v81('purchase_order', 'sent_at',             'TIMESTAMP NULL DEFAULT NULL');
CALL add_col_if_missing_v81('purchase_order', 'received_at',         'TIMESTAMP NULL DEFAULT NULL');

-- Line-level received qty. Default 0 so ordered-vs-received math works even
-- before any receipt is recorded.
CALL add_col_if_missing_v81('purchase_order_item', 'received_quantity',
    'DECIMAL(12,2) NOT NULL DEFAULT 0');

-- Retire orphan statuses. Both are enum values that no service method ever
-- writes today, so mapping them forward is a no-op if none exist.
UPDATE purchase_order SET status = 'SUBMITTED'          WHERE status = 'PENDING';
UPDATE purchase_order SET status = 'PARTIALLY_RECEIVED' WHERE status = 'IN_PROGRESS';

DROP PROCEDURE IF EXISTS add_col_if_missing_v81;
