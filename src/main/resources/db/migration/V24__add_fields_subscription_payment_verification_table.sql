ALTER TABLE subscriptions
    ADD COLUMN `used_trial` BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE payment_verifications
    ADD COLUMN `billing_cycle` VARCHAR(20);