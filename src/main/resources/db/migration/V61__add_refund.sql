-- V61: Formal refund document.
--
-- A Refund tracks money returned to a customer against a specific Payment.
-- Prior to this migration, refunds were only reflected as a negative-amount
-- Payment row (see the commented processCashRefund method in PaymentServiceImpl)
-- — no dedicated document, no number, no audit trail.
--
-- The Refund entity gives us:
--   • a stable RefundNo the customer can be given as proof
--   • per-refund method/reference/notes (partial refunds can differ from the original payment)
--   • an audit-friendly link back to the originating Payment
--   • aggregation to detect over-refunds against the same Payment

CREATE TABLE refund (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    refund_no            VARCHAR(80)  NOT NULL,
    original_payment_id  BIGINT       NOT NULL,
    customer_id          BIGINT       NULL,
    refund_date          DATETIME     NOT NULL,
    amount               DECIMAL(15,2) NOT NULL,
    payment_method       VARCHAR(30)  NOT NULL,
    reference            VARCHAR(255) NULL,
    notes                TEXT         NULL,
    status               VARCHAR(20)  NOT NULL DEFAULT 'COMPLETED',
    shop_id              BIGINT       NOT NULL,
    created_at           DATETIME     NOT NULL,
    updated_at           DATETIME     NOT NULL,

    CONSTRAINT uk_refund_shop_number UNIQUE (shop_id, refund_no),
    CONSTRAINT fk_refund_payment  FOREIGN KEY (original_payment_id) REFERENCES payment(id),
    CONSTRAINT fk_refund_customer FOREIGN KEY (customer_id)         REFERENCES customer(id),
    CONSTRAINT fk_refund_shop     FOREIGN KEY (shop_id)             REFERENCES shop(id),
    CONSTRAINT chk_refund_status  CHECK (status IN ('INITIATED', 'COMPLETED', 'FAILED')),

    INDEX idx_refund_payment  (original_payment_id),
    INDEX idx_refund_customer (customer_id, shop_id),
    INDEX idx_refund_date     (shop_id, refund_date)
);

CREATE TABLE refund_sequence (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    shop_id      BIGINT      NOT NULL,
    prefix       VARCHAR(100) NOT NULL,
    fiscal_year  SMALLINT    NOT NULL,
    last_seq     BIGINT      NOT NULL DEFAULT 0,
    created_at   DATETIME    NOT NULL,
    updated_at   DATETIME    NOT NULL,

    CONSTRAINT uk_refund_seq_shop_prefix_year UNIQUE (shop_id, prefix, fiscal_year)
);
