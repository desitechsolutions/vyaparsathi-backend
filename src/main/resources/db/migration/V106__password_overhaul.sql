-- =====================================================================
-- V106 — Password Overhaul (Phase 1B)
-- =====================================================================
-- 1. Rename users.pin_hash → users.password_hash (in preparation for
--    real 8+ char passwords replacing the 4-char PIN).
-- 2. Create password_history table so we can reject reuse of the last N
--    passwords (default N=5, enforced at the service layer).
--
-- Idempotent via INFORMATION_SCHEMA-guarded stored procedures.
-- =====================================================================

DROP PROCEDURE IF EXISTS v106_rename_col;

DELIMITER $$

CREATE PROCEDURE v106_rename_col(
    IN p_table    VARCHAR(64),
    IN p_from     VARCHAR(64),
    IN p_to       VARCHAR(64),
    IN p_col_type VARCHAR(128)
)
BEGIN
    IF EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = p_table
          AND COLUMN_NAME = p_from
    )
    AND NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = p_table
          AND COLUMN_NAME = p_to
    )
    THEN
        SET @sql = CONCAT('ALTER TABLE `', p_table, '` CHANGE COLUMN `',
                          p_from, '` `', p_to, '` ', p_col_type);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

DELIMITER ;

-- ---------------------------------------------------------------------
-- 1. Rename pin_hash → password_hash
-- ---------------------------------------------------------------------
CALL v106_rename_col('users', 'pin_hash', 'password_hash', 'VARCHAR(255) NOT NULL');

-- ---------------------------------------------------------------------
-- 2. password_history — last N password hashes per user
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS password_history (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_password_history_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_password_history_user_created (user_id, created_at)
);

DROP PROCEDURE IF EXISTS v106_rename_col;
