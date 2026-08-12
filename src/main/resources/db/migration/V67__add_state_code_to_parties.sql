-- ═══════════════════════════════════════════════════════════════════
-- V67 — Add 2-digit GST state code to shop, customer, supplier.
--
-- Phase 3.1 (State registry): every party that appears on a tax invoice
-- must carry the 2-digit GSTN state code (01–38, plus "97" for Other
-- Territory). This code — not the free-text state name — drives:
--   • CGST + SGST when shop and buyer share a state
--   • CGST + UTGST when shop is in a UT
--   • IGST when they differ
--   • The place-of-supply field on GSTR-1 B2B and B2CL invoices
--
-- Column is NULL-safe: existing rows keep NULL until a shop owner edits
-- the record or runs a follow-up backfill. Application code falls back to
-- the state-name lookup while state_code is NULL, so this migration is
-- non-breaking. A later migration (V68+) can enforce NOT NULL once
-- backfill is verified.
-- ═══════════════════════════════════════════════════════════════════

ALTER TABLE shop
    ADD COLUMN state_code VARCHAR(2) NULL COMMENT '2-digit GSTN state code (01–38, 97)';

ALTER TABLE customer
    ADD COLUMN state_code VARCHAR(2) NULL COMMENT '2-digit GSTN state code (01–38, 97)';

ALTER TABLE supplier
    ADD COLUMN state_code VARCHAR(2) NULL COMMENT '2-digit GSTN state code (01–38, 97)';

-- Best-effort backfill from state name. Only unambiguous canonical names
-- are matched; misspellings, abbreviations, or historical names remain
-- NULL for manual review. Legacy codes (25 for old Dadra, 28 for old AP)
-- are intentionally not backfilled — records still on those state names
-- must be reviewed by the shop owner.

UPDATE shop SET state_code = CASE LOWER(TRIM(state))
    WHEN 'andhra pradesh'                    THEN '37'
    WHEN 'arunachal pradesh'                 THEN '12'
    WHEN 'assam'                             THEN '18'
    WHEN 'bihar'                             THEN '10'
    WHEN 'chhattisgarh'                      THEN '22'
    WHEN 'goa'                               THEN '30'
    WHEN 'gujarat'                           THEN '24'
    WHEN 'haryana'                           THEN '06'
    WHEN 'himachal pradesh'                  THEN '02'
    WHEN 'jharkhand'                         THEN '20'
    WHEN 'karnataka'                         THEN '29'
    WHEN 'kerala'                            THEN '32'
    WHEN 'madhya pradesh'                    THEN '23'
    WHEN 'maharashtra'                       THEN '27'
    WHEN 'manipur'                           THEN '14'
    WHEN 'meghalaya'                         THEN '17'
    WHEN 'mizoram'                           THEN '15'
    WHEN 'nagaland'                          THEN '13'
    WHEN 'odisha'                            THEN '21'
    WHEN 'punjab'                            THEN '03'
    WHEN 'rajasthan'                         THEN '08'
    WHEN 'sikkim'                            THEN '11'
    WHEN 'tamil nadu'                        THEN '33'
    WHEN 'telangana'                         THEN '36'
    WHEN 'tripura'                           THEN '16'
    WHEN 'uttarakhand'                       THEN '05'
    WHEN 'uttar pradesh'                     THEN '09'
    WHEN 'west bengal'                       THEN '19'
    WHEN 'andaman and nicobar islands'       THEN '35'
    WHEN 'chandigarh'                        THEN '04'
    WHEN 'dadra and nagar haveli and daman and diu' THEN '26'
    WHEN 'delhi'                             THEN '07'
    WHEN 'jammu and kashmir'                 THEN '01'
    WHEN 'ladakh'                            THEN '38'
    WHEN 'lakshadweep'                       THEN '31'
    WHEN 'puducherry'                        THEN '34'
    WHEN 'other territory'                   THEN '97'
    ELSE NULL
END
WHERE state IS NOT NULL AND state_code IS NULL;

UPDATE customer SET state_code = CASE LOWER(TRIM(state))
    WHEN 'andhra pradesh'                    THEN '37'
    WHEN 'arunachal pradesh'                 THEN '12'
    WHEN 'assam'                             THEN '18'
    WHEN 'bihar'                             THEN '10'
    WHEN 'chhattisgarh'                      THEN '22'
    WHEN 'goa'                               THEN '30'
    WHEN 'gujarat'                           THEN '24'
    WHEN 'haryana'                           THEN '06'
    WHEN 'himachal pradesh'                  THEN '02'
    WHEN 'jharkhand'                         THEN '20'
    WHEN 'karnataka'                         THEN '29'
    WHEN 'kerala'                            THEN '32'
    WHEN 'madhya pradesh'                    THEN '23'
    WHEN 'maharashtra'                       THEN '27'
    WHEN 'manipur'                           THEN '14'
    WHEN 'meghalaya'                         THEN '17'
    WHEN 'mizoram'                           THEN '15'
    WHEN 'nagaland'                          THEN '13'
    WHEN 'odisha'                            THEN '21'
    WHEN 'punjab'                            THEN '03'
    WHEN 'rajasthan'                         THEN '08'
    WHEN 'sikkim'                            THEN '11'
    WHEN 'tamil nadu'                        THEN '33'
    WHEN 'telangana'                         THEN '36'
    WHEN 'tripura'                           THEN '16'
    WHEN 'uttarakhand'                       THEN '05'
    WHEN 'uttar pradesh'                     THEN '09'
    WHEN 'west bengal'                       THEN '19'
    WHEN 'andaman and nicobar islands'       THEN '35'
    WHEN 'chandigarh'                        THEN '04'
    WHEN 'dadra and nagar haveli and daman and diu' THEN '26'
    WHEN 'delhi'                             THEN '07'
    WHEN 'jammu and kashmir'                 THEN '01'
    WHEN 'ladakh'                            THEN '38'
    WHEN 'lakshadweep'                       THEN '31'
    WHEN 'puducherry'                        THEN '34'
    WHEN 'other territory'                   THEN '97'
    ELSE NULL
END
WHERE state IS NOT NULL AND state_code IS NULL;

-- Supplier does not have a state column today (per V1 baseline); add one.
-- If future schema adds supplier.state as a separate column we can extend
-- the same backfill pattern there. For now supplier.state_code is left
-- NULL and populated only when the supplier is edited.

CREATE INDEX idx_shop_state_code     ON shop(state_code);
CREATE INDEX idx_customer_state_code ON customer(state_code);
CREATE INDEX idx_supplier_state_code ON supplier(state_code);
