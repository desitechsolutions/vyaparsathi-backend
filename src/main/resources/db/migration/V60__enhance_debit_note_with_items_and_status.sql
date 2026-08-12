-- V60: Turn debit_notes into a fully-formed document, atomically linked to
-- the PurchaseReturn that spawned it.
--
-- Adds:
--   1. debit_note_item — one row per returned line
--   2. debit_note_sequence — per-shop, per-fiscal-year counter
--   3. applied_amount on debit_notes to track consumption against supplier payables
--   4. purchase_return_id FK — atomic linkage to the return that generated the note
--   5. status CHECK constraint enforcing the DebitNoteStatus enum values
--
-- Note: PurchaseReturnItem carries only pre-tax cost (no CGST/SGST/IGST split).
-- DebitNoteItem mirrors that: taxable_value = total_cost, GST amounts default 0.
-- A future migration can add GST breakdown to purchase returns; for now the
-- debit note reflects what the return actually has.

ALTER TABLE debit_notes
    ADD COLUMN applied_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00 AFTER total_amount,
    ADD COLUMN purchase_return_id BIGINT NULL AFTER supplier_id,
    ADD CONSTRAINT fk_debit_note_purchase_return FOREIGN KEY (purchase_return_id) REFERENCES purchase_return(id) ON DELETE SET NULL;

ALTER TABLE debit_notes
    ADD CONSTRAINT chk_debit_note_status
    CHECK (status IN ('ISSUED', 'PARTIALLY_APPLIED', 'FULLY_APPLIED', 'CANCELLED'));

CREATE TABLE debit_note_item (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    debit_note_id         BIGINT       NOT NULL,
    purchase_return_item_id BIGINT     NULL,
    item_name             VARCHAR(255) NOT NULL,
    hsn_sac               VARCHAR(20)  NULL,
    batch_number          VARCHAR(100) NULL,
    qty                   DECIMAL(10,2) NOT NULL,
    unit_cost             DECIMAL(12,2) NOT NULL,
    taxable_value         DECIMAL(12,2) NOT NULL,
    cgst_amt              DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    sgst_amt              DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    igst_amt              DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    total_amount          DECIMAL(12,2) NOT NULL,
    shop_id               BIGINT       NOT NULL,
    created_at            DATETIME     NOT NULL,
    updated_at            DATETIME     NOT NULL,

    CONSTRAINT fk_debit_note_item_note        FOREIGN KEY (debit_note_id)           REFERENCES debit_notes(id)          ON DELETE CASCADE,
    CONSTRAINT fk_debit_note_item_return_item FOREIGN KEY (purchase_return_item_id) REFERENCES purchase_return_item(id) ON DELETE SET NULL,
    CONSTRAINT fk_debit_note_item_shop        FOREIGN KEY (shop_id)                 REFERENCES shop(id),
    INDEX idx_debit_note_item_note (debit_note_id)
);

CREATE TABLE debit_note_sequence (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    shop_id      BIGINT      NOT NULL,
    prefix       VARCHAR(100) NOT NULL,
    fiscal_year  SMALLINT    NOT NULL,
    last_seq     BIGINT      NOT NULL DEFAULT 0,
    created_at   DATETIME    NOT NULL,
    updated_at   DATETIME    NOT NULL,

    CONSTRAINT uk_debit_note_seq_shop_prefix_year UNIQUE (shop_id, prefix, fiscal_year)
);
