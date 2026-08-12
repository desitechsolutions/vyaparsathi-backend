-- V63: Quotation module — the missing first stage of the quote-to-cash flow.
--
-- A quotation is a non-binding price offer sent to a customer. It has its own
-- number series (QT/YY-YY/NNNNN), a lifecycle (DRAFT → SENT → ACCEPTED/REJECTED
-- → EXPIRED, or → CONVERTED once turned into a Sale), and an expiry date. Line
-- items mirror the sale_item shape (including custom / free-text lines from V57)
-- so converting a quotation to a sale is a straight copy — no re-derivation of
-- taxes needed.
--
-- Three tables:
--   quotation           — header
--   quotation_item      — lines
--   quotation_sequence  — per-shop, per-fiscal-year counter (same pattern as
--                          invoice_sequence)
--
-- IMPORTANT: quotation_item.item_variant_id uses the default RESTRICT delete
-- action (NOT `ON DELETE SET NULL`). MySQL 8 (error 3823) forbids a column from
-- being referenced by both a CHECK constraint and a FK with `SET NULL`, because
-- the SET-NULL action could silently violate the CHECK. RESTRICT is also the
-- correct integrity semantic — an ItemVariant with quotation history should not
-- be quietly nullified.

CREATE TABLE quotation (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    quotation_no          VARCHAR(50)  NOT NULL,
    customer_id           BIGINT       NULL,
    quotation_date        DATETIME     NOT NULL,
    expiry_date           DATE         NULL,

    -- Header aggregates (mirror the Sale entity's totals so conversion is trivial)
    total_taxable_amount  DECIMAL(15,2) NOT NULL DEFAULT 0,
    total_cgst            DECIMAL(15,2) NOT NULL DEFAULT 0,
    total_sgst            DECIMAL(15,2) NOT NULL DEFAULT 0,
    total_igst            DECIMAL(15,2) NOT NULL DEFAULT 0,
    invoice_discount      DECIMAL(15,2) NOT NULL DEFAULT 0,
    shipping_charges      DECIMAL(15,2) NOT NULL DEFAULT 0,
    other_charges         DECIMAL(15,2) NOT NULL DEFAULT 0,
    total_amount          DECIMAL(15,2) NOT NULL DEFAULT 0,

    is_gst_required       BOOLEAN      NOT NULL DEFAULT TRUE,

    notes                 VARCHAR(500) NULL,
    terms                 TEXT         NULL,

    status                VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    converted_to_sale_id  BIGINT       NULL,

    shop_id               BIGINT       NOT NULL,
    created_at            DATETIME     NOT NULL,
    updated_at            DATETIME     NOT NULL,

    CONSTRAINT uk_quotation_shop_number UNIQUE (shop_id, quotation_no),
    CONSTRAINT fk_quotation_customer         FOREIGN KEY (customer_id)          REFERENCES customer(id) ON DELETE SET NULL,
    CONSTRAINT fk_quotation_shop             FOREIGN KEY (shop_id)              REFERENCES shop(id),
    CONSTRAINT fk_quotation_converted_sale   FOREIGN KEY (converted_to_sale_id) REFERENCES sale(id)     ON DELETE SET NULL,
    CONSTRAINT chk_quotation_status CHECK (status IN ('DRAFT','SENT','ACCEPTED','REJECTED','EXPIRED','CANCELLED','CONVERTED')),

    INDEX idx_quotation_shop_status (shop_id, status),
    INDEX idx_quotation_customer    (customer_id, shop_id),
    INDEX idx_quotation_date        (shop_id, quotation_date)
);

CREATE TABLE quotation_item (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    quotation_id       BIGINT       NOT NULL,
    item_variant_id    BIGINT       NULL,

    -- Free-text (service / one-off) fields — populated when item_variant_id is null (see V57)
    custom_item_name   VARCHAR(255) NULL,
    custom_description VARCHAR(500) NULL,
    custom_hsn_sac     VARCHAR(20)  NULL,
    custom_unit        VARCHAR(30)  NULL,

    -- Snapshot line data
    item_name          VARCHAR(255) NOT NULL,
    hsn_sac            VARCHAR(20)  NULL,
    unit               VARCHAR(30)  NULL,
    qty                DECIMAL(10,2) NOT NULL,
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

    -- item_variant_id FK uses default RESTRICT (NOT SET NULL) so it is
    -- compatible with the CHECK below. See header comment.
    CONSTRAINT fk_quotation_item_quotation FOREIGN KEY (quotation_id)    REFERENCES quotation(id)     ON DELETE CASCADE,
    CONSTRAINT fk_quotation_item_variant   FOREIGN KEY (item_variant_id) REFERENCES item_variant(id),
    CONSTRAINT fk_quotation_item_shop      FOREIGN KEY (shop_id)         REFERENCES shop(id),
    CONSTRAINT chk_quotation_item_ref CHECK (item_variant_id IS NOT NULL OR custom_item_name IS NOT NULL),

    INDEX idx_quotation_item_quotation (quotation_id)
);

CREATE TABLE quotation_sequence (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    shop_id      BIGINT      NOT NULL,
    prefix       VARCHAR(100) NOT NULL,
    fiscal_year  SMALLINT    NOT NULL,
    last_seq     BIGINT      NOT NULL DEFAULT 0,
    created_at   DATETIME    NOT NULL,
    updated_at   DATETIME    NOT NULL,

    CONSTRAINT uk_quotation_seq_shop_prefix_year UNIQUE (shop_id, prefix, fiscal_year)
);
