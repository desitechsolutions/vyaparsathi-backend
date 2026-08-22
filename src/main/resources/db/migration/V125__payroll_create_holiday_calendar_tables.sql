-- V125: Create Holiday Calendar Tables
-- Includes: holiday_calendars, holiday_events

CREATE TABLE IF NOT EXISTS holiday_calendars (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    shop_id BIGINT NOT NULL,
    year INT NOT NULL,
    name VARCHAR(100) NOT NULL,
    total_working_days INT NOT NULL DEFAULT 252,
    weekly_off_days VARCHAR(50) NOT NULL DEFAULT 'SUNDAY,SATURDAY' COMMENT 'Comma-separated list',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE CASCADE,
    UNIQUE KEY uk_shop_year (shop_id, year),
    CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci,
    ENGINE=InnoDB
) COMMENT='Holiday calendar master per year';

CREATE TABLE IF NOT EXISTS holiday_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    shop_id BIGINT NOT NULL,
    calendar_id BIGINT NOT NULL,
    holiday_date DATE NOT NULL,
    event_name VARCHAR(200) NOT NULL,
    event_type VARCHAR(50) NOT NULL COMMENT 'NATIONAL, REGIONAL, OPTIONAL, RESTRICTED',
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE CASCADE,
    FOREIGN KEY (calendar_id) REFERENCES holiday_calendars(id) ON DELETE CASCADE,
    UNIQUE KEY uk_calendar_date (calendar_id, holiday_date),
    INDEX idx_shop_date (shop_id, holiday_date),
    CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci,
    ENGINE=InnoDB
) COMMENT='Holiday dates for working day calculation';
