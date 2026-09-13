CREATE TABLE gstr2b_imports (
    id                      BIGINT       NOT NULL AUTO_INCREMENT,
    shop_id                 BIGINT       NOT NULL,
    return_period           VARCHAR(7)   NOT NULL,
    file_name               VARCHAR(500),
    imported_at             DATETIME(6)  NOT NULL,
    total_invoices          INT          NOT NULL DEFAULT 0,
    matched_count           INT          NOT NULL DEFAULT 0,
    mismatched_count        INT          NOT NULL DEFAULT 0,
    missing_in_books_count  INT          NOT NULL DEFAULT 0,
    missing_in_portal_count INT          NOT NULL DEFAULT 0,
    created_at              DATETIME(6)  NOT NULL,
    updated_at              DATETIME(6),
    PRIMARY KEY (id),
    KEY idx_gstr2b_import_shop_period (shop_id, return_period)
);

CREATE TABLE gstr2b_entries (
    id                   BIGINT        NOT NULL AUTO_INCREMENT,
    import_id            BIGINT        NOT NULL,
    shop_id              BIGINT        NOT NULL,
    supplier_gstin       VARCHAR(15),
    supplier_name        VARCHAR(255),
    invoice_number       VARCHAR(100),
    invoice_type         VARCHAR(20),
    invoice_date         DATE,
    invoice_value        DECIMAL(14,2),
    taxable_value        DECIMAL(14,2),
    igst_amount          DECIMAL(12,2),
    cgst_amount          DECIMAL(12,2),
    sgst_amount          DECIMAL(12,2),
    cess_amount          DECIMAL(12,2),
    itc_availability     VARCHAR(1),
    match_status         VARCHAR(30)   NOT NULL,
    matched_purchase_id  BIGINT,
    created_at           DATETIME(6)   NOT NULL,
    updated_at           DATETIME(6),
    PRIMARY KEY (id),
    KEY idx_gstr2b_entry_import (import_id),
    KEY idx_gstr2b_entry_shop (shop_id)
);

ALTER TABLE purchase_invoices ADD COLUMN gstr2b_match_status VARCHAR(30) NULL;
