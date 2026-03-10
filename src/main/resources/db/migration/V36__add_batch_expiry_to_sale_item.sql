-- V36: Add per-line-item batch and expiry tracking to sale_item.
-- batchNumber and expiryDate are captured at point-of-sale from the frontend
-- (sent as batchNumber / expiryDate inside each SaleItemDto) for batch
-- traceability and pharmacy narcotics-register compliance.

ALTER TABLE sale_item
    ADD COLUMN IF NOT EXISTS batch_number VARCHAR(100),
    ADD COLUMN IF NOT EXISTS expiry_date  DATE;
