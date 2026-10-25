CREATE TABLE restaurant_daily_metrics (
    restaurant_id UUID NOT NULL,
    metric_date DATE NOT NULL,
    shifts_opened INTEGER NOT NULL DEFAULT 0,
    shifts_closed INTEGER NOT NULL DEFAULT 0,
    tasks_created INTEGER NOT NULL DEFAULT 0,
    tasks_completed INTEGER NOT NULL DEFAULT 0,
    tasks_cancelled INTEGER NOT NULL DEFAULT 0,
    tasks_overdue INTEGER NOT NULL DEFAULT 0,
    table_status_changes INTEGER NOT NULL DEFAULT 0,
    active_members INTEGER NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (restaurant_id, metric_date)
);

CREATE TABLE employee_daily_metrics (
    restaurant_id UUID NOT NULL,
    membership_id UUID NOT NULL,
    metric_date DATE NOT NULL,
    shifts_count INTEGER NOT NULL DEFAULT 0,
    worked_minutes INTEGER NOT NULL DEFAULT 0,
    tasks_assigned INTEGER NOT NULL DEFAULT 0,
    tasks_completed INTEGER NOT NULL DEFAULT 0,
    tasks_overdue INTEGER NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (restaurant_id, membership_id, metric_date)
);

CREATE TABLE table_daily_metrics (
    restaurant_id UUID NOT NULL,
    table_id UUID NOT NULL,
    metric_date DATE NOT NULL,
    needs_cleaning_count INTEGER NOT NULL DEFAULT 0,
    needs_serving_count INTEGER NOT NULL DEFAULT 0,
    problem_count INTEGER NOT NULL DEFAULT 0,
    ready_count INTEGER NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (restaurant_id, table_id, metric_date)
);

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

COMMENT ON TABLE restaurant_daily_metrics IS
    'Eventually consistent read model populated only from domain events.';
