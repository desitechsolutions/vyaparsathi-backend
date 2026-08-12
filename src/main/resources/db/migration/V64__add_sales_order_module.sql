-- V64: Sales Order module — the confirmed-order stage between quotation and invoice.
--
-- Flow: Quotation (optional) → Sales Order → Sale (invoice)
--   • Sales Order is a customer's confirmed order — before goods leave.
--   • On APPROVE, we reserve stock (create rows in stock_reservation).
--   • On CANCEL, we release the reservation.
--   • On CONVERT-TO-SALE (full or partial), we deduct the fulfilled portion from
--     both reservation and physical stock, and mark the SO PARTIALLY_FULFILLED
--     or FULFILLED accordingly.
--
-- Four tables:
--   sales_order            — header
--   sales_order_item       — lines (includes fulfilled_qty for partial-fulfillment tracking)
--   sales_order_sequence   — per-shop / per-fiscal-year counter
--   stock_reservation      — one row per SO line under active reservation
--
-- IMPORTANT: sales_order_item.item_variant_id uses default RESTRICT (not SET NULL)
-- for the same reason as V63 quotation_item — MySQL 8 (error 3823) forbids a
-- column referenced by both a CHECK and an ON DELETE SET NULL FK.

CREATE TABLE sales_order (
    id                       BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no                 VARCHAR(50)  NOT NULL,
    customer_id              BIGINT       NULL,
    order_date               DATETIME     NOT NULL,
    expected_delivery_date   DATE         NULL,

    -- Header totals (mirror Sale shape for straight conversion)
    total_taxable_amount     DECIMAL(15,2) NOT NULL DEFAULT 0,
    total_cgst               DECIMAL(15,2) NOT NULL DEFAULT 0,
    total_sgst               DECIMAL(15,2) NOT NULL DEFAULT 0,
    total_igst               DECIMAL(15,2) NOT NULL DEFAULT 0,
    invoice_discount         DECIMAL(15,2) NOT NULL DEFAULT 0,
    shipping_charges         DECIMAL(15,2) NOT NULL DEFAULT 0,
    other_charges            DECIMAL(15,2) NOT NULL DEFAULT 0,
    total_amount             DECIMAL(15,2) NOT NULL DEFAULT 0,

    is_gst_required          BOOLEAN      NOT NULL DEFAULT TRUE,

    notes                    VARCHAR(500) NULL,
    terms                    TEXT         NULL,

    status                   VARCHAR(24)  NOT NULL DEFAULT 'DRAFT',

    -- Optional link to the quotation that originated this order
    quotation_id             BIGINT       NULL,

    shop_id                  BIGINT       NOT NULL,
    created_at               DATETIME     NOT NULL,
    updated_at               DATETIME     NOT NULL,

    CONSTRAINT uk_sales_order_shop_number UNIQUE (shop_id, order_no),
    CONSTRAINT fk_sales_order_customer   FOREIGN KEY (customer_id)  REFERENCES customer(id)  ON DELETE SET NULL,
    CONSTRAINT fk_sales_order_shop       FOREIGN KEY (shop_id)      REFERENCES shop(id),
    CONSTRAINT fk_sales_order_quotation  FOREIGN KEY (quotation_id) REFERENCES quotation(id) ON DELETE SET NULL,
    CONSTRAINT chk_sales_order_status CHECK (status IN ('DRAFT','APPROVED','PARTIALLY_FULFILLED','FULFILLED','CANCELLED')),

    INDEX idx_sales_order_shop_status (shop_id, status),
    INDEX idx_sales_order_customer    (customer_id, shop_id),
    INDEX idx_sales_order_date        (shop_id, order_date)
);

CREATE TABLE sales_order_item (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    sales_order_id     BIGINT       NOT NULL,
    item_variant_id    BIGINT       NULL,

    -- Free-text (service / one-off) fields
    custom_item_name   VARCHAR(255) NULL,
    custom_description VARCHAR(500) NULL,
    custom_hsn_sac     VARCHAR(20)  NULL,
    custom_unit        VARCHAR(30)  NULL,

    -- Snapshot line data
    item_name          VARCHAR(255) NOT NULL,
    hsn_sac            VARCHAR(20)  NULL,
    unit               VARCHAR(30)  NULL,
    qty                DECIMAL(10,2) NOT NULL,
    fulfilled_qty      DECIMAL(10,2) NOT NULL DEFAULT 0,
    unit_price         DECIMAL(12,2) NOT NULL,
    discount           DECIMAL(12,2) NOT NULL DEFAULT 0,

    gst_type           VARCHAR(20)  NULL,
    gst_rate           INT          NOT NULL DEFAULT 0,
    taxable_value      DECIMAL(12,2) NOT NULL,
    cgst_amt           DECIMAL(12,2) NOT NULL DEFAULT 0,
    sgst_amt           DECIMAL(12,2) NOT NULL DEFAULT 0,
    igst_amt           DECIMAL(12,2) NOT NULL DEFAULT 0,
    line_total         DECIMAL(12,2) NOT NULL,

    shop_id            BIGINT       NOT NULL,
    created_at         DATETIME     NOT NULL,
    updated_at         DATETIME     NOT NULL,

    CONSTRAINT fk_sales_order_item_order   FOREIGN KEY (sales_order_id) REFERENCES sales_order(id) ON DELETE CASCADE,
    CONSTRAINT fk_sales_order_item_variant FOREIGN KEY (item_variant_id) REFERENCES item_variant(id),
    CONSTRAINT fk_sales_order_item_shop    FOREIGN KEY (shop_id)         REFERENCES shop(id),
    CONSTRAINT chk_sales_order_item_ref CHECK (item_variant_id IS NOT NULL OR custom_item_name IS NOT NULL),

    INDEX idx_sales_order_item_order (sales_order_id)
);

CREATE TABLE sales_order_sequence (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    shop_id      BIGINT      NOT NULL,
    prefix       VARCHAR(100) NOT NULL,
    fiscal_year  SMALLINT    NOT NULL,
    last_seq     BIGINT      NOT NULL DEFAULT 0,
    created_at   DATETIME    NOT NULL,
    updated_at   DATETIME    NOT NULL,

    CONSTRAINT uk_sales_order_seq_shop_prefix_year UNIQUE (shop_id, prefix, fiscal_year)
);

-- Stock reservations: one row per SO line under active reservation. A reservation
-- decrements the item's "available" stock without touching physical stock — so
-- draft/pending SOs can safely coexist with walk-in sales.
CREATE TABLE stock_reservation (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    item_variant_id       BIGINT       NOT NULL,
    sales_order_id        BIGINT       NOT NULL,
    sales_order_item_id   BIGINT       NULL,
    reserved_qty          DECIMAL(10,2) NOT NULL,
    shop_id               BIGINT       NOT NULL,
    created_at            DATETIME     NOT NULL,
    updated_at            DATETIME     NOT NULL,

    CONSTRAINT fk_stock_reservation_variant   FOREIGN KEY (item_variant_id)     REFERENCES item_variant(id),
    CONSTRAINT fk_stock_reservation_order     FOREIGN KEY (sales_order_id)      REFERENCES sales_order(id) ON DELETE CASCADE,
    CONSTRAINT fk_stock_reservation_item      FOREIGN KEY (sales_order_item_id) REFERENCES sales_order_item(id) ON DELETE CASCADE,
    CONSTRAINT fk_stock_reservation_shop      FOREIGN KEY (shop_id)             REFERENCES shop(id),

    INDEX idx_stock_reservation_variant (item_variant_id, shop_id),
    INDEX idx_stock_reservation_order   (sales_order_id)
);
