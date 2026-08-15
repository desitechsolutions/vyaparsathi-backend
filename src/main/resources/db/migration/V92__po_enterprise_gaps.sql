-- Migration V92 — PO enterprise gaps from po_enterprise_audit.md.
--
-- Closes the P0/P1 audit findings:
--   * purchase_order_status_history — every transition captured (mirrors
--     receiving_status_history from V90)
--   * purchase_order_approval — multi-level approval workflow (mirrors
--     receiving_approval from V91)
--   * Landed cost opt-in flag + per-line landed cost on purchase_order_item
--   * Approval delegation columns on the shop table so an approver on leave
--     can delegate to a peer without changing user roles.
--
-- INFORMATION_SCHEMA-guarded per the MySQL migration pitfalls memo.

DELIMITER $$

DROP PROCEDURE IF EXISTS add_col_if_missing_v92 $$
CREATE PROCEDURE add_col_if_missing_v92(IN tbl VARCHAR(64), IN col VARCHAR(64), IN col_def VARCHAR(255))
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

-- Landed cost: allocate freight into line unit cost so inventory valuation is
-- correct. Opt-in per PO so the flag can be flipped later without breaking
-- historical rows.
CALL add_col_if_missing_v92('purchase_order',      'landed_cost_enabled', 'BOOLEAN NOT NULL DEFAULT FALSE');
CALL add_col_if_missing_v92('purchase_order_item', 'landed_unit_cost',    'DECIMAL(12,4) NULL DEFAULT NULL');

-- Approval delegation: a shop-scoped map of "who covers for whom" so the
-- approval router can fall over from the primary approver on leave.
CALL add_col_if_missing_v92('shop', 'approval_delegate_user_id', 'BIGINT NULL DEFAULT NULL');
CALL add_col_if_missing_v92('shop', 'approval_delegate_until',   'DATE NULL DEFAULT NULL');

DROP PROCEDURE IF EXISTS add_col_if_missing_v92;

CREATE TABLE IF NOT EXISTS purchase_order_status_history (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    purchase_order_id BIGINT       NOT NULL,
    shop_id           BIGINT       NOT NULL,
    from_status       VARCHAR(30)  NULL,
    to_status         VARCHAR(30)  NOT NULL,
    changed_by        VARCHAR(100) NULL,
    changed_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    note              VARCHAR(500) NULL,

    INDEX idx_posh_po (purchase_order_id),
    INDEX idx_posh_shop (shop_id),
    CONSTRAINT fk_posh_po FOREIGN KEY (purchase_order_id) REFERENCES purchase_order(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS purchase_order_approval (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    purchase_order_id BIGINT       NOT NULL,
    shop_id           BIGINT       NOT NULL,
    level             SMALLINT     NOT NULL,
    approver_role     VARCHAR(50)  NULL,
    approver_id       BIGINT       NULL,
    delegate_id       BIGINT       NULL,
    status            VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    threshold_min     DECIMAL(14,2) NULL,
    approved_at       TIMESTAMP    NULL DEFAULT NULL,
    note              VARCHAR(500) NULL,
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_po_appr_po (purchase_order_id),
    INDEX idx_po_appr_shop (shop_id),
    CONSTRAINT fk_po_appr_po FOREIGN KEY (purchase_order_id) REFERENCES purchase_order(id) ON DELETE CASCADE
);
