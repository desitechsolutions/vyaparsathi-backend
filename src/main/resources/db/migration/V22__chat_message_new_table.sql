CREATE TABLE support_chats (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,

    -- Shop awareness (Assuming ShopAwareEntity uses shop_id)
    shop_id BIGINT NOT NULL,

    -- Content fields
    sender_name VARCHAR(255),
    message TEXT NOT NULL,

    -- Flags
    is_from_admin BOOLEAN DEFAULT FALSE,
    is_read_by_admin BOOLEAN DEFAULT FALSE,

    -- Auditing fields (Inherited from ShopAwareEntity/BaseEntity)
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),

    -- Indexes for performance
    INDEX idx_support_shop (shop_id),
    INDEX idx_support_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;