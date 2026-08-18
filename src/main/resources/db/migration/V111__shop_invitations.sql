-- =====================================================================
-- V111 — Shop-level staff invitations (Phase 5E)
-- =====================================================================
-- Mirrors admin_invitation (platform-level) but scoped to a shop.
-- Only the SHA-256 hash of the token is persisted — the raw token is
-- delivered in the invitation email exactly once.
--
-- Lifecycle:
--   PENDING  — invitation created, email sent, not yet accepted
--   ACCEPTED — recipient completed sign-up / linking; membership row exists
--   REVOKED  — inviter cancelled it before acceptance
--   EXPIRED  — TTL passed without acceptance (soft state; enforced at read-time)
--
-- Accepting an invitation is what creates the user_shop_membership row.
-- =====================================================================

CREATE TABLE IF NOT EXISTS shop_invitations (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    shop_id       BIGINT NOT NULL,
    email         VARCHAR(255) NOT NULL,
    phone         VARCHAR(20) NULL,
    role_name     VARCHAR(64) NOT NULL,    -- resolves to roles.name within the shop
    invited_by    BIGINT NOT NULL,
    inviter_name  VARCHAR(200) NULL,
    token_hash    VARCHAR(64) NOT NULL UNIQUE,
    status        VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    message       VARCHAR(500) NULL,        -- optional personal note from the inviter
    expires_at    DATETIME NOT NULL,
    accepted_at   DATETIME NULL,
    accepted_by_user_id BIGINT NULL,
    revoked_at    DATETIME NULL,
    revoked_by    BIGINT NULL,
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_shop_invitations_shop FOREIGN KEY (shop_id) REFERENCES shop(id) ON DELETE CASCADE,
    CONSTRAINT fk_shop_invitations_inviter FOREIGN KEY (invited_by) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_shop_invitations_accepted_by FOREIGN KEY (accepted_by_user_id) REFERENCES users(id) ON DELETE SET NULL,
    INDEX idx_shop_invitations_shop_status (shop_id, status),
    INDEX idx_shop_invitations_email (email),
    INDEX idx_shop_invitations_status_expires (status, expires_at)
);
