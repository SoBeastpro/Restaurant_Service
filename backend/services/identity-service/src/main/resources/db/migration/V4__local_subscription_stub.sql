ALTER TABLE users
    ADD COLUMN subscription_status VARCHAR(20) NOT NULL DEFAULT 'NOT_STARTED',
    ADD COLUMN subscription_expires_at TIMESTAMPTZ;

ALTER TABLE users
    ADD CONSTRAINT users_subscription_status_check
        CHECK (subscription_status IN ('NOT_STARTED', 'ACTIVE', 'EXPIRED')),
    ADD CONSTRAINT users_subscription_expiration_check
        CHECK (
            (subscription_status = 'ACTIVE' AND subscription_expires_at IS NOT NULL)
            OR subscription_status <> 'ACTIVE'
        );

COMMENT ON COLUMN users.subscription_status IS
    'Local MVP onboarding stub. It is not connected to a payment provider.';
