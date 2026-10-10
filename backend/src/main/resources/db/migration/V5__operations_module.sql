CREATE TABLE operations.shifts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    restaurant_id UUID NOT NULL
        REFERENCES restaurant.restaurants(id) ON DELETE RESTRICT,
    name VARCHAR(100) NOT NULL,
    scheduled_start_at TIMESTAMPTZ NOT NULL,
    scheduled_end_at TIMESTAMPTZ NOT NULL,
    actual_started_at TIMESTAMPTZ,
    actual_closed_at TIMESTAMPTZ,
    status VARCHAR(20) NOT NULL DEFAULT 'PLANNED',
    responsible_membership_id UUID NOT NULL,
    created_by_membership_id UUID NOT NULL,
    started_by_membership_id UUID,
    closed_by_membership_id UUID,
    close_comment VARCHAR(2000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT shifts_id_restaurant_key UNIQUE (id, restaurant_id),
    CONSTRAINT shifts_responsible_restaurant_fk
        FOREIGN KEY (responsible_membership_id, restaurant_id)
        REFERENCES workforce.restaurant_members(id, restaurant_id) ON DELETE RESTRICT,
    CONSTRAINT shifts_creator_restaurant_fk
        FOREIGN KEY (created_by_membership_id, restaurant_id)
        REFERENCES workforce.restaurant_members(id, restaurant_id) ON DELETE RESTRICT,
    CONSTRAINT shifts_starter_restaurant_fk
        FOREIGN KEY (started_by_membership_id, restaurant_id)
        REFERENCES workforce.restaurant_members(id, restaurant_id) ON DELETE RESTRICT,
    CONSTRAINT shifts_closer_restaurant_fk
        FOREIGN KEY (closed_by_membership_id, restaurant_id)
        REFERENCES workforce.restaurant_members(id, restaurant_id) ON DELETE RESTRICT,
    CONSTRAINT shifts_name_not_blank_check CHECK (BTRIM(name) <> ''),
    CONSTRAINT shifts_schedule_check CHECK (scheduled_end_at > scheduled_start_at),
    CONSTRAINT shifts_actual_period_check
        CHECK (actual_closed_at IS NULL OR (actual_started_at IS NOT NULL AND actual_closed_at >= actual_started_at)),
    CONSTRAINT shifts_status_check
        CHECK (status IN ('PLANNED', 'ACTIVE', 'CLOSED', 'CANCELLED')),
    CONSTRAINT shifts_active_timestamp_check
        CHECK (status <> 'ACTIVE' OR (actual_started_at IS NOT NULL AND started_by_membership_id IS NOT NULL)),
    CONSTRAINT shifts_closed_timestamp_check
        CHECK (status <> 'CLOSED' OR (
            actual_started_at IS NOT NULL
            AND actual_closed_at IS NOT NULL
            AND started_by_membership_id IS NOT NULL
            AND closed_by_membership_id IS NOT NULL
        ))
);

CREATE UNIQUE INDEX shifts_one_active_per_restaurant_key
    ON operations.shifts (restaurant_id) WHERE status = 'ACTIVE';
CREATE INDEX shifts_restaurant_schedule_idx
    ON operations.shifts (restaurant_id, scheduled_start_at DESC);
CREATE INDEX shifts_responsible_idx
    ON operations.shifts (responsible_membership_id, scheduled_start_at DESC);

CREATE TABLE operations.shift_participants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    restaurant_id UUID NOT NULL,
    shift_id UUID NOT NULL,
    membership_id UUID NOT NULL,
    added_by_membership_id UUID NOT NULL,
    role_in_shift VARCHAR(100),
    attendance_status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    checked_in_at TIMESTAMPTZ,
    checked_out_at TIMESTAMPTZ,
    self_completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT shift_participants_shift_member_key UNIQUE (shift_id, membership_id),
    CONSTRAINT shift_participants_shift_restaurant_fk
        FOREIGN KEY (shift_id, restaurant_id)
        REFERENCES operations.shifts(id, restaurant_id) ON DELETE CASCADE,
    CONSTRAINT shift_participants_member_restaurant_fk
        FOREIGN KEY (membership_id, restaurant_id)
        REFERENCES workforce.restaurant_members(id, restaurant_id) ON DELETE RESTRICT,
    CONSTRAINT shift_participants_adder_restaurant_fk
        FOREIGN KEY (added_by_membership_id, restaurant_id)
        REFERENCES workforce.restaurant_members(id, restaurant_id) ON DELETE RESTRICT,
    CONSTRAINT shift_participants_attendance_status_check
        CHECK (attendance_status IN ('SCHEDULED', 'CHECKED_IN', 'CHECKED_OUT', 'NO_SHOW')),
    CONSTRAINT shift_participants_check_in_check
        CHECK (attendance_status NOT IN ('CHECKED_IN', 'CHECKED_OUT') OR checked_in_at IS NOT NULL),
    CONSTRAINT shift_participants_check_out_check
        CHECK (attendance_status <> 'CHECKED_OUT' OR checked_out_at IS NOT NULL),
    CONSTRAINT shift_participants_period_check
        CHECK (checked_out_at IS NULL OR (checked_in_at IS NOT NULL AND checked_out_at >= checked_in_at)),
    CONSTRAINT shift_participants_self_complete_check
        CHECK (self_completed_at IS NULL OR checked_out_at IS NOT NULL)
);

