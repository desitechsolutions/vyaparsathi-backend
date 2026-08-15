-- Migration V97 — split the inventory-side reservation from the sales-order
-- one. V64 already owned the `stock_reservation` table for sales orders, so
-- V94's CREATE TABLE IF NOT EXISTS silently skipped and the inventory-side
-- InventoryReservation entity mapped to a table with the wrong shape
-- (missing expires_at, quantity, reference_type, etc.).
--
-- Fix: give the inventory-side reservations their own table. The sales-order
-- module keeps `stock_reservation` untouched.

CREATE TABLE IF NOT EXISTS inventory_reservation (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    shop_id           BIGINT       NOT NULL,
    item_variant_id   BIGINT       NOT NULL,
    quantity          DECIMAL(12,3) NOT NULL,
    reason            VARCHAR(50)  NOT NULL,
    reference_type    VARCHAR(50)  NULL,
    reference_id      BIGINT       NULL,
    reserved_by       VARCHAR(100) NULL,
    reserved_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at        TIMESTAMP    NULL DEFAULT NULL,
    status            VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    released_at       TIMESTAMP    NULL DEFAULT NULL,
    notes             VARCHAR(500) NULL,
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_ir_shop (shop_id),
    INDEX idx_ir_variant (item_variant_id),
    INDEX idx_ir_status (status),
    INDEX idx_ir_status_expires (status, expires_at)
);
