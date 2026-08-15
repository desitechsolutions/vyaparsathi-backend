-- =====================================================================
-- V100 — Structured shop bank accounts
-- =====================================================================
-- Replaces the free-text `shop.bank_details` blob (and the fragile parser
-- that lived in PartyMapper) with a proper 1:N table. Every account has
-- its own columns for holder / number / IFSC / branch / SWIFT / IBAN, an
-- opt-in `display_on_invoice` flag, and a `purpose` classifier so shops
-- with multiple accounts (current + escrow + payroll + EEFC) can route
-- documents to the right one.
--
-- The `shop.bank_details` column is kept nullable but no longer read at
-- runtime — deprecated until a subsequent release drops it.
--
-- Note: backfill (parsing existing `shop.bank_details` blobs into rows)
-- runs from Java (`ShopBankAccountBackfillRunner`) on next boot. That
-- avoids CREATE FUNCTION / CREATE PROCEDURE, which MySQL rejects with
-- error 1419 for users without SUPER and without
-- `log_bin_trust_function_creators=1`.
-- =====================================================================

CREATE TABLE IF NOT EXISTS `shop_bank_account` (
    `id`                    BIGINT NOT NULL AUTO_INCREMENT,
    `shop_id`               BIGINT NOT NULL,

    `label`                 VARCHAR(60) NULL,
    `account_holder_name`   VARCHAR(255) NOT NULL,
    `account_number`        VARCHAR(40)  NOT NULL,
    `bank_name`             VARCHAR(120) NOT NULL,
    `ifsc_code`             VARCHAR(15)  NULL,
    `branch`                VARCHAR(120) NULL,
    `account_type`          VARCHAR(20)  NULL,       -- CURRENT / SAVINGS / CC / OD / NRE / NRO / EEFC
    `currency_code`         VARCHAR(3)   NOT NULL DEFAULT 'INR',
    `swift_code`            VARCHAR(11)  NULL,
    `iban`                  VARCHAR(34)  NULL,
    `upi_id`                VARCHAR(80)  NULL,
    `purpose`               VARCHAR(20)  NULL,       -- COLLECTIONS / PAYROLL / ESCROW / GENERAL
    `is_default`            TINYINT(1)   NOT NULL DEFAULT 0,
    `is_active`             TINYINT(1)   NOT NULL DEFAULT 1,
    `display_on_invoice`    TINYINT(1)   NOT NULL DEFAULT 1,
    `notes`                 TEXT NULL,

    `created_at`            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (`id`),
    KEY `idx_sba_shop`     (`shop_id`),
    KEY `idx_sba_active`   (`shop_id`, `is_active`),
    KEY `idx_sba_default`  (`shop_id`, `currency_code`, `is_default`)
) ENGINE=InnoDB;
