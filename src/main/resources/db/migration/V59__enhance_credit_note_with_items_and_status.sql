-- V59: Turn credit_notes into a fully-formed document.
--
-- Adds:
--   1. credit_note_item — one row per returned/credited line
--   2. credit_note_sequence — per-shop, per-fiscal-year counter (mirrors invoice_sequence)
--   3. applied_amount on credit_notes to track how much of a credit note has
--      been consumed (applied against a future invoice, refunded, etc.)
--   4. status CHECK constraint enforcing the CreditNoteStatus enum values
--
-- Backwards-compat: existing credit_notes rows keep their current
-- credit_note_no strings (issued as "CN/YYMM/xxxxx" from System.currentTimeMillis
-- prior to this migration). New notes issued after this migration use the
-- format "CR/YY-YY/NNNNN" produced by CreditNoteNumberService.

ALTER TABLE credit_notes
    ADD COLUMN applied_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00 AFTER total_amount;

ALTER TABLE credit_notes
    ADD CONSTRAINT chk_credit_note_status
    CHECK (status IN ('ISSUED', 'PARTIALLY_APPLIED', 'FULLY_APPLIED', 'CANCELLED'));

CREATE TABLE credit_note_item (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    credit_note_id    BIGINT       NOT NULL,
    sale_item_id      BIGINT       NULL,
    item_name         VARCHAR(255) NOT NULL,
    hsn_sac           VARCHAR(20)  NULL,
    unit              VARCHAR(30)  NULL,
    qty               DECIMAL(10,2) NOT NULL,
    unit_price        DECIMAL(12,2) NOT NULL,
    discount          DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    taxable_value     DECIMAL(12,2) NOT NULL,
    cgst_amt          DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    sgst_amt          DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    igst_amt          DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    total_amount      DECIMAL(12,2) NOT NULL,
    shop_id           BIGINT       NOT NULL,
    created_at        DATETIME     NOT NULL,
    updated_at        DATETIME     NOT NULL,

    CONSTRAINT fk_credit_note_item_note      FOREIGN KEY (credit_note_id) REFERENCES credit_notes(id) ON DELETE CASCADE,
    CONSTRAINT fk_credit_note_item_sale_item FOREIGN KEY (sale_item_id)   REFERENCES sale_item(id)    ON DELETE SET NULL,
    CONSTRAINT fk_credit_note_item_shop      FOREIGN KEY (shop_id)        REFERENCES shop(id),
    INDEX idx_credit_note_item_note (credit_note_id)
);

CREATE TABLE credit_note_sequence (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    shop_id      BIGINT      NOT NULL,
    prefix       VARCHAR(100) NOT NULL,
    fiscal_year  SMALLINT    NOT NULL,
    last_seq     BIGINT      NOT NULL DEFAULT 0,
    created_at   DATETIME    NOT NULL,
    updated_at   DATETIME    NOT NULL,

    CONSTRAINT uk_credit_note_seq_shop_prefix_year UNIQUE (shop_id, prefix, fiscal_year)
);
