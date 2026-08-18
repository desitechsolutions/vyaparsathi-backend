-- V115: Customer module Phase 2 — data-model foundation for the
-- enterprise redesign. Adds the tables + columns needed to close the
-- feature gap against Zoho Books / QuickBooks / SAP B1 without which
-- the FE redesign has nothing meaningful to render.
--
-- Six new tables:
--   customer_contact               — extra contact people per customer
--   customer_address               — billing / shipping / registered
--   customer_attachment            — files (KYC docs, contracts)
--   customer_note                  — free-text notes with author/timestamp
--   customer_custom_field_value    — EAV-style per-shop extension fields
--   customer_segment (+ member)    — normalised replacement for the
--                                    comma-separated `tags` column
--
-- Plus a set of new columns on `customer` for credit-hold flag,
-- assigned sales rep, MSME/TAN/TDS, comm opt-ins, currency preference.

-- ─── Helper procedures (idempotent) ─────────────────────────────────
DROP PROCEDURE IF EXISTS v115_ensure_column;
DELIMITER $$
CREATE PROCEDURE v115_ensure_column(IN tbl VARCHAR(64), IN col VARCHAR(64), IN ddl TEXT)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = tbl AND COLUMN_NAME = col
    ) THEN
        SET @s = CONCAT('ALTER TABLE `', tbl, '` ADD COLUMN ', ddl);
        PREPARE stmt FROM @s;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

