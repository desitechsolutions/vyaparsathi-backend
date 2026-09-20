-- Migration V141: Add CIN column to platform_details and update platform corporate identity
SET @dbname = DATABASE();

SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = 'platform_details' AND COLUMN_NAME = 'cin') > 0,
  'SELECT 1',
  'ALTER TABLE platform_details ADD COLUMN cin VARCHAR(30) NULL AFTER trade_name'
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Update all existing platform_details rows with official corporate identity
UPDATE platform_details
SET company_name   = 'Biruma Technology Solutions Pvt. Ltd.',
    trade_name     = 'VyaparSathi Enterprise SaaS',
    cin            = 'U62010HR2025PTC139151',
    gstin          = '06AAOCB1973G1ZJ',
    pan            = 'AAOCB1973G',
    address_line1  = 'Arjun Nagar',
    address_line2  = '',
    city           = 'Gurgaon',
    state          = 'Haryana',
    state_code     = '06',
    pincode        = '122001',
    support_email  = 'contact@desitechsolutions.com';

-- Insert default row 1 if table has no records
INSERT INTO platform_details (
    id, company_name, trade_name, cin, gstin, pan, address_line1, address_line2, city, state, state_code, pincode,
    support_email, support_phone, hsn_sac_code, invoice_prefix, bank_name, account_number, ifsc_code, upi_id
) SELECT 
    1, 'Biruma Technology Solutions Pvt. Ltd.', 'VyaparSathi Enterprise SaaS', 'U62010HR2025PTC139151', '06AAOCB1973G1ZJ', 'AAOCB1973G',
    'Arjun Nagar', '', 'Gurgaon', 'Haryana', '06', '122001',
    'contact@desitechsolutions.com', '+91 98765 43210', '998313', 'SUB-INV',
    'HDFC Bank Ltd.', '50200012345678', 'HDFC0000123', 'vyaparsathi@hdfcbank'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM platform_details LIMIT 1);
