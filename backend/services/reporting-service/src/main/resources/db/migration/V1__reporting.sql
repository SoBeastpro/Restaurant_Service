CREATE EXTENSION IF NOT EXISTS pgcrypto;

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

CREATE INDEX employee_daily_metrics_member_date_idx ON employee_daily_metrics (membership_id, metric_date DESC);
CREATE INDEX table_daily_metrics_table_date_idx ON table_daily_metrics (table_id, metric_date DESC);

COMMENT ON TABLE restaurant_daily_metrics IS 'Eventually consistent read model populated only from domain events.';
