-- =============================================================================
-- V55: SuperAdmin SaaS Operations Control Center Migration (MySQL 8.x Safe)
-- =============================================================================

-- 1. Extend Audit Log Table with Nullable Admin & Impersonation Metadata
ALTER TABLE `audit_log` 
ADD COLUMN `actor_admin_id` BIGINT NULL AFTER `user_agent`,
ADD COLUMN `target_shop_id` BIGINT NULL AFTER `actor_admin_id`,
ADD COLUMN `reason` TEXT NULL AFTER `target_shop_id`,
ADD COLUMN `previous_value` TEXT NULL AFTER `reason`,
ADD COLUMN `new_value` TEXT NULL AFTER `previous_value`,
ADD COLUMN `impersonation_session_id` VARCHAR(64) NULL AFTER `new_value`;

CREATE INDEX `idx_audit_actor_admin` ON `audit_log`(`actor_admin_id`);
CREATE INDEX `idx_audit_target_shop` ON `audit_log`(`target_shop_id`);

-- 2. Platform Admin Invitations Table
CREATE TABLE IF NOT EXISTS `admin_invitations` (
    `id`                  BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `email`               VARCHAR(100) NOT NULL UNIQUE,
    `role`                VARCHAR(30)  NOT NULL DEFAULT 'TECH_ADMIN',
    `token_hash`          VARCHAR(64)  NOT NULL UNIQUE,
    `invited_by_admin_id` BIGINT       NOT NULL,
    `status`              VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    `expires_at`          DATETIME(6)  NOT NULL,
    `accepted_at`         DATETIME(6)  NULL,
    `created_at`          DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX `idx_admin_invites_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. Impersonation Sessions Audit Table
CREATE TABLE IF NOT EXISTS `impersonation_sessions` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `session_uuid`     VARCHAR(64)  NOT NULL UNIQUE,
    `super_admin_id`   BIGINT       NOT NULL,
    `super_admin_email` VARCHAR(100) NOT NULL,
    `target_user_id`   BIGINT       NOT NULL,
    `target_shop_id`   BIGINT       NOT NULL,
    `reason`           TEXT         NOT NULL,
    `started_at`       DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `expires_at`       DATETIME(6)  NOT NULL,
    `ended_at`         DATETIME(6)  NULL,
    `source_ip`        VARCHAR(45)  NULL,
    INDEX `idx_imp_super_admin` (`super_admin_id`),
    INDEX `idx_imp_target_shop` (`target_shop_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. Platform Feature Flags Table
CREATE TABLE IF NOT EXISTS `platform_feature_flags` (
    `feature_key`     VARCHAR(50)  NOT NULL PRIMARY KEY,
    `feature_name`    VARCHAR(100) NOT NULL,
    `description`     TEXT         NULL,
    `default_enabled` TINYINT(1)   NOT NULL DEFAULT 0,
    `category`        VARCHAR(30)  NOT NULL DEFAULT 'GENERAL'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. Tenant Feature Flags Table (shop_id is NOT NULL)
CREATE TABLE IF NOT EXISTS `tenant_feature_flags` (
    `id`                  BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `shop_id`             BIGINT      NOT NULL,
    `feature_key`         VARCHAR(50) NOT NULL,
    `enabled`             TINYINT(1)  NOT NULL,
    `updated_by_admin_id` BIGINT      NOT NULL,
    `updated_at`          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT `uq_shop_feature` UNIQUE (`shop_id`, `feature_key`),
    CONSTRAINT `fk_tf_shop` FOREIGN KEY (`shop_id`) REFERENCES `shop`(`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_tf_feature` FOREIGN KEY (`feature_key`) REFERENCES `platform_feature_flags`(`feature_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6. Tenant Limit Overrides Table
CREATE TABLE IF NOT EXISTS `tenant_limit_overrides` (
    `id`                  BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `shop_id`             BIGINT      NOT NULL,
    `resource_key`        VARCHAR(50) NOT NULL,
    `override_limit`      INT         NOT NULL,
    `start_date`          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `end_date`            DATETIME(6) NULL,
    `is_active`           TINYINT(1)  NOT NULL DEFAULT 1,
    `reason`              TEXT        NOT NULL,
    `created_by_admin_id` BIGINT      NOT NULL,
    `created_at`          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT `fk_limit_shop` FOREIGN KEY (`shop_id`) REFERENCES `shop`(`id`) ON DELETE CASCADE,
    INDEX `idx_limit_override_lookup` (`shop_id`, `resource_key`, `is_active`, `start_date`, `end_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
