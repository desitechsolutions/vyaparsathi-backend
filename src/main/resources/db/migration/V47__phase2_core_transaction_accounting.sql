-- =========================================================================
-- Migration V47: Phase 2 Core Transaction Integrity & Double-Entry Accounting Foundation
-- Adds place_of_supply to sale, purchase_invoices, credit_notes, debit_notes, and supplier_ledger
-- =========================================================================

-- 1. Add Place of Supply to sale table (for GST Inter-state vs Intra-state determination)
ALTER TABLE sale ADD COLUMN place_of_supply VARCHAR(100) DEFAULT NULL AFTER is_gst_required;

-- 2. Purchase Invoices (Vendor Bills / Goods Inward)
CREATE TABLE IF NOT EXISTS purchase_invoices (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    shop_id BIGINT NOT NULL,
    supplier_id BIGINT NOT NULL,
    purchase_invoice_no VARCHAR(50) NOT NULL,
    supplier_invoice_no VARCHAR(50) DEFAULT NULL,
    purchase_date DATE NOT NULL,
    payment_terms VARCHAR(50) DEFAULT 'NET_30',
    total_taxable_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    total_cgst DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    total_sgst DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    total_igst DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    total_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    paid_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    payment_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    notes VARCHAR(500) DEFAULT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_purchase_invoice_shop FOREIGN KEY (shop_id) REFERENCES shop(id),
    CONSTRAINT fk_purchase_invoice_supplier FOREIGN KEY (supplier_id) REFERENCES supplier(id),
    CONSTRAINT uk_shop_purchase_invoice UNIQUE (shop_id, purchase_invoice_no)
);

CREATE TABLE IF NOT EXISTS purchase_invoice_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    purchase_invoice_id BIGINT NOT NULL,
    item_variant_id BIGINT DEFAULT NULL,
    item_name VARCHAR(255) NOT NULL,
    hsn_code VARCHAR(20) DEFAULT NULL,
    batch_number VARCHAR(50) DEFAULT NULL,
    expiry_date DATE DEFAULT NULL,
    quantity DECIMAL(10,2) NOT NULL,
    unit_cost DECIMAL(12,2) NOT NULL,
    discount DECIMAL(12,2) DEFAULT 0.00,
    taxable_amount DECIMAL(12,2) NOT NULL,
    gst_rate DECIMAL(5,2) DEFAULT 0.00,
    cgst_amount DECIMAL(12,2) DEFAULT 0.00,
    sgst_amount DECIMAL(12,2) DEFAULT 0.00,
    igst_amount DECIMAL(12,2) DEFAULT 0.00,
    line_total DECIMAL(12,2) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_purchase_item_invoice FOREIGN KEY (purchase_invoice_id) REFERENCES purchase_invoices(id) ON DELETE CASCADE,
    CONSTRAINT fk_purchase_item_variant FOREIGN KEY (item_variant_id) REFERENCES item_variant(id) ON DELETE SET NULL
);

-- 3. Credit Notes (Issued to customers for returns / discounts)
CREATE TABLE IF NOT EXISTS credit_notes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    shop_id BIGINT NOT NULL,
    sale_id BIGINT DEFAULT NULL,
    customer_id BIGINT DEFAULT NULL,
    credit_note_no VARCHAR(50) NOT NULL,
    credit_note_date DATE NOT NULL,
    reason VARCHAR(255) NOT NULL,
    taxable_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    cgst_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    sgst_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    igst_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    total_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    status VARCHAR(20) NOT NULL DEFAULT 'ISSUED',
    notes VARCHAR(500) DEFAULT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_credit_note_shop FOREIGN KEY (shop_id) REFERENCES shop(id),
    CONSTRAINT fk_credit_note_sale FOREIGN KEY (sale_id) REFERENCES sale(id) ON DELETE SET NULL,
    CONSTRAINT fk_credit_note_customer FOREIGN KEY (customer_id) REFERENCES customer(id) ON DELETE SET NULL,
    CONSTRAINT uk_shop_credit_note UNIQUE (shop_id, credit_note_no)
);

-- 4. Debit Notes (Issued to suppliers for purchase returns / price adjustments)
CREATE TABLE IF NOT EXISTS debit_notes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    shop_id BIGINT NOT NULL,
    purchase_invoice_id BIGINT DEFAULT NULL,
    supplier_id BIGINT DEFAULT NULL,
    debit_note_no VARCHAR(50) NOT NULL,
    debit_note_date DATE NOT NULL,
    reason VARCHAR(255) NOT NULL,
    taxable_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    cgst_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    sgst_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    igst_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    total_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    status VARCHAR(20) NOT NULL DEFAULT 'ISSUED',
    notes VARCHAR(500) DEFAULT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_debit_note_shop FOREIGN KEY (shop_id) REFERENCES shop(id),
    CONSTRAINT fk_debit_note_purchase FOREIGN KEY (purchase_invoice_id) REFERENCES purchase_invoices(id) ON DELETE SET NULL,
    CONSTRAINT fk_debit_note_supplier FOREIGN KEY (supplier_id) REFERENCES supplier(id) ON DELETE SET NULL,
    CONSTRAINT uk_shop_debit_note UNIQUE (shop_id, debit_note_no)
);

-- 5. Supplier Ledger (Vendor Double-Entry Account Tracking)
CREATE TABLE IF NOT EXISTS supplier_ledger (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    shop_id BIGINT NOT NULL,
    supplier_id BIGINT NOT NULL,
    transaction_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    transaction_type VARCHAR(50) NOT NULL, -- 'PURCHASE_INVOICE', 'VENDOR_PAYMENT', 'DEBIT_NOTE', 'OPENING_BALANCE'
    reference_no VARCHAR(100) DEFAULT NULL,
    debit_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00,   -- Reduces vendor payable (payments, debit notes)
    credit_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00,  -- Increases vendor payable (purchases)
    running_balance DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    notes VARCHAR(255) DEFAULT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_supplier_ledger_shop FOREIGN KEY (shop_id) REFERENCES shop(id),
    CONSTRAINT fk_supplier_ledger_supplier FOREIGN KEY (supplier_id) REFERENCES supplier(id)
);
