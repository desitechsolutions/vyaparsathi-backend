-- Migration for Shop Settings enhancements
-- Compatibility: MySQL 8.0+ / 8.4+

-- 1. Add columns using standard ALTER TABLE (remove IF NOT EXISTS)
-- If Flyway fails because columns already exist, you may need to wrap this in a procedure
-- or ensure your development environment matches the migration history.

ALTER TABLE shop
    ADD COLUMN phone VARCHAR(20) DEFAULT NULL AFTER locale,
    ADD COLUMN email VARCHAR(255) DEFAULT NULL AFTER phone,
    ADD COLUMN is_composition_scheme TINYINT(1) DEFAULT 0,
    ADD COLUMN brand_color VARCHAR(20) DEFAULT '#2980b9',
    ADD COLUMN terms_and_conditions TEXT DEFAULT NULL,
    ADD COLUMN bank_details TEXT DEFAULT NULL;

-- 2. Ensure columns are TEXT (in case they were created as VARCHAR previously)
ALTER TABLE shop
    MODIFY COLUMN terms_and_conditions TEXT,
    MODIFY COLUMN bank_details TEXT;

-- 3. Indexing for shop code lookups
-- Note: If the index already exists, this might fail. Flyway migrations should be clean.
CREATE INDEX idx_shop_code ON shop (code);