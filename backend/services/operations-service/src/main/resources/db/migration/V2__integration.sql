CREATE TABLE outbox_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(), aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id UUID NOT NULL, event_type VARCHAR(150) NOT NULL, event_version INTEGER NOT NULL DEFAULT 1,
    payload JSONB NOT NULL, headers JSONB NOT NULL DEFAULT '{}'::JSONB,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    available_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, published_at TIMESTAMPTZ,
    attempts_count INTEGER NOT NULL DEFAULT 0, last_error VARCHAR(2000),
    CONSTRAINT outbox_payload_object_check CHECK (jsonb_typeof(payload) = 'object'),
    CONSTRAINT outbox_headers_object_check CHECK (jsonb_typeof(headers) = 'object'),
    CONSTRAINT outbox_attempts_check CHECK (attempts_count >= 0)
);
CREATE INDEX outbox_unpublished_idx ON outbox_events (available_at, occurred_at) WHERE published_at IS NULL;
CREATE TABLE inbox_messages (
    message_id UUID PRIMARY KEY, event_type VARCHAR(150) NOT NULL, producer VARCHAR(100) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, processed_at TIMESTAMPTZ,
    error_message VARCHAR(2000)
);
