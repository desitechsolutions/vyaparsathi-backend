-- Migration V93 — closes the remaining P0/P1 gaps from the enterprise audit:
--   * Receiving-side freight + landed cost (freight often differs between
--     PO estimate and actual delivery; capture on GRN so inventory valuation
--     reflects reality)
--   * ASN (Advance Shipment Notice) — supplier-provided pre-fill for a GRN

DELIMITER $$

DROP PROCEDURE IF EXISTS add_col_if_missing_v93 $$
CREATE PROCEDURE add_col_if_missing_v93(IN tbl VARCHAR(64), IN col VARCHAR(64), IN col_def VARCHAR(255))
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

-- Receiving-side landed cost: freight actually paid at delivery, plus the
-- opt-in flag so the value is only distributed when the operator wants.
CALL add_col_if_missing_v93('receiving', 'freight_actual',       'DECIMAL(12,2) NULL DEFAULT NULL');
CALL add_col_if_missing_v93('receiving', 'landed_cost_enabled',  'BOOLEAN NOT NULL DEFAULT FALSE');

-- Per-line landed cost snapshot for downstream valuation.
CALL add_col_if_missing_v93('receiving_item', 'landed_unit_cost', 'DECIMAL(12,4) NULL DEFAULT NULL');

DROP PROCEDURE IF EXISTS add_col_if_missing_v93;

-- ASN — supplier-provided pre-shipment declaration. Populated either by an
-- inbound EDI/CSV import or via the supplier portal (future). One row per
-- ASN document; multiple ASNs may attach to a single PO for split shipments.
CREATE TABLE IF NOT EXISTS advance_shipment_notice (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    shop_id              BIGINT       NOT NULL,
    purchase_order_id    BIGINT       NULL,
    supplier_id          BIGINT       NULL,
    asn_number           VARCHAR(100) NOT NULL,
    dispatch_date        DATE         NULL,
    expected_arrival     DATE         NULL,
    carrier              VARCHAR(200) NULL,
    tracking_number      VARCHAR(100) NULL,
    vehicle_no           VARCHAR(50)  NULL,
    total_cartons        INT          NULL,
    total_weight_kg      DECIMAL(12,2) NULL,
    consumed_receiving_id BIGINT      NULL,
    status               VARCHAR(30)  NOT NULL DEFAULT 'PENDING',
    payload_json         TEXT         NULL,
    notes                VARCHAR(500) NULL,
    created_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_asn_shop     (shop_id),
    INDEX idx_asn_po       (purchase_order_id),
    INDEX idx_asn_supplier (supplier_id),
    INDEX idx_asn_number   (asn_number)
);
