-- Phase 1: Salary Structure Configuration Tables

CREATE TABLE IF NOT EXISTS `salary_structures` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `shop_id` BIGINT NOT NULL,
    `structure_name` VARCHAR(100) NOT NULL,
    `structure_code` VARCHAR(50) NOT NULL,
    `description` TEXT,
    `is_active` BOOLEAN DEFAULT TRUE,
    `effective_from` DATE NOT NULL,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY `uk_shop_structure_code` (`shop_id`, `structure_code`),
    INDEX `idx_shop_active` (`shop_id`, `is_active`),
    CONSTRAINT `fk_struct_shop` FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`) ON DELETE CASCADE

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Individual salary components (Basic, HRA, DA, Special, PF, ESI, etc.)
CREATE TABLE IF NOT EXISTS `salary_components` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `shop_id` BIGINT NOT NULL,
    `structure_id` BIGINT NOT NULL,
    `component_name` VARCHAR(100) NOT NULL,
    `component_code` VARCHAR(50) NOT NULL,
    `component_type` ENUM('EARNING', 'DEDUCTION', 'EMPLOYER_CONTRIBUTION') NOT NULL,
    `calculation_type` ENUM('FLAT_AMOUNT', 'PERCENTAGE_OF_BASIC', 'PERCENTAGE_OF_GROSS', 'FORMULA') NOT NULL,
    `calculation_value` DECIMAL(10, 4) NOT NULL DEFAULT 0.0000,
    `is_taxable` BOOLEAN DEFAULT TRUE,
    `affects_pf` BOOLEAN DEFAULT TRUE,
    `affects_esi` BOOLEAN DEFAULT TRUE,
    `is_statutory` BOOLEAN DEFAULT FALSE,
    `is_active` BOOLEAN DEFAULT TRUE,
    `order_sequence` INT DEFAULT 0,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY `uk_comp_structure_code` (`structure_id`, `component_code`),
    INDEX `idx_structure` (`structure_id`),
    INDEX `idx_type` (`component_type`),
    CONSTRAINT `fk_comp_struct` FOREIGN KEY (`structure_id`) REFERENCES `salary_structures` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_comp_shop` FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`) ON DELETE CASCADE

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
