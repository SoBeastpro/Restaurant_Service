CREATE TABLE inbox_messages (
    message_id UUID PRIMARY KEY, event_type VARCHAR(150) NOT NULL, producer VARCHAR(100) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, processed_at TIMESTAMPTZ,
    error_message VARCHAR(2000)
);
