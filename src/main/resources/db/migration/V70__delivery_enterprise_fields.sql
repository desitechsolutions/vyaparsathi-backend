-- Enterprise-grade delivery fields:
--  * Proof-of-delivery (POD): recipient name, signature/photo URLs, OTP, collected timestamp
--  * Cash-on-delivery (COD): amount, collected flag, collected-at
--  * Attempt tracking: count, last attempt, failure reason
--  * Logistics: tracking number, e-way bill, courier partner
--  * Planning: estimated delivery date
--  * Immutable address at dispatch time (snapshot), independent of the customer record
--
-- All new fields default nullable so legacy rows continue to load.

ALTER TABLE deliveries
    ADD COLUMN estimated_delivery_date DATE          NULL,
    ADD COLUMN delivery_address_snapshot TEXT        NULL,
    ADD COLUMN recipient_name          VARCHAR(255)  NULL,
    ADD COLUMN pod_signature_url       VARCHAR(1024) NULL,
    ADD COLUMN pod_photo_url           VARCHAR(1024) NULL,
    ADD COLUMN pod_otp                 VARCHAR(20)   NULL,
    ADD COLUMN pod_collected_at        DATETIME(6)   NULL,
    ADD COLUMN cod_amount              DECIMAL(12,2) NULL,
    ADD COLUMN cod_collected           BOOLEAN       NOT NULL DEFAULT FALSE,
    ADD COLUMN cod_collected_at        DATETIME(6)   NULL,
    ADD COLUMN attempt_count           INT           NOT NULL DEFAULT 0,
    ADD COLUMN last_attempt_at         DATETIME(6)   NULL,
    ADD COLUMN failure_reason          VARCHAR(500)  NULL,
    ADD COLUMN tracking_number         VARCHAR(100)  NULL,
    ADD COLUMN eway_bill_no            VARCHAR(50)   NULL,
    ADD COLUMN courier_partner         VARCHAR(100)  NULL;

CREATE INDEX idx_deliveries_status         ON deliveries(delivery_status);
CREATE INDEX idx_deliveries_shop_status    ON deliveries(shop_id, delivery_status);
CREATE INDEX idx_deliveries_shop_created   ON deliveries(shop_id, created_at);
CREATE INDEX idx_deliveries_person         ON deliveries(delivery_person_id);
CREATE INDEX idx_deliveries_tracking_no    ON deliveries(tracking_number);

-- Delivery-person enrichment for enterprise-grade person records
ALTER TABLE delivery_persons
    ADD COLUMN vehicle_number VARCHAR(50) NULL,
    ADD COLUMN license_number VARCHAR(50) NULL,
    ADD COLUMN employee_id    VARCHAR(50) NULL,
    ADD COLUMN active         BOOLEAN     NOT NULL DEFAULT TRUE;

-- Distinguish status-change history entries from reassignment audit entries.
-- Reassignments write a row with event_type='ASSIGNMENT' and note describing the change,
-- while regular status changes remain event_type='STATUS_CHANGE'.
ALTER TABLE delivery_status_history
    ADD COLUMN event_type VARCHAR(30) NOT NULL DEFAULT 'STATUS_CHANGE',
    ADD COLUMN note       VARCHAR(500) NULL;
