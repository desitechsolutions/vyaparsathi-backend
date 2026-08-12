-- V65: Proforma Invoice support.
--
-- Adds a Sale.saleType classifier so a single Sale row can represent either a
-- real tax invoice (INVOICE, the default) or a proforma invoice (PROFORMA).
--
-- Behaviour differences enforced at the service layer:
--   • PROFORMA sales use the "PI" number series (PI/YY-YY/NNNNN)
--   • PROFORMA sales do NOT deduct stock
--   • PROFORMA sales do NOT create customer-ledger CREDIT entries
--     (the customer does not owe money until the proforma is converted to a
--     real invoice)
--
-- {@code proforma_source_sale_id} links a real invoice back to its originating
-- proforma so conversion is atomic (and one proforma cannot be converted twice).

ALTER TABLE sale
    ADD COLUMN sale_type VARCHAR(20) NOT NULL DEFAULT 'INVOICE',
    ADD COLUMN proforma_source_sale_id BIGINT NULL,
    ADD CONSTRAINT chk_sale_type CHECK (sale_type IN ('INVOICE', 'PROFORMA')),
    ADD CONSTRAINT fk_sale_proforma_source FOREIGN KEY (proforma_source_sale_id) REFERENCES sale(id) ON DELETE SET NULL,
    ADD INDEX idx_sale_shop_type_status (shop_id, sale_type, status);

-- Existing rows: sale_type defaults to 'INVOICE' — every historical sale
-- remains a real invoice. No data migration needed.
