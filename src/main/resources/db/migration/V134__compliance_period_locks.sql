-- Phase 5: Period Lock Engine — stores per-shop, per-month, per-form-type locks
-- that prevent any financial mutation within a locked tax period.
CREATE TABLE compliance_period_locks (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    shop_id          BIGINT       NOT NULL,
    period_year      INT          NOT NULL,
    period_month     INT          NOT NULL,
    form_type        VARCHAR(20)  NOT NULL DEFAULT 'ALL',
    status           VARCHAR(20)  NOT NULL DEFAULT 'LOCKED',
    reason           VARCHAR(500),
    locked_at        DATETIME(6),
    locked_by_user_id BIGINT,
    locked_by        VARCHAR(255),
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_period_lock (shop_id, period_year, period_month, form_type),
    KEY idx_period_lock_shop (shop_id)
);
