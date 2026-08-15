-- Migration V87 (Phase 6.1): per-shop GRN number sequence.
-- Retires the timestamp-based "GR-yyyyMMddHHmmss" numbering which was
-- collision-prone and not human-readable. Sequence table mirrors the
-- purchase_order_sequence table so both docs share a rendering pattern
-- (PO/YY-YY/NNNNN and GRN/YY-YY/NNNNN).

CREATE TABLE IF NOT EXISTS grn_sequence (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    shop_id      BIGINT       NOT NULL,
    prefix       VARCHAR(100) NOT NULL,
    fiscal_year  SMALLINT     NOT NULL,
    last_seq     BIGINT       NOT NULL DEFAULT 0,
    created_at   DATETIME     NOT NULL,
    updated_at   DATETIME     NOT NULL,

    CONSTRAINT uk_grn_seq_shop_prefix_year UNIQUE (shop_id, prefix, fiscal_year)
);