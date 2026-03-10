-- V38: Add supplier_payment table
-- Stores payments made to suppliers against purchase orders.
-- purchaseOrderId is kept as a plain reference (no FK) to keep the
-- supplier domain decoupled from the purchaseorder domain.

CREATE TABLE supplier_payment (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    shop_id           BIGINT        NOT NULL,
    supplier_id       BIGINT        NOT NULL,
    purchase_order_id BIGINT        NOT NULL,
    amount            DECIMAL(19,2) NOT NULL,
    payment_date      DATETIME,
    payment_method    VARCHAR(50),
    reference         VARCHAR(255),
    notes             TEXT,
    status            VARCHAR(50)   NOT NULL,
    created_at        DATETIME      NOT NULL,
    updated_at        DATETIME      NOT NULL,

    CONSTRAINT fk_sp_shop     FOREIGN KEY (shop_id)     REFERENCES shop(id),
    CONSTRAINT fk_sp_supplier FOREIGN KEY (supplier_id) REFERENCES supplier(id)
);
