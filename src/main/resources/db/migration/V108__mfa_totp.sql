-- =====================================================================
-- V108 — MFA (TOTP) — Phase 3
-- =====================================================================
-- Adds per-user MFA state and a bag of backup codes.
--
-- Design:
--   * user_mfa_settings — one row per user; TOTP secret is stored so the
--     server can verify codes on every login. Not sensitive on its own
--     (needs the user's device to be useful), but treated like a password
--     hash: encrypted-at-rest at the DB level is out of scope for V108,
--     but the column length is generous enough for us to swap in an
--     envelope-encrypted value later without another migration.
--   * user_backup_codes — SHA-256 hashed one-time codes issued at MFA
--     enrollment. Ten codes, each single-use. Regenerating the set
--     supersedes the previous batch (delete + reissue).
--
-- Both tables are user-scoped, NOT shop-scoped: MFA belongs to the
-- identity that owns the account, not to a shop.
-- =====================================================================

CREATE TABLE IF NOT EXISTS user_mfa_settings (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id          BIGINT NOT NULL,
    secret           VARCHAR(255) NOT NULL,
    enabled          BOOLEAN NOT NULL DEFAULT FALSE,
    enrolled_at      DATETIME NULL,
    last_verified_at DATETIME NULL,
    created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_mfa_settings_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uk_user_mfa_settings_user UNIQUE (user_id)
);

CREATE TABLE IF NOT EXISTS user_backup_codes (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id          BIGINT NOT NULL,
    code_hash        VARCHAR(64) NOT NULL,
    used             BOOLEAN NOT NULL DEFAULT FALSE,
    used_at          DATETIME NULL,
    created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_backup_codes_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_user_backup_codes_user (user_id, used),
    INDEX idx_user_backup_codes_hash (code_hash)
);
