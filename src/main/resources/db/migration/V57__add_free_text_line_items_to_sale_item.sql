-- V57: Support free-text (service / one-off) line items on sales.
--
-- Rationale: Vyaparsathi is currently unable to bill service work, one-off
-- charges (freight, packaging, installation, labor), or any item not present
-- in the ItemVariant catalog because sale_item.item_variant_id was NOT NULL.
--
-- This migration:
--   1. Relaxes the item_variant_id FK to nullable.
--   2. Adds four columns describing a custom (non-catalog) line item.
--   3. Adds a CHECK constraint enforcing that every row is either a catalog
--      line (item_variant_id NOT NULL) or a custom line (custom_item_name
--      NOT NULL) -- never both null.
--
-- All other NOT NULL columns on sale_item (unit_price, taxable_value, gst_type,
-- cgst_amt, sgst_amt, igst_amt) remain required and are populated the same way
-- for custom lines using the user-supplied GST rate.

ALTER TABLE sale_item MODIFY COLUMN item_variant_id BIGINT NULL;

ALTER TABLE sale_item
    ADD COLUMN custom_item_name   VARCHAR(255) NULL AFTER item_variant_id,
    ADD COLUMN custom_description VARCHAR(500) NULL AFTER custom_item_name,
    ADD COLUMN custom_hsn_sac     VARCHAR(20)  NULL AFTER custom_description,
    ADD COLUMN custom_unit        VARCHAR(30)  NULL AFTER custom_hsn_sac;

ALTER TABLE sale_item
    ADD CONSTRAINT chk_sale_item_has_product_or_custom
    CHECK (item_variant_id IS NOT NULL OR custom_item_name IS NOT NULL);
