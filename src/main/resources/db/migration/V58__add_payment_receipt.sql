-- V58: Payment Receipt as a first-class document.
--
-- Rationale: every recorded payment (against a sale, a purchase order, or a
-- customer advance) needs a printable proof-of-payment PDF. The receipt is
-- persisted so it can be reprinted with a stable number long after the payment
-- was recorded.
--
-- Two tables:
--   payment_receipt          — one row per issued receipt
--   payment_receipt_sequence — atomic per-shop, per-fiscal-year counter
--                              (same pessimistic-lock pattern as invoice_sequence)

CREATE TABLE payment_receipt (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    receipt_number    VARCHAR(80)  NOT NULL,
    payment_id        BIGINT       NOT NULL,
    customer_id       BIGINT       NULL,
    receipt_date      DATETIME     NOT NULL,
    amount            DECIMAL(15,2) NOT NULL,
    payment_method    VARCHAR(30)  NOT NULL,
    reference         VARCHAR(255) NULL,
    notes             TEXT         NULL,
    shop_id           BIGINT       NOT NULL,
    created_at        DATETIME     NOT NULL,
    updated_at        DATETIME     NOT NULL,

    CONSTRAINT uk_payment_receipt_shop_number UNIQUE (shop_id, receipt_number),
    CONSTRAINT fk_payment_receipt_payment  FOREIGN KEY (payment_id)  REFERENCES payment(id),
    CONSTRAINT fk_payment_receipt_customer FOREIGN KEY (customer_id) REFERENCES customer(id),
    CONSTRAINT fk_payment_receipt_shop     FOREIGN KEY (shop_id)     REFERENCES shop(id),
    INDEX idx_payment_receipt_payment  (payment_id),
    INDEX idx_payment_receipt_customer (customer_id, shop_id),
    INDEX idx_payment_receipt_shop_date (shop_id, receipt_date)
);

CREATE TABLE payment_receipt_sequence (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    shop_id      BIGINT      NOT NULL,
    prefix       VARCHAR(100) NOT NULL,
    fiscal_year  SMALLINT    NOT NULL,
    last_seq     BIGINT      NOT NULL DEFAULT 0,
    created_at   DATETIME    NOT NULL,
    updated_at   DATETIME    NOT NULL,

    CONSTRAINT uk_receipt_seq_shop_prefix_year UNIQUE (shop_id, prefix, fiscal_year)
);
