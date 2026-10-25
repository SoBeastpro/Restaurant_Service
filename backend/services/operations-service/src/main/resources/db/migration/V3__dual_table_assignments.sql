ALTER TABLE shift_table_assignments
    ADD COLUMN assignment_role VARCHAR(20) NOT NULL DEFAULT 'WAITER';

ALTER TABLE shift_table_assignments
    DROP CONSTRAINT shift_table_assignments_shift_table_key,
    ADD CONSTRAINT shift_table_assignments_role_check
        CHECK (assignment_role IN ('WAITER', 'RUNNER')),
    ADD CONSTRAINT shift_table_assignments_shift_table_role_key
        UNIQUE (shift_id, table_id, assignment_role);

CREATE INDEX shift_table_assignments_table_idx
    ON shift_table_assignments (shift_id, table_id, assignment_role);

COMMENT ON CONSTRAINT shift_table_assignments_shift_table_role_key ON shift_table_assignments IS
    'A table has at most one waiter and at most one runner within a shift.';
