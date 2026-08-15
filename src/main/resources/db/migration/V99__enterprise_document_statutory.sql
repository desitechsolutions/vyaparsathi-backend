-- =====================================================================
-- V99 — Enterprise Document Statutory Backfill
-- =====================================================================
-- Adds the P0 statutory columns identified in the Principal Architect
-- audit across every doc-bearing entity:
--
--   * Shop        : legal_name, trade_name, pan, cin, signatory_name,
--                   signatory_designation, invoice_footer_terms
--   * Supplier    : legal_name, trade_name, pan, state
--   * Customer    : (already has state/stateCode/pan)
--   * Sale        : place_of_supply_state, place_of_supply_state_code,
--                   supply_type, reverse_charge, amount_in_words,
--                   document_hash, irn, ack_number, ack_date, qr_payload,
--                   ewb_number, ewb_generated_at, ewb_valid_till,
--                   print_count, printed_at, printed_by, revision_number
--   * PurchaseInvoice, DebitNote, CreditNote, PurchaseOrder, Receiving,
--     PurchaseReturn, DeliveryChallan : same statutory columns
--
-- All ALTERs are wrapped in an idempotent INFORMATION_SCHEMA-guarded
-- stored procedure (MySQL has no "IF NOT EXISTS" on ADD COLUMN) so
-- re-running V99 after a partial-fail is safe.
-- =====================================================================

DROP PROCEDURE IF EXISTS v99_add_col;