CREATE INDEX shift_participants_member_idx
    ON operations.shift_participants (membership_id, shift_id);
CREATE INDEX shift_participants_shift_status_idx
    ON operations.shift_participants (shift_id, attendance_status);

CREATE TABLE operations.task_templates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    restaurant_id UUID NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    default_priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    created_by_membership_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT task_templates_id_restaurant_key UNIQUE (id, restaurant_id),
    CONSTRAINT task_templates_restaurant_fk
        FOREIGN KEY (restaurant_id)
        REFERENCES restaurant.restaurants(id) ON DELETE RESTRICT,
    CONSTRAINT task_templates_creator_restaurant_fk
        FOREIGN KEY (created_by_membership_id, restaurant_id)
        REFERENCES workforce.restaurant_members(id, restaurant_id) ON DELETE RESTRICT,
    CONSTRAINT task_templates_title_not_blank_check CHECK (BTRIM(title) <> ''),
    CONSTRAINT task_templates_priority_check
        CHECK (default_priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT')),
    CONSTRAINT task_templates_status_check CHECK (status IN ('ACTIVE', 'ARCHIVED'))
);

CREATE INDEX task_templates_restaurant_status_idx
    ON operations.task_templates (restaurant_id, status);

CREATE TABLE operations.tasks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    restaurant_id UUID NOT NULL,
    shift_id UUID,
    carried_from_shift_id UUID,
    template_id UUID,
    created_by_membership_id UUID NOT NULL,
    assigned_to_membership_id UUID,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'CREATED',
    priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    carry_over_state VARCHAR(20) NOT NULL DEFAULT 'NONE',
    due_at TIMESTAMPTZ,
    accepted_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    archived_at TIMESTAMPTZ,
    cancellation_reason VARCHAR(1000),
    carry_over_comment VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT tasks_id_restaurant_key UNIQUE (id, restaurant_id),
    CONSTRAINT tasks_restaurant_fk
        FOREIGN KEY (restaurant_id)
        REFERENCES restaurant.restaurants(id) ON DELETE RESTRICT,
    CONSTRAINT tasks_shift_restaurant_fk
        FOREIGN KEY (shift_id, restaurant_id)
        REFERENCES operations.shifts(id, restaurant_id) ON DELETE RESTRICT,
    CONSTRAINT tasks_carried_from_shift_restaurant_fk
        FOREIGN KEY (carried_from_shift_id, restaurant_id)
        REFERENCES operations.shifts(id, restaurant_id) ON DELETE RESTRICT,
    CONSTRAINT tasks_template_restaurant_fk
        FOREIGN KEY (template_id, restaurant_id)
        REFERENCES operations.task_templates(id, restaurant_id) ON DELETE RESTRICT,
    CONSTRAINT tasks_creator_restaurant_fk
        FOREIGN KEY (created_by_membership_id, restaurant_id)
        REFERENCES workforce.restaurant_members(id, restaurant_id) ON DELETE RESTRICT,
    CONSTRAINT tasks_assignee_restaurant_fk
        FOREIGN KEY (assigned_to_membership_id, restaurant_id)
        REFERENCES workforce.restaurant_members(id, restaurant_id) ON DELETE RESTRICT,
    CONSTRAINT tasks_title_not_blank_check CHECK (BTRIM(title) <> ''),
    CONSTRAINT tasks_status_check
        CHECK (status IN ('CREATED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT tasks_priority_check CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT')),
    CONSTRAINT tasks_carry_over_state_check
        CHECK (carry_over_state IN ('NONE', 'PENDING', 'ASSIGNED')),
    CONSTRAINT tasks_pending_carry_over_check
        CHECK (carry_over_state <> 'PENDING' OR (shift_id IS NULL AND assigned_to_membership_id IS NULL AND carried_from_shift_id IS NOT NULL)),
    CONSTRAINT tasks_accepted_at_check
        CHECK (status <> 'IN_PROGRESS' OR accepted_at IS NOT NULL),
    CONSTRAINT tasks_completed_at_check
        CHECK (status <> 'COMPLETED' OR completed_at IS NOT NULL),
    CONSTRAINT tasks_cancelled_at_check
        CHECK (status <> 'CANCELLED' OR (cancelled_at IS NOT NULL AND NULLIF(BTRIM(cancellation_reason), '') IS NOT NULL))
);

