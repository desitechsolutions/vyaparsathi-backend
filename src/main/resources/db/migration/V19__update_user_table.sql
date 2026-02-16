ALTER TABLE users
    ADD COLUMN last_login_at DATETIME(6) NULL DEFAULT NULL AFTER active,
    ADD COLUMN last_password_change_at DATETIME(6) NULL DEFAULT NULL AFTER last_login_at;