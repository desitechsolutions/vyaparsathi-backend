-- Shop modifications
ALTER TABLE shop
ADD COLUMN upi_id VARCHAR(100) NULL,
ADD COLUMN invoice_prefix VARCHAR(50) NULL,
ADD COLUMN company_website VARCHAR(255) NULL,
ADD COLUMN invoice_footer TEXT NULL,
ADD COLUMN support_contact VARCHAR(100) NULL,
ADD COLUMN invoice_due_days INT DEFAULT 30;

-- Sale modifications
ALTER TABLE sale
ADD COLUMN due_date DATE NULL,
ADD COLUMN invoice_discount DECIMAL(12,2) DEFAULT 0.00,
ADD COLUMN shipping_charges DECIMAL(12,2) DEFAULT 0.00,
ADD COLUMN other_charges DECIMAL(12,2) DEFAULT 0.00;
