-- Migration V98 — Debit Note application audit log.
--
-- Records every "apply DN X against supplier invoice Y for amount Z" event so
-- the {@code debit_notes.applied_amount} column becomes an auditable running
-- total instead of a floating number. Enables aging + unapplied-outstanding
-- reporting and matches AP subledger conventions.

CREATE TABLE IF NOT EXISTS debit_note_application (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    shop_id               BIGINT       NOT NULL,
    debit_note_id         BIGINT       NOT NULL,
    purchase_invoice_id   BIGINT       NULL,
    supplier_payment_id   BIGINT       NULL,
    applied_amount        DECIMAL(12,2) NOT NULL,
    applied_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    applied_by            VARCHAR(100) NULL,
    note                  VARCHAR(500) NULL,
    reversed              BOOLEAN      NOT NULL DEFAULT FALSE,
    reversed_at           TIMESTAMP    NULL DEFAULT NULL,
    reversed_by           VARCHAR(100) NULL,
    created_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_dna_shop (shop_id),
    INDEX idx_dna_dn (debit_note_id),
    INDEX idx_dna_pi (purchase_invoice_id),
    CONSTRAINT fk_dna_dn FOREIGN KEY (debit_note_id) REFERENCES debit_notes(id) ON DELETE CASCADE
);