CREATE INDEX tasks_restaurant_status_idx
    ON operations.tasks (restaurant_id, status) WHERE archived_at IS NULL;
CREATE INDEX tasks_assignee_status_idx
    ON operations.tasks (assigned_to_membership_id, status) WHERE archived_at IS NULL;
CREATE INDEX tasks_shift_idx ON operations.tasks (shift_id);
CREATE INDEX tasks_pending_carry_over_idx
    ON operations.tasks (restaurant_id, created_at) WHERE carry_over_state = 'PENDING';
CREATE INDEX tasks_due_idx
    ON operations.tasks (due_at)
    WHERE status NOT IN ('COMPLETED', 'CANCELLED') AND due_at IS NOT NULL;

CREATE TABLE operations.task_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    restaurant_id UUID NOT NULL,
    task_id UUID NOT NULL,
    actor_membership_id UUID NOT NULL,
    event_type VARCHAR(30) NOT NULL,
    old_status VARCHAR(20),
    new_status VARCHAR(20),
    old_assignee_membership_id UUID,
    new_assignee_membership_id UUID,
    from_shift_id UUID,
    to_shift_id UUID,
    comment VARCHAR(1000),
    details JSONB NOT NULL DEFAULT '{}'::JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT task_history_task_restaurant_fk
        FOREIGN KEY (task_id, restaurant_id)
        REFERENCES operations.tasks(id, restaurant_id) ON DELETE RESTRICT,
    CONSTRAINT task_history_actor_restaurant_fk
        FOREIGN KEY (actor_membership_id, restaurant_id)
        REFERENCES workforce.restaurant_members(id, restaurant_id) ON DELETE RESTRICT,
    CONSTRAINT task_history_old_assignee_restaurant_fk
        FOREIGN KEY (old_assignee_membership_id, restaurant_id)
        REFERENCES workforce.restaurant_members(id, restaurant_id) ON DELETE RESTRICT,
    CONSTRAINT task_history_new_assignee_restaurant_fk
        FOREIGN KEY (new_assignee_membership_id, restaurant_id)
        REFERENCES workforce.restaurant_members(id, restaurant_id) ON DELETE RESTRICT,
    CONSTRAINT task_history_from_shift_restaurant_fk
        FOREIGN KEY (from_shift_id, restaurant_id)
        REFERENCES operations.shifts(id, restaurant_id) ON DELETE RESTRICT,
    CONSTRAINT task_history_to_shift_restaurant_fk
        FOREIGN KEY (to_shift_id, restaurant_id)
        REFERENCES operations.shifts(id, restaurant_id) ON DELETE RESTRICT,
    CONSTRAINT task_history_event_type_check
        CHECK (event_type IN ('CREATED', 'UPDATED', 'ASSIGNED', 'STATUS_CHANGED', 'CARRIED_OVER', 'CANCELLED', 'ARCHIVED')),
    CONSTRAINT task_history_old_status_check
        CHECK (old_status IS NULL OR old_status IN ('CREATED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT task_history_new_status_check
        CHECK (new_status IS NULL OR new_status IN ('CREATED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT task_history_details_object_check CHECK (jsonb_typeof(details) = 'object')
);

CREATE INDEX task_history_task_created_idx
    ON operations.task_history (task_id, created_at DESC);

CREATE TABLE operations.shift_task_resolutions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    restaurant_id UUID NOT NULL,
    shift_id UUID NOT NULL,
    task_id UUID NOT NULL,
    action VARCHAR(20) NOT NULL,
    decided_by_membership_id UUID NOT NULL,
    comment VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT shift_task_resolutions_shift_task_key UNIQUE (shift_id, task_id),
    CONSTRAINT shift_task_resolutions_shift_restaurant_fk
        FOREIGN KEY (shift_id, restaurant_id)
        REFERENCES operations.shifts(id, restaurant_id) ON DELETE RESTRICT,
    CONSTRAINT shift_task_resolutions_task_restaurant_fk
        FOREIGN KEY (task_id, restaurant_id)
        REFERENCES operations.tasks(id, restaurant_id) ON DELETE RESTRICT,
    CONSTRAINT shift_task_resolutions_decider_restaurant_fk
        FOREIGN KEY (decided_by_membership_id, restaurant_id)
        REFERENCES workforce.restaurant_members(id, restaurant_id) ON DELETE RESTRICT,
    CONSTRAINT shift_task_resolutions_action_check CHECK (action IN ('CARRY_OVER', 'CANCEL'))
);

CREATE TABLE operations.shift_reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    restaurant_id UUID NOT NULL,
    shift_id UUID NOT NULL,
    generated_by_membership_id UUID NOT NULL,
    summary TEXT,
    metrics JSONB NOT NULL DEFAULT '{}'::JSONB,
    generated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT shift_reports_shift_key UNIQUE (shift_id),
    CONSTRAINT shift_reports_shift_restaurant_fk
        FOREIGN KEY (shift_id, restaurant_id)
        REFERENCES operations.shifts(id, restaurant_id) ON DELETE RESTRICT,
    CONSTRAINT shift_reports_generator_restaurant_fk
        FOREIGN KEY (generated_by_membership_id, restaurant_id)
        REFERENCES workforce.restaurant_members(id, restaurant_id) ON DELETE RESTRICT,
    CONSTRAINT shift_reports_metrics_object_check CHECK (jsonb_typeof(metrics) = 'object')
);

CREATE TABLE operations.favorite_task_templates (
    membership_id UUID NOT NULL,
    restaurant_id UUID NOT NULL,
    template_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (membership_id, template_id),
    CONSTRAINT favorite_task_templates_member_restaurant_fk
        FOREIGN KEY (membership_id, restaurant_id)
        REFERENCES workforce.restaurant_members(id, restaurant_id) ON DELETE CASCADE,
    CONSTRAINT favorite_task_templates_template_restaurant_fk
        FOREIGN KEY (template_id, restaurant_id)
        REFERENCES operations.task_templates(id, restaurant_id) ON DELETE CASCADE
);

CREATE TRIGGER shifts_set_updated_at
    BEFORE UPDATE ON operations.shifts
    FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

CREATE TRIGGER shift_participants_set_updated_at
    BEFORE UPDATE ON operations.shift_participants
    FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

CREATE TRIGGER task_templates_set_updated_at
    BEFORE UPDATE ON operations.task_templates
    FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

CREATE TRIGGER tasks_set_updated_at
    BEFORE UPDATE ON operations.tasks
    FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

CREATE OR REPLACE FUNCTION operations.validate_shift_transition()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'INSERT' AND NEW.status <> 'PLANNED' THEN
        RAISE EXCEPTION 'A shift must be created with PLANNED status';
    END IF;

    IF TG_OP = 'UPDATE' AND NEW.status IS DISTINCT FROM OLD.status THEN
        IF NOT (
            (OLD.status = 'PLANNED' AND NEW.status IN ('ACTIVE', 'CANCELLED'))
            OR (OLD.status = 'ACTIVE' AND NEW.status = 'CLOSED')
        ) THEN
            RAISE EXCEPTION 'Invalid shift transition: % -> %', OLD.status, NEW.status;
        END IF;
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER shifts_validate_transition
    BEFORE INSERT OR UPDATE OF status ON operations.shifts
    FOR EACH ROW EXECUTE FUNCTION operations.validate_shift_transition();

CREATE OR REPLACE FUNCTION operations.validate_task_transition()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'INSERT' AND NEW.status <> 'CREATED' THEN
        RAISE EXCEPTION 'A task must be created with CREATED status';
    END IF;

    IF TG_OP = 'UPDATE' AND NEW.status IS DISTINCT FROM OLD.status THEN
        IF NOT (
            (OLD.status = 'CREATED' AND NEW.status IN ('IN_PROGRESS', 'COMPLETED', 'CANCELLED'))
            OR (OLD.status = 'IN_PROGRESS' AND NEW.status IN ('COMPLETED', 'CANCELLED'))
        ) THEN
            RAISE EXCEPTION 'Invalid task transition: % -> %', OLD.status, NEW.status;
        END IF;
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER tasks_validate_transition
    BEFORE INSERT OR UPDATE OF status ON operations.tasks
    FOR EACH ROW EXECUTE FUNCTION operations.validate_task_transition();

COMMENT ON COLUMN operations.tasks.due_at IS
    'Overdue is derived from due_at and status; it is intentionally not stored as a separate status.';
COMMENT ON COLUMN operations.tasks.carry_over_state IS
    'PENDING means the task waits unassigned for the next shift.';
