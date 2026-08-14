-- Migration V84 (Phase 2 of the Purchase Order enterprise redesign):
-- per-shop PO number sequence so the shop no longer types a PO number
-- manually. Format PO/YY-YY/NNNNN — matches the invoice / quotation naming
-- convention.
--
-- Wrapped in an existence check so re-running the migration is safe.

CREATE TABLE IF NOT EXISTS purchase_order_sequence (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    shop_id      BIGINT       NOT NULL,
    prefix       VARCHAR(100) NOT NULL,
    fiscal_year  SMALLINT     NOT NULL,
    last_seq     BIGINT       NOT NULL DEFAULT 0,
    created_at   DATETIME     NOT NULL,
    updated_at   DATETIME     NOT NULL,

    CONSTRAINT uk_po_seq_shop_prefix_year UNIQUE (shop_id, prefix, fiscal_year)
);