-- Migration V86 (Phase 5 of the Purchase Order enterprise redesign):
-- attachments table for arbitrary supplier documents (quotes, price lists,
-- spec sheets, PDFs to forward with the PO). Metadata lives here; the actual
-- bytes are stored via the configured FileStorageService and addressed by
-- file_path. Mirrors the receiving_ticket_attachment shape.

CREATE TABLE IF NOT EXISTS purchase_order_attachment (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    shop_id            BIGINT       NOT NULL,
    purchase_order_id  BIGINT       NOT NULL,
    file_name          VARCHAR(255) NOT NULL,
    file_type          VARCHAR(100) NULL,
    file_path          VARCHAR(500) NOT NULL,
    uploaded_by        BIGINT       NULL,
    created_at         DATETIME     NOT NULL,
    updated_at         DATETIME     NOT NULL,

    INDEX idx_po_attachment_po (purchase_order_id),
    INDEX idx_po_attachment_shop (shop_id),
    CONSTRAINT fk_po_attachment_po FOREIGN KEY (purchase_order_id) REFERENCES purchase_order(id) ON DELETE CASCADE
);