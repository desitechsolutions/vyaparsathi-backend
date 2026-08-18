-- =====================================================================
-- V112 — Shop-level MFA policy (Phase 5 deferred)
-- =====================================================================
-- Adds a per-shop flag: when true, OWNER + ADMIN members must have MFA
-- enabled on their account to sign in. Enforced at
-- AuthService.authenticateAndGenerateToken time.
--
-- Idempotent via INFORMATION_SCHEMA guard.
-- =====================================================================

DROP PROCEDURE IF EXISTS v112_add_col;

DELIMITER $$
CREATE PROCEDURE v112_add_col(
    IN p_table  VARCHAR(64),
    IN p_column VARCHAR(64),
    IN p_ddl    VARCHAR(1024)
)
BEGIN
    IF EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table
    )
    AND NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = p_table
          AND COLUMN_NAME = p_column
    )
    THEN
        SET @sql = CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN ', p_ddl);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

CALL v112_add_col(
    'shop',
    'require_mfa_for_admins',
    '`require_mfa_for_admins` BOOLEAN NOT NULL DEFAULT FALSE'
);

DROP PROCEDURE IF EXISTS v112_add_col;