-- ─── 1. customer_contact ────────────────────────────────────────────
-- Multiple contact persons per customer (a business may have a
-- purchase contact, an accounts contact, a delivery contact). Exactly
-- one row per customer is flagged is_primary — enforced by unique
-- partial index on (customer_id) where is_primary = 1 (MySQL 8 syntax).
CREATE TABLE IF NOT EXISTS customer_contact (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    shop_id       BIGINT NOT NULL,
    customer_id   BIGINT NOT NULL,
    name          VARCHAR(200) NOT NULL,
    designation   VARCHAR(120),
    phone         VARCHAR(30),
    email         VARCHAR(200),
    is_primary    BOOLEAN NOT NULL DEFAULT FALSE,
    notes         VARCHAR(500),
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_customer_contact_customer FOREIGN KEY (customer_id) REFERENCES customer(id) ON DELETE CASCADE,
    KEY idx_customer_contact_shop (shop_id),
    KEY idx_customer_contact_customer (customer_id),
    KEY idx_customer_contact_primary (customer_id, is_primary)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ─── 2. customer_address ────────────────────────────────────────────
-- Multiple addresses per customer. address_type is BILLING / SHIPPING
-- / REGISTERED. Two default flags (is_default_billing,
-- is_default_shipping) so one row can serve as both default billing
-- AND default shipping (common for retail customers).
CREATE TABLE IF NOT EXISTS customer_address (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    shop_id               BIGINT NOT NULL,
    customer_id           BIGINT NOT NULL,
    address_type          VARCHAR(20) NOT NULL DEFAULT 'BILLING',
    label                 VARCHAR(120),
    address_line1         VARCHAR(255),
    address_line2         VARCHAR(255),
    city                  VARCHAR(120),
    state                 VARCHAR(120),
    state_code            VARCHAR(2),
    postal_code           VARCHAR(20),
    country               VARCHAR(120) DEFAULT 'India',
    is_default_billing    BOOLEAN NOT NULL DEFAULT FALSE,
    is_default_shipping   BOOLEAN NOT NULL DEFAULT FALSE,
    created_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_customer_address_customer FOREIGN KEY (customer_id) REFERENCES customer(id) ON DELETE CASCADE,
    KEY idx_customer_address_shop (shop_id),
    KEY idx_customer_address_customer (customer_id),
    KEY idx_customer_address_default_billing (customer_id, is_default_billing),
    KEY idx_customer_address_default_shipping (customer_id, is_default_shipping)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ─── 3. customer_attachment ─────────────────────────────────────────
-- File attachments (KYC docs, MSME certificate, PO PDFs, contracts).
-- Storage is delegated to FileStorageService — this table stores only
-- the pointer. Mirrors receiving_attachment (V86) shape so we reuse
-- the same upload plumbing.
CREATE TABLE IF NOT EXISTS customer_attachment (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    shop_id        BIGINT NOT NULL,
    customer_id    BIGINT NOT NULL,
    file_name      VARCHAR(255) NOT NULL,
    file_path      VARCHAR(500) NOT NULL,
    mime_type      VARCHAR(120),
    size_bytes     BIGINT,
    category       VARCHAR(40),                -- KYC / CONTRACT / PO / OTHER
    uploaded_by    VARCHAR(120),
    created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_customer_attachment_customer FOREIGN KEY (customer_id) REFERENCES customer(id) ON DELETE CASCADE,
    KEY idx_customer_attachment_shop (shop_id),
    KEY idx_customer_attachment_customer (customer_id),
    KEY idx_customer_attachment_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ─── 4. customer_note ───────────────────────────────────────────────
-- User-written notes with author + timestamp. Distinct from
-- customer_audit (system events like "profile updated"). Renders
-- in the Notes tab of the customer 360° page.
CREATE TABLE IF NOT EXISTS customer_note (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    shop_id        BIGINT NOT NULL,
    customer_id    BIGINT NOT NULL,
    body           TEXT NOT NULL,
    author_user_id BIGINT,
    author_name    VARCHAR(120),
    pinned         BOOLEAN NOT NULL DEFAULT FALSE,
    created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_customer_note_customer FOREIGN KEY (customer_id) REFERENCES customer(id) ON DELETE CASCADE,
    KEY idx_customer_note_shop (shop_id),
    KEY idx_customer_note_customer (customer_id),
    KEY idx_customer_note_pinned (customer_id, pinned, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ─── 5. customer_custom_field_value ─────────────────────────────────
-- EAV table for per-shop custom fields. Field definitions come from
-- the existing custom_attributes (Phase 4) plumbing — no separate
-- definition table needed here.
CREATE TABLE IF NOT EXISTS customer_custom_field_value (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    shop_id        BIGINT NOT NULL,
    customer_id    BIGINT NOT NULL,
    field_key      VARCHAR(80) NOT NULL,
    field_value    TEXT,
    created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_customer_cfv_customer FOREIGN KEY (customer_id) REFERENCES customer(id) ON DELETE CASCADE,
    UNIQUE KEY uk_customer_cfv_customer_field (customer_id, field_key),
    KEY idx_customer_cfv_shop (shop_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ─── 6. customer_segment + customer_segment_member ──────────────────
-- Normalised replacement for the CSV `tags` VARCHAR(500) column.
-- customer.tags stays for backward-compat (CSV import/export still
-- writes to it) but new UI reads/writes the join table.
CREATE TABLE IF NOT EXISTS customer_segment (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    shop_id        BIGINT NOT NULL,
    name           VARCHAR(80) NOT NULL,
    description    VARCHAR(500),
    color          VARCHAR(16),               -- e.g. "#F59E0B" for chip rendering
    created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_customer_segment_shop_name (shop_id, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS customer_segment_member (
    customer_id    BIGINT NOT NULL,
    segment_id     BIGINT NOT NULL,
    added_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (customer_id, segment_id),
    CONSTRAINT fk_csm_customer FOREIGN KEY (customer_id) REFERENCES customer(id) ON DELETE CASCADE,
    CONSTRAINT fk_csm_segment  FOREIGN KEY (segment_id)  REFERENCES customer_segment(id) ON DELETE CASCADE,
    KEY idx_csm_segment (segment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ─── 7. Extend `customer` with enterprise columns ───────────────────
CALL v115_ensure_column('customer', 'credit_hold',
    'credit_hold BOOLEAN NOT NULL DEFAULT FALSE');
CALL v115_ensure_column('customer', 'assigned_user_id',
    'assigned_user_id BIGINT NULL');
CALL v115_ensure_column('customer', 'preferred_currency',
    'preferred_currency VARCHAR(3) NOT NULL DEFAULT ''INR''');
CALL v115_ensure_column('customer', 'msme_udyam',
    'msme_udyam VARCHAR(30) NULL');
CALL v115_ensure_column('customer', 'tan',
    'tan VARCHAR(20) NULL');
CALL v115_ensure_column('customer', 'tds_applicable',
    'tds_applicable BOOLEAN NOT NULL DEFAULT FALSE');
CALL v115_ensure_column('customer', 'email_opt_in',
    'email_opt_in BOOLEAN NOT NULL DEFAULT TRUE');
CALL v115_ensure_column('customer', 'sms_opt_in',
    'sms_opt_in BOOLEAN NOT NULL DEFAULT TRUE');
CALL v115_ensure_column('customer', 'whatsapp_opt_in',
    'whatsapp_opt_in BOOLEAN NOT NULL DEFAULT TRUE');

-- Index on assigned_user_id for the "sales rep dashboard" queries.
DROP PROCEDURE IF EXISTS v115_ensure_index;
DELIMITER $$
CREATE PROCEDURE v115_ensure_index(IN tbl VARCHAR(64), IN idx VARCHAR(64), IN cols VARCHAR(200))
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = tbl AND INDEX_NAME = idx
    ) THEN
        SET @s = CONCAT('CREATE INDEX `', idx, '` ON `', tbl, '` (', cols, ')');
        PREPARE stmt FROM @s;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

CALL v115_ensure_index('customer', 'idx_customer_assigned_user', 'shop_id, assigned_user_id');
CALL v115_ensure_index('customer', 'idx_customer_credit_hold', 'shop_id, credit_hold');

-- ─── Cleanup ─────────────────────────────────────────────────────────
DROP PROCEDURE IF EXISTS v115_ensure_column;
DROP PROCEDURE IF EXISTS v115_ensure_index;
