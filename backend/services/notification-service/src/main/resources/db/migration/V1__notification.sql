CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
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

CREATE INDEX notifications_user_created_idx
    ON notifications (user_id, created_at DESC);
CREATE INDEX notifications_user_unread_idx
    ON notifications (user_id, created_at DESC) WHERE read_at IS NULL;

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

CREATE INDEX push_registrations_user_active_idx
    ON push_registrations (user_id) WHERE status = 'ACTIVE';

CREATE TABLE delivery_attempts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    notification_id UUID NOT NULL REFERENCES notifications(id) ON DELETE CASCADE,
    push_registration_id UUID NOT NULL REFERENCES push_registrations(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempt_number INTEGER NOT NULL DEFAULT 1,
    provider_message_id VARCHAR(255),
    error_message VARCHAR(2000),
    attempted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT delivery_attempts_attempt_key UNIQUE (
        notification_id,
        push_registration_id,
        attempt_number
    ),
    CONSTRAINT delivery_attempts_status_check CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    CONSTRAINT delivery_attempts_number_check CHECK (attempt_number > 0)
);

CREATE INDEX delivery_attempts_pending_idx
    ON delivery_attempts (attempted_at) WHERE status = 'PENDING';

CREATE TABLE outbox_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(150) NOT NULL,
    event_version INTEGER NOT NULL DEFAULT 1,
    payload JSONB NOT NULL,
    headers JSONB NOT NULL DEFAULT '{}'::JSONB,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    available_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMPTZ,
    attempts_count INTEGER NOT NULL DEFAULT 0,
    last_error VARCHAR(2000),
    CONSTRAINT outbox_payload_object_check CHECK (jsonb_typeof(payload) = 'object'),
    CONSTRAINT outbox_headers_object_check CHECK (jsonb_typeof(headers) = 'object'),
    CONSTRAINT outbox_attempts_check CHECK (attempts_count >= 0)
);

CREATE INDEX outbox_unpublished_idx
    ON outbox_events (available_at, occurred_at) WHERE published_at IS NULL;

CREATE TABLE inbox_messages (
    message_id UUID PRIMARY KEY,
    event_type VARCHAR(150) NOT NULL,
    producer VARCHAR(100) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMPTZ,
    error_message VARCHAR(2000)
);

CREATE INDEX inbox_unprocessed_idx
    ON inbox_messages (received_at) WHERE processed_at IS NULL;

CREATE TRIGGER push_registrations_set_updated_at
BEFORE UPDATE ON push_registrations
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON COLUMN notifications.user_id IS
    'External identity-service UUID; no cross-database FK.';
COMMENT ON COLUMN push_registrations.device_id IS
    'Stable identity-service device UUID.';
