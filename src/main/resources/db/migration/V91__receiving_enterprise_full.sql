-- Migration V91 (Phase 6.3 — enterprise feature completion):
-- Adds the schema needed for every remaining item in the receiving enterprise
-- feature map:
--   * Pre-receipt: expected_delivery_date, dock_bay, checklist_json,
--     digital_signature_url on receiving
--   * 3-way match: ap_invoice table linking supplier invoice ↔ GRN
--   * Cost variance flag + short-close markers on PO
--   * Multi-level approval workflow (receiving_approval)
--   * Putaway bin/location assignments (receiving_bin_assignment)
--   * Notification audit (receiving_notification_log)
--   * Ticket → debit note link (ticket carries debit_note_id)
--
-- INFORMATION_SCHEMA-guarded per the MySQL migration pitfalls memo.

DELIMITER $$

DROP PROCEDURE IF EXISTS add_col_if_missing_v91 $$
CREATE PROCEDURE add_col_if_missing_v91(IN tbl VARCHAR(64), IN col VARCHAR(64), IN col_def VARCHAR(255))
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

-- Pre-receipt planning fields on receiving.
CALL add_col_if_missing_v91('receiving', 'expected_delivery_date', 'DATE NULL DEFAULT NULL');
CALL add_col_if_missing_v91('receiving', 'dock_bay',               'VARCHAR(50) NULL DEFAULT NULL');
CALL add_col_if_missing_v91('receiving', 'checklist_json',         'TEXT NULL');
CALL add_col_if_missing_v91('receiving', 'digital_signature_url',  'VARCHAR(500) NULL');
CALL add_col_if_missing_v91('receiving', 'cost_variance_flag',     'BOOLEAN NOT NULL DEFAULT FALSE');
CALL add_col_if_missing_v91('receiving', 'auto_ticket_raised',     'BOOLEAN NOT NULL DEFAULT FALSE');

-- Ticket → debit note link so financial recovery is one click.
CALL add_col_if_missing_v91('receiving_ticket', 'debit_note_id', 'BIGINT NULL DEFAULT NULL');

-- PO short-close markers.
CALL add_col_if_missing_v91('purchase_order', 'is_short_closed', 'BOOLEAN NOT NULL DEFAULT FALSE');
CALL add_col_if_missing_v91('purchase_order', 'close_reason',    'VARCHAR(500) NULL DEFAULT NULL');
CALL add_col_if_missing_v91('purchase_order', 'closed_at',       'TIMESTAMP NULL DEFAULT NULL');
CALL add_col_if_missing_v91('purchase_order', 'closed_by',       'BIGINT NULL DEFAULT NULL');

DROP PROCEDURE IF EXISTS add_col_if_missing_v91;

-- AP invoice ledger — one row per supplier invoice, may match multiple GRNs.
CREATE TABLE IF NOT EXISTS ap_invoice (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    shop_id           BIGINT       NOT NULL,
    supplier_id       BIGINT       NOT NULL,
    receiving_id      BIGINT       NULL,
    invoice_no        VARCHAR(100) NOT NULL,
    invoice_date      DATE         NOT NULL,
    due_date          DATE         NULL,
    payment_terms_days INT         NULL DEFAULT 30,
    subtotal          DECIMAL(14,2) NOT NULL DEFAULT 0,
    tax_amount        DECIMAL(14,2) NOT NULL DEFAULT 0,
    total_amount      DECIMAL(14,2) NOT NULL DEFAULT 0,
    match_status      VARCHAR(30)  NOT NULL DEFAULT 'UNMATCHED',
    matched_at        TIMESTAMP    NULL DEFAULT NULL,
    matched_by        BIGINT       NULL DEFAULT NULL,
    variance_amount   DECIMAL(14,2) NOT NULL DEFAULT 0,
    variance_note     VARCHAR(500) NULL,
    notes             VARCHAR(500) NULL,
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_ap_invoice_shop (shop_id),
    INDEX idx_ap_invoice_supplier (supplier_id),
    INDEX idx_ap_invoice_receiving (receiving_id),
    CONSTRAINT uk_ap_invoice_shop_supplier_no UNIQUE (shop_id, supplier_id, invoice_no)
);

-- Multi-level approval workflow — L1 → L2 gating for high-value GRNs.
CREATE TABLE IF NOT EXISTS receiving_approval (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    receiving_id    BIGINT       NOT NULL,
    shop_id         BIGINT       NOT NULL,
    level           SMALLINT     NOT NULL,
    approver_role   VARCHAR(50)  NULL,
    approver_id     BIGINT       NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    threshold_min   DECIMAL(14,2) NULL,
    approved_at     TIMESTAMP    NULL DEFAULT NULL,
    note            VARCHAR(500) NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_rec_appr_receiving (receiving_id),
    INDEX idx_rec_appr_shop (shop_id),
    CONSTRAINT fk_rec_appr_receiving FOREIGN KEY (receiving_id) REFERENCES receiving(id) ON DELETE CASCADE
);

-- Warehouse putaway destinations per line.
CREATE TABLE IF NOT EXISTS receiving_bin_assignment (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    receiving_item_id   BIGINT       NOT NULL,
    shop_id             BIGINT       NOT NULL,
    bin_code            VARCHAR(50)  NOT NULL,
    quantity            INT          NOT NULL DEFAULT 0,
    assigned_by         VARCHAR(100) NULL,
    assigned_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_bin_receiving_item (receiving_item_id),
    INDEX idx_bin_shop (shop_id)
);

-- Notification audit — mirrors delivery module pattern; keeps a delivery-log of every
-- push to give supervisors visibility into what's been sent when.
CREATE TABLE IF NOT EXISTS receiving_notification_log (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    shop_id       BIGINT       NOT NULL,
    receiving_id  BIGINT       NULL,
    ticket_id     BIGINT       NULL,
    event         VARCHAR(50)  NOT NULL,
    channel       VARCHAR(20)  NOT NULL,
    recipient     VARCHAR(200) NULL,
    subject       VARCHAR(200) NULL,
    body          TEXT         NULL,
    sent_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status        VARCHAR(20)  NOT NULL DEFAULT 'SENT',
    error_note    VARCHAR(500) NULL,

    INDEX idx_recn_shop (shop_id),
    INDEX idx_recn_receiving (receiving_id),
    INDEX idx_recn_ticket (ticket_id)
);
