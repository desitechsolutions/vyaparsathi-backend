-- Migration V85 (Phase 3 of the Purchase Order enterprise redesign):
-- approval workflow. Adds a PENDING_APPROVAL state (via the enum column, no
-- schema change needed for the enum value itself — Java @Enumerated STRING
-- means it's just a new value the JVM knows about), audit columns on
-- purchase_order for the four approval events, and shop-level policy toggles
-- so shops that don't need approval keep the previous DRAFT→SUBMITTED flow.
--
-- Additions to `purchase_order`:
--   * `submitted_by`         — user id who submitted the PO (either for
--                              approval or straight-to-SUBMITTED)
--   * `approved_by` / `approved_at` — set when OWNER/ADMIN approves
--   * `rejected_by` / `rejected_at` — set when OWNER/ADMIN rejects
--   * `rejection_reason`     — free-text @NotBlank on reject
--
-- Additions to `shop`:
--   * `po_approval_required`         — BOOLEAN, default FALSE (opt-in)
--   * `po_approval_threshold_amount` — DECIMAL, default 0 (0 = every PO,
--                                      non-zero = only POs at or above this
--                                      amount need approval)
--
-- INFORMATION_SCHEMA-guarded so re-runs are safe.

DELIMITER $$

DROP PROCEDURE IF EXISTS add_col_if_missing_v85 $$
CREATE PROCEDURE add_col_if_missing_v85(IN tbl VARCHAR(64), IN col VARCHAR(64), IN col_def VARCHAR(255))
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

-- purchase_order audit columns
CALL add_col_if_missing_v85('purchase_order', 'submitted_by',     'BIGINT NULL DEFAULT NULL');
CALL add_col_if_missing_v85('purchase_order', 'approved_by',      'BIGINT NULL DEFAULT NULL');
CALL add_col_if_missing_v85('purchase_order', 'approved_at',      'TIMESTAMP NULL DEFAULT NULL');
CALL add_col_if_missing_v85('purchase_order', 'rejected_by',      'BIGINT NULL DEFAULT NULL');
CALL add_col_if_missing_v85('purchase_order', 'rejected_at',      'TIMESTAMP NULL DEFAULT NULL');
CALL add_col_if_missing_v85('purchase_order', 'rejection_reason', 'VARCHAR(500) NULL DEFAULT NULL');

-- shop-level opt-in
CALL add_col_if_missing_v85('shop', 'po_approval_required',         'BOOLEAN NOT NULL DEFAULT FALSE');
CALL add_col_if_missing_v85('shop', 'po_approval_threshold_amount', 'DECIMAL(12,2) NOT NULL DEFAULT 0');

DROP PROCEDURE IF EXISTS add_col_if_missing_v85;