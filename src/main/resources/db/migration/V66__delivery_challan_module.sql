-- V66: Delivery Challan as an independent document.
--
-- Before: a Delivery row tracked *whether* a Sale was dispatched but had no
-- own number and no per-line breakdown — a Sale-with-Delivery was effectively
-- treated as one delivery of the whole invoice.
--
-- After:
--   • Every Delivery gets its own {@code challan_no} (DC/YY-YY/NNNNN series)
--     so it prints as a standalone GST-compliant document (Rule 55).
--   • A new {@code delivery_item} table records which SaleItems and how much
--     of each were dispatched in this shipment. Multiple partial deliveries
--     against one sale are now supported.
--   • {@code delivery_challan_sequence} tracks the per-shop counter.

ALTER TABLE deliveries
    ADD COLUMN challan_no VARCHAR(50) NULL,
    ADD INDEX idx_deliveries_challan_no (challan_no);

CREATE TABLE delivery_item (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    delivery_id           BIGINT NOT NULL,
    sale_item_id          BIGINT NULL,
    item_name             VARCHAR(255) NOT NULL,
    hsn_sac               VARCHAR(20)  NULL,
    unit                  VARCHAR(30)  NULL,
    qty                   DECIMAL(10,2) NOT NULL,
    batch_number          VARCHAR(100) NULL,
    expiry_date           DATE         NULL,
    shop_id               BIGINT       NOT NULL,
    created_at            DATETIME     NOT NULL,
    updated_at            DATETIME     NOT NULL,

    CONSTRAINT fk_delivery_item_delivery  FOREIGN KEY (delivery_id)  REFERENCES deliveries(id) ON DELETE CASCADE,
    CONSTRAINT fk_delivery_item_sale_item FOREIGN KEY (sale_item_id) REFERENCES sale_item(id),
    CONSTRAINT fk_delivery_item_shop      FOREIGN KEY (shop_id)      REFERENCES shop(id),

    INDEX idx_delivery_item_delivery (delivery_id)
);

CREATE TABLE delivery_challan_sequence (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    shop_id      BIGINT      NOT NULL,
    prefix       VARCHAR(100) NOT NULL,
    fiscal_year  SMALLINT    NOT NULL,
    last_seq     BIGINT      NOT NULL DEFAULT 0,
    created_at   DATETIME    NOT NULL,
    updated_at   DATETIME    NOT NULL,

    CONSTRAINT uk_delivery_challan_seq_shop_prefix_year UNIQUE (shop_id, prefix, fiscal_year)
);

-- Existing deliveries: challan_no stays NULL. The service lazy-assigns a
-- number on first PDF request so historical rows get one without a bulk update.
