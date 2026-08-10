-- Flyway Migration V54: Create platform_details table & seed default vendor metadata

CREATE TABLE IF NOT EXISTS platform_details (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_name VARCHAR(255) NOT NULL,
    trade_name VARCHAR(255),
    gstin VARCHAR(20),
    pan VARCHAR(20),
    address_line1 VARCHAR(255),
    address_line2 VARCHAR(255),
    city VARCHAR(100),
    state VARCHAR(100),
    state_code VARCHAR(10),
    pincode VARCHAR(10),
    support_email VARCHAR(255),
    support_phone VARCHAR(30),
    hsn_sac_code VARCHAR(20) DEFAULT '998313',
    invoice_prefix VARCHAR(20) DEFAULT 'SUB-INV',
    bank_name VARCHAR(150),
    account_number VARCHAR(50),
    ifsc_code VARCHAR(20),
    upi_id VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Seed initial default platform configuration row if empty
INSERT INTO platform_details (
    id, company_name, trade_name, gstin, pan, address_line1, address_line2, city, state, state_code, pincode,
    support_email, support_phone, hsn_sac_code, invoice_prefix, bank_name, account_number, ifsc_code, upi_id
) SELECT 
    1, 'DesiTech Solutions Pvt. Ltd.', 'VyaparSathi Enterprise SaaS', '27AAACD1234E1Z5', 'AAACD1234E',
    '101, Tech Hub Tower', 'Senapati Bapat Marg, Lower Parel', 'Mumbai', 'Maharashtra', '27', '400013',
    'support@desitechsolutions.in', '+91 98765 43210', '998313', 'SUB-INV',
    'HDFC Bank Ltd.', '50200012345678', 'HDFC0000123', 'vyaparsathi@hdfcbank'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM platform_details WHERE id = 1);
