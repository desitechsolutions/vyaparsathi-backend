-- =====================================================================
-- V110 — Multi-shop membership per user (Phase 5C)
-- =====================================================================
-- Adds the join table that lets a single user belong to multiple shops
-- with independent roles per shop.
--
-- Migration strategy (coexistence, not big-bang):
--   1. Create user_shop_membership.
--   2. Backfill one row per existing (user, users.shop_id) with the
--      legacy `role` from the user's row.
--   3. Keep `users.shop_id` as the user's PRIMARY / default shop.
--      New code reads memberships; legacy paths that resolve
--      `user.getShop()` keep working.
--   4. When a user switches shops at runtime, we re-issue a JWT with a
--      different `shopId` claim. `users.shop_id` stays pointing at
--      whatever their default is.
--
-- Each membership can also carry a role_id (V109 roles.id) — nullable so
-- old rows survive; the resolver falls back to the legacy `role` enum on
-- the users row when role_id is missing.
-- =====================================================================

CREATE TABLE IF NOT EXISTS user_shop_membership (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT NOT NULL,
    shop_id     BIGINT NOT NULL,
    role        VARCHAR(32) NOT NULL,       -- legacy enum value, always populated
    role_id     BIGINT NULL,                -- link to roles.id — added when the user is on the new RBAC role
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    is_default  BOOLEAN NOT NULL DEFAULT FALSE,
    joined_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    invited_by  BIGINT NULL,                -- user_id of the inviter (nullable for backfilled OWNER rows)
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_shop_membership_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_shop_membership_shop FOREIGN KEY (shop_id) REFERENCES shop(id)  ON DELETE CASCADE,
    CONSTRAINT fk_user_shop_membership_role FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE SET NULL,
    CONSTRAINT uk_user_shop_membership_pair UNIQUE (user_id, shop_id),
    INDEX idx_user_shop_membership_user (user_id, active),
    INDEX idx_user_shop_membership_shop (shop_id, active)
);

-- ---------------------------------------------------------------------
-- Backfill: every existing user that already has a shop_id gets a
-- membership row. is_default=true so switching UX has an obvious home.
-- ---------------------------------------------------------------------
INSERT INTO user_shop_membership (user_id, shop_id, role, active, is_default, joined_at, created_at, updated_at)
SELECT u.id, u.shop_id, u.role, u.active, TRUE, COALESCE(u.created_at, NOW()), NOW(), NOW()
  FROM users u
 WHERE u.shop_id IS NOT NULL
   AND NOT EXISTS (
       SELECT 1 FROM user_shop_membership m
        WHERE m.user_id = u.id AND m.shop_id = u.shop_id
   );
