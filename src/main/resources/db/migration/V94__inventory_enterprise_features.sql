-- Migration V94 — closes the P0/P1 gaps from inventory_enterprise_audit.md.
--
-- Adds:
--   * stock_reservation — soft-hold on qty for sales/quotations before commit
--   * cycle_count / cycle_count_line — periodic stocktake workflow
--   * batch_recall / batch_recall_impact — recall traceout
--   * stock_adjustment_approval — approval gate for large adjustments
--   * StockTransfer PENDING_APPROVAL + IN_TRANSIT states via lifecycle columns
--   * shop.adjustment_approval_threshold — value above which adjustments need
--     an OWNER approval before commit
--
-- INFORMATION_SCHEMA-guarded per the MySQL migration pitfalls memo.

DELIMITER $$

DROP PROCEDURE IF EXISTS add_col_if_missing_v94 $$
CREATE PROCEDURE add_col_if_missing_v94(IN tbl VARCHAR(64), IN col VARCHAR(64), IN col_def VARCHAR(255))
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

-- Adjustment approval threshold — every ADJUST above this delta value needs
-- an OWNER approval. 0 or NULL means no approval required (default).
CALL add_col_if_missing_v94('shop', 'adjustment_approval_threshold', 'DECIMAL(14,2) NULL DEFAULT NULL');

-- Stock transfer approval + in-transit lifecycle fields.
CALL add_col_if_missing_v94('stock_transfer', 'approved_by',      'BIGINT NULL DEFAULT NULL');
CALL add_col_if_missing_v94('stock_transfer', 'approved_at',      'TIMESTAMP NULL DEFAULT NULL');
CALL add_col_if_missing_v94('stock_transfer', 'in_transit_at',    'TIMESTAMP NULL DEFAULT NULL');
CALL add_col_if_missing_v94('stock_transfer', 'received_at',      'TIMESTAMP NULL DEFAULT NULL');
CALL add_col_if_missing_v94('stock_transfer', 'approval_note',    'VARCHAR(500) NULL');

DROP PROCEDURE IF EXISTS add_col_if_missing_v94;

-- Soft-hold on inventory — a reservation reduces sellable-qty without moving
-- stock. When the sale commits, the reservation is consumed; when it expires
-- or cancels, the qty is released back to the sellable pool.
CREATE TABLE IF NOT EXISTS stock_reservation (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    shop_id           BIGINT       NOT NULL,
    item_variant_id   BIGINT       NOT NULL,
    quantity          DECIMAL(12,3) NOT NULL,
    reason            VARCHAR(50)  NOT NULL,
    reference_type    VARCHAR(50)  NULL,
    reference_id      BIGINT       NULL,
    reserved_by       VARCHAR(100) NULL,
    reserved_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at        TIMESTAMP    NULL DEFAULT NULL,
    status            VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    released_at       TIMESTAMP    NULL DEFAULT NULL,
    notes             VARCHAR(500) NULL,

    INDEX idx_sr_shop (shop_id),
    INDEX idx_sr_variant (item_variant_id),
    INDEX idx_sr_status (status)
);

-- Stocktake / cycle count.
CREATE TABLE IF NOT EXISTS cycle_count (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    shop_id           BIGINT       NOT NULL,
    count_number      VARCHAR(50)  NOT NULL,
    scope             VARCHAR(30)  NOT NULL DEFAULT 'FULL',
    status            VARCHAR(20)  NOT NULL DEFAULT 'PLANNED',
    planned_date      DATE         NULL,
    started_at        TIMESTAMP    NULL DEFAULT NULL,
    completed_at      TIMESTAMP    NULL DEFAULT NULL,
    initiated_by      VARCHAR(100) NULL,
    notes             VARCHAR(500) NULL,
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_cc_shop (shop_id),
    INDEX idx_cc_status (status)
);

CREATE TABLE IF NOT EXISTS cycle_count_line (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    cycle_count_id    BIGINT       NOT NULL,
    item_variant_id   BIGINT       NOT NULL,
    batch_number      VARCHAR(100) NULL,
    system_qty        DECIMAL(12,3) NOT NULL DEFAULT 0,
    counted_qty       DECIMAL(12,3) NULL,
    variance_qty      DECIMAL(12,3) NULL,
    reason            VARCHAR(255) NULL,
    counted_by        VARCHAR(100) NULL,
    counted_at        TIMESTAMP    NULL DEFAULT NULL,

    INDEX idx_ccl_count (cycle_count_id),
    INDEX idx_ccl_variant (item_variant_id),
    CONSTRAINT fk_ccl_count FOREIGN KEY (cycle_count_id) REFERENCES cycle_count(id) ON DELETE CASCADE
);

-- Batch recall — trace outbound movements for a batch and lock any remaining
-- stock so it can't be sold.
CREATE TABLE IF NOT EXISTS batch_recall (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    shop_id           BIGINT       NOT NULL,
    batch_number      VARCHAR(100) NOT NULL,
    item_variant_id   BIGINT       NULL,
    reason            VARCHAR(500) NOT NULL,
    status            VARCHAR(20)  NOT NULL DEFAULT 'OPEN',
    initiated_by      VARCHAR(100) NULL,
    initiated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    closed_at         TIMESTAMP    NULL DEFAULT NULL,
    supplier_notified BOOLEAN      NOT NULL DEFAULT FALSE,
    customers_notified BOOLEAN     NOT NULL DEFAULT FALSE,
    notes             VARCHAR(500) NULL,

    INDEX idx_br_shop (shop_id),
    INDEX idx_br_batch (batch_number)
);

CREATE TABLE IF NOT EXISTS batch_recall_impact (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_recall_id   BIGINT       NOT NULL,
    reference_type    VARCHAR(30)  NOT NULL,
    reference_id      BIGINT       NULL,
    reference_number  VARCHAR(100) NULL,
    quantity          DECIMAL(12,3) NULL,
    party_name        VARCHAR(200) NULL,
    party_contact     VARCHAR(200) NULL,
    outcome           VARCHAR(30)  NULL,
    logged_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_bri_recall (batch_recall_id),
    CONSTRAINT fk_bri_recall FOREIGN KEY (batch_recall_id) REFERENCES batch_recall(id) ON DELETE CASCADE
);

-- Approval gate on high-value stock adjustments.
CREATE TABLE IF NOT EXISTS stock_adjustment_approval (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    shop_id           BIGINT       NOT NULL,
    item_variant_id   BIGINT       NOT NULL,
    delta_qty         DECIMAL(12,3) NOT NULL,
    delta_value       DECIMAL(14,2) NOT NULL DEFAULT 0,
    reason            VARCHAR(500) NULL,
    requested_by      VARCHAR(100) NULL,
    requested_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    approved_by       BIGINT       NULL,
    approved_at       TIMESTAMP    NULL DEFAULT NULL,
    status            VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    note              VARCHAR(500) NULL,

    INDEX idx_saa_shop (shop_id),
    INDEX idx_saa_variant (item_variant_id),
    INDEX idx_saa_status (status)
);
