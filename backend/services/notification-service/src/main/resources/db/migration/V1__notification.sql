CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN NEW.updated_at = CURRENT_TIMESTAMP; RETURN NEW; END;
$$;

CREATE TABLE notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    restaurant_id UUID,
    type VARCHAR(50) NOT NULL,
    title VARCHAR(200) NOT NULL,
    message TEXT NOT NULL,
    payload JSONB NOT NULL DEFAULT '{}'::JSONB,
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT notifications_type_not_blank_check CHECK (BTRIM(type) <> ''),
    CONSTRAINT notifications_title_not_blank_check CHECK (BTRIM(title) <> ''),
    CONSTRAINT notifications_message_not_blank_check CHECK (BTRIM(message) <> ''),
    CONSTRAINT notifications_payload_object_check CHECK (jsonb_typeof(payload) = 'object')
);

CREATE INDEX notifications_user_unread_idx ON notifications (user_id, created_at DESC) WHERE read_at IS NULL;
CREATE INDEX notifications_restaurant_created_idx ON notifications (restaurant_id, created_at DESC) WHERE restaurant_id IS NOT NULL;

CREATE TABLE push_registrations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    device_id UUID NOT NULL,
    push_token VARCHAR(500) NOT NULL UNIQUE,
    provider VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_success_at TIMESTAMPTZ,
    last_failure_at TIMESTAMPTZ,
    CONSTRAINT push_registrations_device_key UNIQUE (device_id),
    CONSTRAINT push_registrations_provider_check CHECK (provider IN ('FCM', 'APNS', 'WEB_PUSH')),
    CONSTRAINT push_registrations_status_check CHECK (status IN ('ACTIVE', 'INVALID', 'REVOKED'))
);

CREATE INDEX push_registrations_user_status_idx ON push_registrations (user_id, status);

CREATE TABLE delivery_attempts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    notification_id UUID NOT NULL REFERENCES notifications(id) ON DELETE CASCADE,
    push_registration_id UUID NOT NULL REFERENCES push_registrations(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempt_number INTEGER NOT NULL DEFAULT 1,
    provider_message_id VARCHAR(255),
    error_message VARCHAR(2000),
    attempted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT delivery_attempts_attempt_key UNIQUE (notification_id, push_registration_id, attempt_number),
    CONSTRAINT delivery_attempts_status_check CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    CONSTRAINT delivery_attempts_number_check CHECK (attempt_number > 0)
);

CREATE INDEX delivery_attempts_pending_idx ON delivery_attempts (attempted_at) WHERE status = 'PENDING';
CREATE TRIGGER push_registrations_set_updated_at BEFORE UPDATE ON push_registrations FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON COLUMN notifications.user_id IS 'External identity-service UUID; no cross-database FK.';
COMMENT ON COLUMN push_registrations.device_id IS 'Stable mobile device UUID supplied by the client.';
