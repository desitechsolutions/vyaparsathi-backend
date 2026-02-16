CREATE TABLE password_reset_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,

    -- From ShopAwareEntity
    shop_id BIGINT NOT NULL,

    -- Token details
    token VARCHAR(255) NOT NULL,
    user_id BIGINT NOT NULL,
    expiry_date DATETIME(6) NOT NULL,

    -- Status flags
    used BOOLEAN DEFAULT FALSE NOT NULL,
    used_at DATETIME(6),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,

    -- Constraints
    UNIQUE (token),

    -- Foreign Key to Users table
    CONSTRAINT fk_password_reset_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE CASCADE,

    -- Index for performance (Token lookup and cleaning up expired tokens)
    INDEX idx_reset_token (token),
    INDEX idx_shop_reset (shop_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;