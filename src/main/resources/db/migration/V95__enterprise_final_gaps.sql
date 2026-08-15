-- Migration V95 — closes every remaining P2/P3 gap from the three audits:
--   * Product bundles / kits (BOM)
--   * UOM conversion table
--   * Supplier rate card per variant
--   * Alert snooze + saved views (server-side)
--   * QC sample records (AQL) + temperature/condition log
--   * PO multi-currency (currency code + exchange rate) + recurring PO schedule
--   * PO field-level audit log
--
-- INFORMATION_SCHEMA-guarded per the MySQL migration pitfalls memo.

DELIMITER $$

DROP PROCEDURE IF EXISTS add_col_if_missing_v95 $$
CREATE PROCEDURE add_col_if_missing_v95(IN tbl VARCHAR(64), IN col VARCHAR(64), IN col_def VARCHAR(255))
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

-- PO multi-currency support.
CALL add_col_if_missing_v95('purchase_order', 'currency_code',    'VARCHAR(3) NOT NULL DEFAULT ''INR''');
CALL add_col_if_missing_v95('purchase_order', 'exchange_rate',    'DECIMAL(14,6) NOT NULL DEFAULT 1.0');
CALL add_col_if_missing_v95('purchase_order', 'base_total_amount', 'DECIMAL(14,2) NULL DEFAULT NULL');

-- PO recurrence schedule (cron-driven auto-generation).
CALL add_col_if_missing_v95('purchase_order', 'recurring_enabled',      'BOOLEAN NOT NULL DEFAULT FALSE');
CALL add_col_if_missing_v95('purchase_order', 'recurring_frequency',    'VARCHAR(20) NULL DEFAULT NULL');
CALL add_col_if_missing_v95('purchase_order', 'recurring_next_at',      'TIMESTAMP NULL DEFAULT NULL');
CALL add_col_if_missing_v95('purchase_order', 'recurring_parent_id',    'BIGINT NULL DEFAULT NULL');

-- Item variant enhancements: UOM primary tag + barcode print flag.
CALL add_col_if_missing_v95('item_variant', 'primary_uom',            'VARCHAR(20) NULL DEFAULT NULL');
CALL add_col_if_missing_v95('item_variant', 'barcode_label_template', 'VARCHAR(50) NULL DEFAULT NULL');

DROP PROCEDURE IF EXISTS add_col_if_missing_v95;

-- Product bundle / kit — a parent item variant composed of N child variants.
CREATE TABLE IF NOT EXISTS product_bundle (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    shop_id           BIGINT       NOT NULL,
    bundle_variant_id BIGINT       NOT NULL,
    bundle_name       VARCHAR(200) NOT NULL,
    active            BOOLEAN      NOT NULL DEFAULT TRUE,
    notes             VARCHAR(500) NULL,
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_pb_shop (shop_id),
    INDEX idx_pb_bundle_variant (bundle_variant_id)
);

CREATE TABLE IF NOT EXISTS product_bundle_component (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_bundle_id     BIGINT       NOT NULL,
    component_variant_id  BIGINT       NOT NULL,
    quantity              DECIMAL(12,3) NOT NULL,
    unit                  VARCHAR(20)  NULL,
    optional_flag         BOOLEAN      NOT NULL DEFAULT FALSE,

    INDEX idx_pbc_bundle (product_bundle_id),
    CONSTRAINT fk_pbc_bundle FOREIGN KEY (product_bundle_id) REFERENCES product_bundle(id) ON DELETE CASCADE
);

-- Unit-of-measure conversion. from_unit → to_unit × factor.
CREATE TABLE IF NOT EXISTS uom_conversion (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    shop_id      BIGINT       NULL,
    from_unit    VARCHAR(20)  NOT NULL,
    to_unit      VARCHAR(20)  NOT NULL,
    factor       DECIMAL(14,6) NOT NULL,
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_uom_shop (shop_id),
    CONSTRAINT uk_uom_shop_pair UNIQUE (shop_id, from_unit, to_unit)
);

