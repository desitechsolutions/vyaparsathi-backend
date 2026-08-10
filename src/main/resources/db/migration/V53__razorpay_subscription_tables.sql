-- =============================================================================
-- V53: Razorpay AutoPay Subscription Tables
-- =============================================================================
-- Creates four new tables to support Razorpay subscription integration.
-- All existing tables are unchanged (backward-compatible migration).
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. razorpay_customer
--    Stores one Razorpay customer record per VyaparSathi shop.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS razorpay_customer (
    id                    BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    shop_id               BIGINT       NOT NULL UNIQUE COMMENT 'VyaparSathi shop FK',
    razorpay_customer_id  VARCHAR(100) NOT NULL UNIQUE COMMENT 'cust_XXXX from Razorpay API',
    email                 VARCHAR(150),
    contact               VARCHAR(20),
    created_at            DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at            DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    INDEX idx_rzp_customer_shop_id (shop_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Razorpay customer records (one per shop)';

-- ---------------------------------------------------------------------------
-- 2. razorpay_subscription_order
--    Tracks every Razorpay subscription mandate created for a shop.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS razorpay_subscription_order (
    id                        BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    shop_id                   BIGINT       NOT NULL COMMENT 'VyaparSathi shop FK',
    plan_code                 VARCHAR(50)  NOT NULL COMMENT 'Tier name: STARTER | PRO | ENTERPRISE',
    razorpay_plan_id          VARCHAR(100) NOT NULL COMMENT 'plan_XXXX from Razorpay',
    razorpay_subscription_id  VARCHAR(100) NOT NULL UNIQUE COMMENT 'sub_XXXX from Razorpay',
    razorpay_customer_id      VARCHAR(100) NOT NULL COMMENT 'cust_XXXX from Razorpay',
    status                    VARCHAR(50)  NOT NULL DEFAULT 'CREATED'
                              COMMENT 'CREATED|AUTHENTICATED|ACTIVE|PENDING|PAUSED|HALTED|CANCELLED|COMPLETED|EXPIRED',
    mandate_status            VARCHAR(50)           DEFAULT 'PENDING'
                              COMMENT 'PENDING|ACTIVE|HALTED|REJECTED|CANCELLED',
    billing_cycle             VARCHAR(20)  NOT NULL DEFAULT 'MONTHLY' COMMENT 'MONTHLY|YEARLY',
    short_url                 VARCHAR(500)           COMMENT 'Razorpay-generated payment link',
    current_start             DATETIME(6),
    current_end               DATETIME(6),
    charge_at                 DATETIME(6),
    next_charge_at            DATETIME(6),
    total_count               INT          NOT NULL DEFAULT 0,
    paid_count                INT          NOT NULL DEFAULT 0,
    remaining_count           INT          NOT NULL DEFAULT 0,
    auth_attempts             INT                   DEFAULT 0,
    failed_retry_count        INT                   DEFAULT 0,
    cancelled_at              DATETIME(6),
    paused_at                 DATETIME(6),
    ended_at                  DATETIME(6),
    cancel_at_cycle_end       TINYINT(1)            DEFAULT 0,
    last_webhook_event        VARCHAR(100),
    last_webhook_at           DATETIME(6),
    created_at                DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at                DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    INDEX idx_rzp_sub_shop_id        (shop_id),
    INDEX idx_rzp_sub_status         (status),
    INDEX idx_rzp_sub_created_at     (shop_id, created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Razorpay subscription mandate orders';

-- ---------------------------------------------------------------------------
-- 3. razorpay_payment_log
--    Immutable record of every payment event (success or failure).
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS razorpay_payment_log (
    id                        BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    shop_id                   BIGINT          NOT NULL COMMENT 'VyaparSathi shop FK',
    razorpay_subscription_id  VARCHAR(100)    NOT NULL COMMENT 'sub_XXXX',
    razorpay_payment_id       VARCHAR(100)    NOT NULL UNIQUE COMMENT 'pay_XXXX (idempotency key)',
    razorpay_signature        VARCHAR(500),
    razorpay_invoice_id       VARCHAR(100)    COMMENT 'inv_XXXX',
    razorpay_order_id         VARCHAR(100)    COMMENT 'order_XXXX',
    amount                    DECIMAL(10, 2)  NOT NULL COMMENT 'Amount in INR (not paise)',
    currency                  VARCHAR(10)     NOT NULL DEFAULT 'INR',
    status                    VARCHAR(50)     NOT NULL COMMENT 'SUCCESS|FAILED|REFUNDED',
    invoice_status            VARCHAR(50)              COMMENT 'PAID|UNPAID',
    method                    VARCHAR(50)              COMMENT 'card|upi|netbanking|emandate',
    card_id                   VARCHAR(100),
    bank                      VARCHAR(50),
    vpa                       VARCHAR(100)    COMMENT 'UPI VPA',
    error_code                VARCHAR(100),
    error_description         TEXT,
    created_at                DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_rzp_pay_shop_id    (shop_id),
    INDEX idx_rzp_pay_sub_id     (razorpay_subscription_id),
    INDEX idx_rzp_pay_created_at (shop_id, created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Immutable Razorpay payment event log';

-- ---------------------------------------------------------------------------
-- 4. razorpay_webhook_event
--    Audit log and idempotency store for all incoming webhook deliveries.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS razorpay_webhook_event (
    id                  BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    event_id            VARCHAR(100) NOT NULL UNIQUE COMMENT 'Razorpay event ID (idempotency key)',
    event_type          VARCHAR(100) NOT NULL COMMENT 'e.g. subscription.charged',
    payload             LONGTEXT     NOT NULL COMMENT 'Full raw JSON as received',
    status              VARCHAR(30)  NOT NULL DEFAULT 'RECEIVED' COMMENT 'RECEIVED|PROCESSED|FAILED|IGNORED',
    attempts            INT                   DEFAULT 1,
    signature_verified  TINYINT(1)            DEFAULT 0,
    http_status         INT                   DEFAULT 200,
    error_message       TEXT,
    processed_at        DATETIME(6),
    created_at          DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_webhook_status     (status),
    INDEX idx_webhook_event_type (event_type),
    INDEX idx_webhook_created_at (created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Razorpay webhook delivery audit log and idempotency store';
