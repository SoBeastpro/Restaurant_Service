ALTER TABLE dining_tables
    DROP CONSTRAINT dining_tables_seats_check,
    ADD CONSTRAINT dining_tables_seats_check CHECK (seats_count BETWEEN 1 AND 12),
    ADD COLUMN rotation_degrees SMALLINT NOT NULL DEFAULT 0,
    ADD COLUMN is_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD CONSTRAINT dining_tables_rotation_check CHECK (rotation_degrees IN (0, 90));