-- Supplier rate card per variant.
CREATE TABLE IF NOT EXISTS supplier_rate_card (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    shop_id           BIGINT       NOT NULL,
    supplier_id       BIGINT       NOT NULL,
    item_variant_id   BIGINT       NOT NULL,
    unit_cost         DECIMAL(12,4) NOT NULL,
    min_order_qty     DECIMAL(12,3) NULL,
    lead_time_days    INT          NULL,
    valid_from        DATE         NOT NULL,
    valid_to          DATE         NULL,
    currency_code     VARCHAR(3)   NOT NULL DEFAULT 'INR',
    notes             VARCHAR(500) NULL,
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_src_shop (shop_id),
    INDEX idx_src_supplier (supplier_id),
    INDEX idx_src_variant (item_variant_id)
);

-- Server-side alert snooze (per user).
CREATE TABLE IF NOT EXISTS alert_snooze (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    shop_id       BIGINT       NOT NULL,
    user_id       BIGINT       NOT NULL,
    alert_type    VARCHAR(50)  NOT NULL,
    alert_key     VARCHAR(200) NOT NULL,
    snoozed_until TIMESTAMP    NOT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_snooze_shop (shop_id),
    INDEX idx_snooze_user (user_id),
    CONSTRAINT uk_snooze_user_key UNIQUE (user_id, alert_type, alert_key)
);

-- Named filter presets ("Saved views").
CREATE TABLE IF NOT EXISTS saved_view (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    shop_id       BIGINT       NOT NULL,
    user_id       BIGINT       NOT NULL,
    surface       VARCHAR(50)  NOT NULL,
    name          VARCHAR(100) NOT NULL,
    payload_json  TEXT         NOT NULL,
    is_default    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_view_user_surface (user_id, surface)
);

-- QC sample / AQL record — one row per sampled batch.
CREATE TABLE IF NOT EXISTS qc_sample (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    shop_id           BIGINT       NOT NULL,
    receiving_id      BIGINT       NULL,
    receiving_item_id BIGINT       NULL,
    item_variant_id   BIGINT       NOT NULL,
    batch_number      VARCHAR(100) NULL,
    sample_size       INT          NOT NULL,
    defects_found     INT          NOT NULL DEFAULT 0,
    aql_pct           DECIMAL(6,3) NULL,
    verdict           VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    inspected_by      VARCHAR(100) NULL,
    inspected_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    notes             VARCHAR(500) NULL,

    INDEX idx_qc_shop (shop_id),
    INDEX idx_qc_receiving (receiving_id),
    INDEX idx_qc_variant (item_variant_id)
);

-- Temperature / condition log — cold-chain receipts.
CREATE TABLE IF NOT EXISTS temperature_log (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    shop_id       BIGINT       NOT NULL,
    receiving_id  BIGINT       NULL,
    reading_at    TIMESTAMP    NOT NULL,
    temperature_c DECIMAL(6,2) NOT NULL,
    humidity_pct  DECIMAL(5,2) NULL,
    location      VARCHAR(200) NULL,
    within_spec   BOOLEAN      NOT NULL DEFAULT TRUE,
    logged_by     VARCHAR(100) NULL,
    notes         VARCHAR(500) NULL,

    INDEX idx_temp_shop (shop_id),
    INDEX idx_temp_receiving (receiving_id)
);

-- PO field-level audit log.
CREATE TABLE IF NOT EXISTS po_field_audit (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    shop_id           BIGINT       NOT NULL,
    purchase_order_id BIGINT       NOT NULL,
    field_name        VARCHAR(80)  NOT NULL,
    old_value         VARCHAR(500) NULL,
    new_value         VARCHAR(500) NULL,
    changed_by        VARCHAR(100) NULL,
    changed_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_pfa_po (purchase_order_id),
    INDEX idx_pfa_shop (shop_id)
);
