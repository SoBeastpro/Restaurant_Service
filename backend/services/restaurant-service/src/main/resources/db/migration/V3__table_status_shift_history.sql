ALTER TABLE table_status_history
    ADD COLUMN shift_id UUID;

CREATE INDEX table_status_history_shift_created_idx
    ON table_status_history (shift_id, created_at DESC)
    WHERE shift_id IS NOT NULL;

COMMENT ON COLUMN table_status_history.shift_id IS
    'External operations-service shift UUID; intentionally has no cross-database FK.';
