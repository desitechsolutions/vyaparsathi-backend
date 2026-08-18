-- =====================================================================
-- V101 — Enterprise Credit Note upgrade
-- =====================================================================
-- Two changes to bring the Credit Note module up to Zoho Books / SAP B1
-- parity:
--
-- 1. `credit_note_allocation` table  — one row per "apply-to-invoice" or
--    "cash-refund" event, so applied_amount becomes an auditable running
--    total instead of a floating number. Mirrors V98's debit_note_application.
--
-- 2. Two new columns on `credit_notes`:
--      - `reason_code` — enum tag (SALES_RETURN / POST_SALE_DISCOUNT /
--         DEFECTIVE_GOODS / INVOICE_CORRECTION / OTHER). Kept alongside the
--         legacy free-text `reason` column so historical rows stay valid.
--      - `restock_items` — whether the associated goods flowed back into
--         stock at approval time. Persisted for audit; the actual stock
--         movement is fired synchronously by CreditNoteService.
--
-- MySQL 8: no CREATE FUNCTION / PROCEDURE here — the log_bin_trust_function
-- setup on the target box may not permit them (see feedback memory).
-- =====================================================================

CREATE TABLE IF NOT EXISTS `credit_note_allocation` (
    `id`                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    `shop_id`               BIGINT       NOT NULL,
    `credit_note_id`        BIGINT       NOT NULL,

    -- INVOICE = applied against a sales invoice's outstanding balance.
    -- REFUND  = paid back to the customer via cash / bank.
    `allocation_type`       VARCHAR(20)  NOT NULL,

    `sale_id`               BIGINT       NULL,        -- populated when allocation_type=INVOICE
    `payment_reference`     VARCHAR(120) NULL,        -- bank txn id / cheque no / UPI ref
    `payment_mode`          VARCHAR(30)  NULL,        -- CASH / BANK / UPI / CHEQUE
    `allocated_amount`      DECIMAL(12,2) NOT NULL,
    `allocated_at`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `allocated_by`          VARCHAR(120) NULL,
    `note`                  VARCHAR(500) NULL,

    `reversed`              TINYINT(1)   NOT NULL DEFAULT 0,
    `reversed_at`           DATETIME     NULL,
    `reversed_by`           VARCHAR(120) NULL,

    `created_at`            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    KEY `idx_cna_shop`  (`shop_id`),
    KEY `idx_cna_cn`    (`credit_note_id`),
    KEY `idx_cna_sale`  (`sale_id`),
    CONSTRAINT `fk_cna_cn` FOREIGN KEY (`credit_note_id`)
        REFERENCES `credit_notes` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Idempotent column adds via INFORMATION_SCHEMA guards (no PROCEDURE).
SET @dbn := DATABASE();
SET @sql := (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = @dbn
        AND TABLE_NAME = 'credit_notes'
        AND COLUMN_NAME = 'reason_code') = 0,
    'ALTER TABLE `credit_notes` ADD COLUMN `reason_code` VARCHAR(30) NULL AFTER `reason`',
    'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = @dbn
        AND TABLE_NAME = 'credit_notes'
        AND COLUMN_NAME = 'restock_items') = 0,
    'ALTER TABLE `credit_notes` ADD COLUMN `restock_items` TINYINT(1) NOT NULL DEFAULT 0',
    'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Refund status shortcut. When true, the credit note was cash-refunded and
-- no further allocations can happen. Useful for the compliance dashboard.
SET @sql := (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = @dbn
        AND TABLE_NAME = 'credit_notes'
        AND COLUMN_NAME = 'refunded') = 0,
    'ALTER TABLE `credit_notes` ADD COLUMN `refunded` TINYINT(1) NOT NULL DEFAULT 0',
    'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
