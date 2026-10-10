CREATE TABLE notification.notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES identity.users(id) ON DELETE CASCADE,
    restaurant_id UUID REFERENCES restaurant.restaurants(id) ON DELETE CASCADE,
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

CREATE INDEX notifications_user_unread_idx
    ON notification.notifications (user_id, created_at DESC) WHERE read_at IS NULL;
CREATE INDEX notifications_restaurant_created_idx
    ON notification.notifications (restaurant_id, created_at DESC)
    WHERE restaurant_id IS NOT NULL;

CREATE TABLE notification.push_registrations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES identity.users(id) ON DELETE CASCADE,
    device_id UUID NOT NULL,
    push_token VARCHAR(500) NOT NULL,
    provider VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_success_at TIMESTAMPTZ,
    last_failure_at TIMESTAMPTZ,
    CONSTRAINT push_registrations_device_user_fk
        FOREIGN KEY (device_id, user_id)
        REFERENCES identity.devices(id, user_id) ON DELETE CASCADE,
    CONSTRAINT push_registrations_token_key UNIQUE (push_token),
    CONSTRAINT push_registrations_device_key UNIQUE (device_id),
    CONSTRAINT push_registrations_provider_check CHECK (provider IN ('FCM', 'APNS', 'WEB_PUSH')),
    CONSTRAINT push_registrations_status_check CHECK (status IN ('ACTIVE', 'INVALID', 'REVOKED'))
);

CREATE INDEX push_registrations_user_status_idx
    ON notification.push_registrations (user_id, status);

CREATE TABLE notification.delivery_attempts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    notification_id UUID NOT NULL
        REFERENCES notification.notifications(id) ON DELETE CASCADE,
    push_registration_id UUID NOT NULL
        REFERENCES notification.push_registrations(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempt_number INTEGER NOT NULL DEFAULT 1,
    provider_message_id VARCHAR(255),
    error_message VARCHAR(2000),
    attempted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT delivery_attempts_attempt_key
        UNIQUE (notification_id, push_registration_id, attempt_number),
    CONSTRAINT delivery_attempts_status_check
        CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    CONSTRAINT delivery_attempts_number_check CHECK (attempt_number > 0)
);

CREATE INDEX delivery_attempts_pending_idx
    ON notification.delivery_attempts (attempted_at) WHERE status = 'PENDING';

CREATE TABLE notification.outbox_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    restaurant_id UUID REFERENCES restaurant.restaurants(id) ON DELETE CASCADE,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts_count INTEGER NOT NULL DEFAULT 0,
    available_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMPTZ,
    last_error VARCHAR(2000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT outbox_events_aggregate_type_not_blank_check CHECK (BTRIM(aggregate_type) <> ''),
    CONSTRAINT outbox_events_event_type_not_blank_check CHECK (BTRIM(event_type) <> ''),
    CONSTRAINT outbox_events_payload_object_check CHECK (jsonb_typeof(payload) = 'object'),
    CONSTRAINT outbox_events_status_check CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED')),
    CONSTRAINT outbox_events_attempts_check CHECK (attempts_count >= 0),
    CONSTRAINT outbox_events_published_at_check
        CHECK (status <> 'PUBLISHED' OR published_at IS NOT NULL)
);

CREATE INDEX outbox_events_pending_idx
    ON notification.outbox_events (available_at, created_at)
    WHERE status IN ('PENDING', 'FAILED');

CREATE TRIGGER push_registrations_set_updated_at
    BEFORE UPDATE ON notification.push_registrations
    FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

COMMENT ON TABLE notification.outbox_events IS
    'Transactional events for RabbitMQ/WebSocket delivery; mobile clients never connect to RabbitMQ.';