DELIMITER $$
CREATE PROCEDURE v99_add_col(
    IN p_table   VARCHAR(64),
    IN p_column  VARCHAR(64),
    IN p_ddl     VARCHAR(1024)
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME   = p_table
          AND COLUMN_NAME  = p_column
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN ', p_ddl);
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

-- ─────────────────────────────────────────────────────────────────────
-- SHOP — issuer identity fields
-- ─────────────────────────────────────────────────────────────────────
CALL v99_add_col('shop', 'legal_name',            '`legal_name` VARCHAR(255) NULL');
CALL v99_add_col('shop', 'trade_name',            '`trade_name` VARCHAR(255) NULL');
CALL v99_add_col('shop', 'pan',                   '`pan` VARCHAR(10) NULL');
CALL v99_add_col('shop', 'cin',                   '`cin` VARCHAR(21) NULL');
CALL v99_add_col('shop', 'signatory_name',        '`signatory_name` VARCHAR(120) NULL');
CALL v99_add_col('shop', 'signatory_designation', '`signatory_designation` VARCHAR(80) NULL');
CALL v99_add_col('shop', 'address_line2',         '`address_line2` VARCHAR(255) NULL');
CALL v99_add_col('shop', 'city',                  '`city` VARCHAR(120) NULL');
CALL v99_add_col('shop', 'pincode',               '`pincode` VARCHAR(10) NULL');
CALL v99_add_col('shop', 'country',               '`country` VARCHAR(80) NULL DEFAULT ''IN''');
CALL v99_add_col('shop', 'e_invoicing_enabled',   '`e_invoicing_enabled` TINYINT(1) NOT NULL DEFAULT 0');
CALL v99_add_col('shop', 'e_way_bill_enabled',    '`e_way_bill_enabled` TINYINT(1) NOT NULL DEFAULT 0');
CALL v99_add_col('shop', 'digital_signing_enabled', '`digital_signing_enabled` TINYINT(1) NOT NULL DEFAULT 0');

-- Backfill legal_name from name if empty (safe default until user edits)
UPDATE `shop` SET `legal_name` = `name` WHERE `legal_name` IS NULL;

-- ─────────────────────────────────────────────────────────────────────
-- SUPPLIER — counterparty identity fields
-- ─────────────────────────────────────────────────────────────────────
CALL v99_add_col('supplier', 'legal_name', '`legal_name` VARCHAR(255) NULL');
CALL v99_add_col('supplier', 'trade_name', '`trade_name` VARCHAR(255) NULL');
CALL v99_add_col('supplier', 'pan',        '`pan` VARCHAR(10) NULL');
CALL v99_add_col('supplier', 'state',      '`state` VARCHAR(120) NULL');
CALL v99_add_col('supplier', 'city',       '`city` VARCHAR(120) NULL');
CALL v99_add_col('supplier', 'pincode',    '`pincode` VARCHAR(10) NULL');
CALL v99_add_col('supplier', 'country',    '`country` VARCHAR(80) NULL DEFAULT ''IN''');

UPDATE `supplier` SET `legal_name` = `name` WHERE `legal_name` IS NULL;

-- Backfill state_code from GSTIN prefix where possible
UPDATE `supplier`
   SET `state_code` = LEFT(`gstin`, 2)
 WHERE `gstin` IS NOT NULL AND CHAR_LENGTH(`gstin`) >= 2
   AND (`state_code` IS NULL OR `state_code` = '');

-- ─────────────────────────────────────────────────────────────────────
-- CUSTOMER — statutory backfill
-- ─────────────────────────────────────────────────────────────────────
CALL v99_add_col('customer', 'legal_name', '`legal_name` VARCHAR(255) NULL');

UPDATE `customer` SET `legal_name` = `name` WHERE `legal_name` IS NULL;
UPDATE `customer`
   SET `state_code` = LEFT(`gst_number`, 2)
 WHERE `gst_number` IS NOT NULL AND CHAR_LENGTH(`gst_number`) >= 2
   AND (`state_code` IS NULL OR `state_code` = '');

-- ─────────────────────────────────────────────────────────────────────
-- SALE (Tax Invoice - outward)
-- ─────────────────────────────────────────────────────────────────────
CALL v99_add_col('sale', 'place_of_supply_state',      '`place_of_supply_state` VARCHAR(120) NULL');
CALL v99_add_col('sale', 'place_of_supply_state_code', '`place_of_supply_state_code` VARCHAR(2) NULL');
CALL v99_add_col('sale', 'supply_type',                '`supply_type` VARCHAR(30) NULL');
CALL v99_add_col('sale', 'reverse_charge',             '`reverse_charge` TINYINT(1) NOT NULL DEFAULT 0');
CALL v99_add_col('sale', 'amount_in_words',            '`amount_in_words` VARCHAR(500) NULL');
CALL v99_add_col('sale', 'document_hash',              '`document_hash` VARCHAR(80) NULL');
CALL v99_add_col('sale', 'irn',                        '`irn` VARCHAR(80) NULL');
CALL v99_add_col('sale', 'ack_number',                 '`ack_number` VARCHAR(50) NULL');
CALL v99_add_col('sale', 'ack_date',                   '`ack_date` DATETIME NULL');
CALL v99_add_col('sale', 'qr_payload',                 '`qr_payload` TEXT NULL');
CALL v99_add_col('sale', 'ewb_number',                 '`ewb_number` VARCHAR(30) NULL');
CALL v99_add_col('sale', 'ewb_generated_at',           '`ewb_generated_at` DATETIME NULL');
CALL v99_add_col('sale', 'ewb_valid_till',             '`ewb_valid_till` DATETIME NULL');
CALL v99_add_col('sale', 'transporter_gstin',          '`transporter_gstin` VARCHAR(20) NULL');
CALL v99_add_col('sale', 'transporter_name',           '`transporter_name` VARCHAR(120) NULL');
CALL v99_add_col('sale', 'vehicle_number',             '`vehicle_number` VARCHAR(20) NULL');
CALL v99_add_col('sale', 'lr_number',                  '`lr_number` VARCHAR(40) NULL');
CALL v99_add_col('sale', 'lr_date',                    '`lr_date` DATE NULL');
CALL v99_add_col('sale', 'print_count',                '`print_count` INT NOT NULL DEFAULT 0');
CALL v99_add_col('sale', 'printed_at',                 '`printed_at` DATETIME NULL');
CALL v99_add_col('sale', 'printed_by',                 '`printed_by` BIGINT NULL');
CALL v99_add_col('sale', 'revision_number',            '`revision_number` INT NOT NULL DEFAULT 0');
CALL v99_add_col('sale', 'bill_to_party_snapshot',     '`bill_to_party_snapshot` TEXT NULL');
CALL v99_add_col('sale', 'ship_to_party_snapshot',     '`ship_to_party_snapshot` TEXT NULL');
CALL v99_add_col('sale', 'consignee_party_snapshot',   '`consignee_party_snapshot` TEXT NULL');

-- ─────────────────────────────────────────────────────────────────────
-- PURCHASE_INVOICE (inward)
-- ─────────────────────────────────────────────────────────────────────
CALL v99_add_col('purchase_invoice', 'place_of_supply_state',      '`place_of_supply_state` VARCHAR(120) NULL');
CALL v99_add_col('purchase_invoice', 'place_of_supply_state_code', '`place_of_supply_state_code` VARCHAR(2) NULL');
CALL v99_add_col('purchase_invoice', 'supply_type',                '`supply_type` VARCHAR(30) NULL');
CALL v99_add_col('purchase_invoice', 'reverse_charge',             '`reverse_charge` TINYINT(1) NOT NULL DEFAULT 0');
CALL v99_add_col('purchase_invoice', 'amount_in_words',            '`amount_in_words` VARCHAR(500) NULL');
CALL v99_add_col('purchase_invoice', 'document_hash',              '`document_hash` VARCHAR(80) NULL');
CALL v99_add_col('purchase_invoice', 'print_count',                '`print_count` INT NOT NULL DEFAULT 0');
CALL v99_add_col('purchase_invoice', 'printed_at',                 '`printed_at` DATETIME NULL');
CALL v99_add_col('purchase_invoice', 'printed_by',                 '`printed_by` BIGINT NULL');
CALL v99_add_col('purchase_invoice', 'revision_number',            '`revision_number` INT NOT NULL DEFAULT 0');

-- ─────────────────────────────────────────────────────────────────────
-- DEBIT_NOTES / CREDIT_NOTES — statutory + originating-invoice reference
-- ─────────────────────────────────────────────────────────────────────
CALL v99_add_col('debit_notes', 'place_of_supply_state',      '`place_of_supply_state` VARCHAR(120) NULL');
CALL v99_add_col('debit_notes', 'place_of_supply_state_code', '`place_of_supply_state_code` VARCHAR(2) NULL');
CALL v99_add_col('debit_notes', 'supply_type',                '`supply_type` VARCHAR(30) NULL');
CALL v99_add_col('debit_notes', 'reverse_charge',             '`reverse_charge` TINYINT(1) NOT NULL DEFAULT 0');
CALL v99_add_col('debit_notes', 'amount_in_words',            '`amount_in_words` VARCHAR(500) NULL');
CALL v99_add_col('debit_notes', 'document_hash',              '`document_hash` VARCHAR(80) NULL');
CALL v99_add_col('debit_notes', 'original_invoice_no',        '`original_invoice_no` VARCHAR(50) NULL');
CALL v99_add_col('debit_notes', 'original_invoice_date',      '`original_invoice_date` DATE NULL');
CALL v99_add_col('debit_notes', 'print_count',                '`print_count` INT NOT NULL DEFAULT 0');
CALL v99_add_col('debit_notes', 'printed_at',                 '`printed_at` DATETIME NULL');
CALL v99_add_col('debit_notes', 'printed_by',                 '`printed_by` BIGINT NULL');
CALL v99_add_col('debit_notes', 'revision_number',            '`revision_number` INT NOT NULL DEFAULT 0');

CALL v99_add_col('credit_notes', 'place_of_supply_state',      '`place_of_supply_state` VARCHAR(120) NULL');
CALL v99_add_col('credit_notes', 'place_of_supply_state_code', '`place_of_supply_state_code` VARCHAR(2) NULL');
CALL v99_add_col('credit_notes', 'supply_type',                '`supply_type` VARCHAR(30) NULL');
CALL v99_add_col('credit_notes', 'reverse_charge',             '`reverse_charge` TINYINT(1) NOT NULL DEFAULT 0');
CALL v99_add_col('credit_notes', 'amount_in_words',            '`amount_in_words` VARCHAR(500) NULL');
CALL v99_add_col('credit_notes', 'document_hash',              '`document_hash` VARCHAR(80) NULL');
CALL v99_add_col('credit_notes', 'original_invoice_no',        '`original_invoice_no` VARCHAR(50) NULL');
CALL v99_add_col('credit_notes', 'original_invoice_date',      '`original_invoice_date` DATE NULL');
CALL v99_add_col('credit_notes', 'print_count',                '`print_count` INT NOT NULL DEFAULT 0');
CALL v99_add_col('credit_notes', 'printed_at',                 '`printed_at` DATETIME NULL');
CALL v99_add_col('credit_notes', 'printed_by',                 '`printed_by` BIGINT NULL');
CALL v99_add_col('credit_notes', 'revision_number',            '`revision_number` INT NOT NULL DEFAULT 0');
CALL v99_add_col('credit_notes', 'irn',                        '`irn` VARCHAR(80) NULL');
CALL v99_add_col('credit_notes', 'ack_number',                 '`ack_number` VARCHAR(50) NULL');
CALL v99_add_col('credit_notes', 'ack_date',                   '`ack_date` DATETIME NULL');
CALL v99_add_col('credit_notes', 'qr_payload',                 '`qr_payload` TEXT NULL');

-- ─────────────────────────────────────────────────────────────────────
-- PURCHASE_ORDER — statutory + audit
-- ─────────────────────────────────────────────────────────────────────
CALL v99_add_col('purchase_order', 'place_of_supply_state',      '`place_of_supply_state` VARCHAR(120) NULL');
CALL v99_add_col('purchase_order', 'place_of_supply_state_code', '`place_of_supply_state_code` VARCHAR(2) NULL');
CALL v99_add_col('purchase_order', 'supply_type',                '`supply_type` VARCHAR(30) NULL');
CALL v99_add_col('purchase_order', 'reverse_charge',             '`reverse_charge` TINYINT(1) NOT NULL DEFAULT 0');
CALL v99_add_col('purchase_order', 'amount_in_words',            '`amount_in_words` VARCHAR(500) NULL');
CALL v99_add_col('purchase_order', 'document_hash',              '`document_hash` VARCHAR(80) NULL');
CALL v99_add_col('purchase_order', 'print_count',                '`print_count` INT NOT NULL DEFAULT 0');
CALL v99_add_col('purchase_order', 'printed_at',                 '`printed_at` DATETIME NULL');
CALL v99_add_col('purchase_order', 'printed_by',                 '`printed_by` BIGINT NULL');
CALL v99_add_col('purchase_order', 'revision_number',            '`revision_number` INT NOT NULL DEFAULT 0');
CALL v99_add_col('purchase_order', 'ship_to_party_snapshot',     '`ship_to_party_snapshot` TEXT NULL');
CALL v99_add_col('purchase_order', 'bill_to_party_snapshot',     '`bill_to_party_snapshot` TEXT NULL');

-- ─────────────────────────────────────────────────────────────────────
-- RECEIVING (GRN)
-- ─────────────────────────────────────────────────────────────────────
CALL v99_add_col('receiving', 'place_of_supply_state',      '`place_of_supply_state` VARCHAR(120) NULL');
CALL v99_add_col('receiving', 'place_of_supply_state_code', '`place_of_supply_state_code` VARCHAR(2) NULL');
CALL v99_add_col('receiving', 'supply_type',                '`supply_type` VARCHAR(30) NULL');
CALL v99_add_col('receiving', 'reverse_charge',             '`reverse_charge` TINYINT(1) NOT NULL DEFAULT 0');
CALL v99_add_col('receiving', 'document_hash',              '`document_hash` VARCHAR(80) NULL');
CALL v99_add_col('receiving', 'print_count',                '`print_count` INT NOT NULL DEFAULT 0');
CALL v99_add_col('receiving', 'printed_at',                 '`printed_at` DATETIME NULL');
CALL v99_add_col('receiving', 'printed_by',                 '`printed_by` BIGINT NULL');
CALL v99_add_col('receiving', 'revision_number',            '`revision_number` INT NOT NULL DEFAULT 0');

-- ─────────────────────────────────────────────────────────────────────
-- PURCHASE_RETURN
-- ─────────────────────────────────────────────────────────────────────
CALL v99_add_col('purchase_return', 'place_of_supply_state',      '`place_of_supply_state` VARCHAR(120) NULL');
CALL v99_add_col('purchase_return', 'place_of_supply_state_code', '`place_of_supply_state_code` VARCHAR(2) NULL');
CALL v99_add_col('purchase_return', 'supply_type',                '`supply_type` VARCHAR(30) NULL');
CALL v99_add_col('purchase_return', 'reverse_charge',             '`reverse_charge` TINYINT(1) NOT NULL DEFAULT 0');
CALL v99_add_col('purchase_return', 'amount_in_words',            '`amount_in_words` VARCHAR(500) NULL');
CALL v99_add_col('purchase_return', 'document_hash',              '`document_hash` VARCHAR(80) NULL');
CALL v99_add_col('purchase_return', 'original_invoice_no',        '`original_invoice_no` VARCHAR(50) NULL');
CALL v99_add_col('purchase_return', 'original_invoice_date',      '`original_invoice_date` DATE NULL');
CALL v99_add_col('purchase_return', 'print_count',                '`print_count` INT NOT NULL DEFAULT 0');
CALL v99_add_col('purchase_return', 'printed_at',                 '`printed_at` DATETIME NULL');
CALL v99_add_col('purchase_return', 'printed_by',                 '`printed_by` BIGINT NULL');
CALL v99_add_col('purchase_return', 'revision_number',            '`revision_number` INT NOT NULL DEFAULT 0');

-- ─────────────────────────────────────────────────────────────────────
-- DELIVERY (challan) — if the table exists
-- ─────────────────────────────────────────────────────────────────────
CALL v99_add_col('delivery', 'place_of_supply_state',      '`place_of_supply_state` VARCHAR(120) NULL');
CALL v99_add_col('delivery', 'place_of_supply_state_code', '`place_of_supply_state_code` VARCHAR(2) NULL');
CALL v99_add_col('delivery', 'supply_type',                '`supply_type` VARCHAR(30) NULL');
CALL v99_add_col('delivery', 'ewb_number',                 '`ewb_number` VARCHAR(30) NULL');
CALL v99_add_col('delivery', 'ewb_generated_at',           '`ewb_generated_at` DATETIME NULL');
CALL v99_add_col('delivery', 'ewb_valid_till',             '`ewb_valid_till` DATETIME NULL');
CALL v99_add_col('delivery', 'document_hash',              '`document_hash` VARCHAR(80) NULL');
CALL v99_add_col('delivery', 'print_count',                '`print_count` INT NOT NULL DEFAULT 0');
CALL v99_add_col('delivery', 'printed_at',                 '`printed_at` DATETIME NULL');
CALL v99_add_col('delivery', 'printed_by',                 '`printed_by` BIGINT NULL');

-- ─────────────────────────────────────────────────────────────────────
-- DOCUMENT_REFERENCE — typed cross-reference table (PO → GRN → INV → DN)
-- ─────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `document_reference` (
    `id`             BIGINT NOT NULL AUTO_INCREMENT,
    `shop_id`        BIGINT NOT NULL,
    `source_type`    VARCHAR(30) NOT NULL,
    `source_id`      BIGINT NOT NULL,
    `source_number`  VARCHAR(50) NULL,
    `target_type`    VARCHAR(30) NOT NULL,
    `target_id`      BIGINT NOT NULL,
    `target_number`  VARCHAR(50) NULL,
    `link_kind`      VARCHAR(30) NOT NULL,
    `created_at`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_docref_source` (`source_type`, `source_id`),
    KEY `idx_docref_target` (`target_type`, `target_id`),
    KEY `idx_docref_shop` (`shop_id`)
) ENGINE=InnoDB;

-- ─────────────────────────────────────────────────────────────────────
-- E_INVOICE — IRN capture (opt-in)
-- ─────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `e_invoice` (
    `id`              BIGINT NOT NULL AUTO_INCREMENT,
    `shop_id`         BIGINT NOT NULL,
    `document_type`   VARCHAR(30) NOT NULL,
    `document_id`     BIGINT NOT NULL,
    `document_number` VARCHAR(50) NULL,
    `irn`             VARCHAR(80) NOT NULL,
    `ack_number`      VARCHAR(50) NULL,
    `ack_date`        DATETIME NULL,
    `qr_payload`      TEXT NULL,
    `signed_invoice`  MEDIUMTEXT NULL,
    `signed_qr_code`  TEXT NULL,
    `status`          VARCHAR(20) NOT NULL DEFAULT 'GENERATED',
    `cancellation_reason` VARCHAR(255) NULL,
    `cancelled_at`    DATETIME NULL,
    `raw_response`    MEDIUMTEXT NULL,
    `created_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_einv_irn` (`irn`),
    KEY `idx_einv_doc` (`document_type`, `document_id`),
    KEY `idx_einv_shop` (`shop_id`)
) ENGINE=InnoDB;

-- ─────────────────────────────────────────────────────────────────────
-- E_WAY_BILL — EWB capture (opt-in)
-- ─────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `e_way_bill` (
    `id`              BIGINT NOT NULL AUTO_INCREMENT,
    `shop_id`         BIGINT NOT NULL,
    `document_type`   VARCHAR(30) NOT NULL,
    `document_id`     BIGINT NOT NULL,
    `document_number` VARCHAR(50) NULL,
    `ewb_number`      VARCHAR(30) NOT NULL,
    `generated_at`    DATETIME NULL,
    `valid_till`      DATETIME NULL,
    `distance_km`     INT NULL,
    `transporter_gstin` VARCHAR(20) NULL,
    `transporter_name`  VARCHAR(120) NULL,
    `vehicle_number`  VARCHAR(20) NULL,
    `transport_mode`  VARCHAR(30) NULL,
    `status`          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    `raw_response`    MEDIUMTEXT NULL,
    `created_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_ewb_number` (`ewb_number`),
    KEY `idx_ewb_doc` (`document_type`, `document_id`),
    KEY `idx_ewb_shop` (`shop_id`)
) ENGINE=InnoDB;

-- ─────────────────────────────────────────────────────────────────────
-- DOCUMENT_PRINT_AUDIT — per-print row for compliance monitoring
-- ─────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `document_print_audit` (
    `id`             BIGINT NOT NULL AUTO_INCREMENT,
    `shop_id`        BIGINT NOT NULL,
    `document_type`  VARCHAR(30) NOT NULL,
    `document_id`    BIGINT NOT NULL,
    `document_number` VARCHAR(50) NULL,
    `printed_at`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `printed_by`     BIGINT NULL,
    `printed_by_name` VARCHAR(120) NULL,
    `is_duplicate`   TINYINT(1) NOT NULL DEFAULT 0,
    `document_hash_snapshot` VARCHAR(80) NULL,
    `created_at`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_dpa_doc` (`document_type`, `document_id`),
    KEY `idx_dpa_shop` (`shop_id`),
    KEY `idx_dpa_printed_at` (`printed_at`)
) ENGINE=InnoDB;

DROP PROCEDURE IF EXISTS v99_add_col;
