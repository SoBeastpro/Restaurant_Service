CREATE TABLE shift_table_assignments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    restaurant_id UUID NOT NULL,
    shift_id UUID NOT NULL,
    table_id UUID NOT NULL,
    membership_id UUID NOT NULL,
    assigned_by_membership_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT shift_table_assignments_shift_table_key UNIQUE (shift_id, table_id),
    CONSTRAINT shift_table_assignments_shift_restaurant_fk FOREIGN KEY (shift_id, restaurant_id)
        REFERENCES shifts(id, restaurant_id) ON DELETE CASCADE,
    CONSTRAINT shift_table_assignments_participant_fk FOREIGN KEY (shift_id, membership_id)
        REFERENCES shift_participants(shift_id, membership_id) ON DELETE CASCADE
);

CREATE INDEX shift_table_assignments_member_idx
    ON shift_table_assignments (shift_id, membership_id);

COMMENT ON CONSTRAINT shift_table_assignments_shift_table_key ON shift_table_assignments IS
    'Within one shift a table can be assigned to exactly one employee.';
COMMENT ON COLUMN shift_table_assignments.table_id IS
    'External restaurant-service table UUID.';

ALTER TABLE tasks
    ADD COLUMN deadline_type VARCHAR(20) NOT NULL DEFAULT 'NONE';

UPDATE tasks
SET deadline_type = 'TIME'
WHERE due_at IS NOT NULL;

ALTER TABLE tasks
    ADD CONSTRAINT tasks_deadline_type_check
        CHECK (deadline_type IN ('NONE', 'TIME', 'SHIFT_END')),
    ADD CONSTRAINT tasks_deadline_consistency_check
        CHECK (
            (deadline_type = 'NONE' AND due_at IS NULL)
            OR (deadline_type IN ('TIME', 'SHIFT_END') AND due_at IS NOT NULL)
        );
