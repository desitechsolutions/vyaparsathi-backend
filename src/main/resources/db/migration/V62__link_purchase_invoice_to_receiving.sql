-- V62: Link Purchase Invoices to their originating GRN (Receiving) so stock is
-- not double-counted.
--
-- Prior behaviour (bug): PurchaseInvoiceService.createPurchaseInvoice() called
-- stockService.addStock() unconditionally. Meanwhile ReceivingService already
-- added stock when the GRN was recorded. Creating both records for the same
-- physical shipment therefore incremented inventory twice.
--
-- Resolution: the FK below identifies the GRN a Purchase Invoice was created
-- against. When set, the service skips its own stock-add call — the GRN owns
-- the inventory increment. When null (direct-invoice flow, no GRN yet), the
-- service still adds stock as before.
--
-- Existing rows: left with receiving_id = NULL. This is correct historically —
-- prior to V62 the stock-add was owned by the PurchaseInvoice, so those rows
-- keep that ownership.

ALTER TABLE purchase_invoices
    ADD COLUMN receiving_id BIGINT NULL AFTER supplier_id,
    ADD CONSTRAINT fk_purchase_invoice_receiving
        FOREIGN KEY (receiving_id) REFERENCES receiving(id) ON DELETE SET NULL;

CREATE INDEX idx_purchase_invoice_receiving ON purchase_invoices (receiving_id);
