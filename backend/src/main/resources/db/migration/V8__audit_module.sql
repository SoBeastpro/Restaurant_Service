CREATE TABLE audit.activity_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES identity.users(id) ON DELETE RESTRICT,
    membership_id UUID REFERENCES workforce.restaurant_members(id) ON DELETE RESTRICT,
    restaurant_id UUID REFERENCES restaurant.restaurants(id) ON DELETE RESTRICT,
    action VARCHAR(100) NOT NULL,
    entity_type VARCHAR(100),
    entity_id UUID,
    details JSONB NOT NULL DEFAULT '{}'::JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT activity_logs_action_not_blank_check CHECK (BTRIM(action) <> ''),
    CONSTRAINT activity_logs_details_object_check CHECK (jsonb_typeof(details) = 'object')
);

CREATE INDEX activity_logs_user_created_idx
    ON audit.activity_logs (user_id, created_at DESC);
CREATE INDEX activity_logs_restaurant_created_idx
    ON audit.activity_logs (restaurant_id, created_at DESC)
    WHERE restaurant_id IS NOT NULL;

CREATE TABLE audit.audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_user_id UUID REFERENCES identity.users(id) ON DELETE SET NULL,
    actor_membership_id UUID REFERENCES workforce.restaurant_members(id) ON DELETE SET NULL,
    subject_user_id UUID REFERENCES identity.users(id) ON DELETE SET NULL,
    restaurant_id UUID REFERENCES restaurant.restaurants(id) ON DELETE RESTRICT,
    scope VARCHAR(20) NOT NULL,
    action VARCHAR(100) NOT NULL,
    entity_type VARCHAR(100),
    entity_id UUID,
    outcome VARCHAR(20) NOT NULL DEFAULT 'SUCCESS',
    ip_address INET,
    user_agent VARCHAR(1000),
    details JSONB NOT NULL DEFAULT '{}'::JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT audit_logs_scope_check CHECK (scope IN ('PERSONAL', 'RESTAURANT', 'PLATFORM')),
    CONSTRAINT audit_logs_action_not_blank_check CHECK (BTRIM(action) <> ''),
    CONSTRAINT audit_logs_outcome_check CHECK (outcome IN ('SUCCESS', 'DENIED', 'FAILED')),
    CONSTRAINT audit_logs_details_object_check CHECK (jsonb_typeof(details) = 'object'),
    CONSTRAINT audit_logs_restaurant_scope_check
        CHECK (scope <> 'RESTAURANT' OR restaurant_id IS NOT NULL)
);

CREATE INDEX audit_logs_actor_created_idx
    ON audit.audit_logs (actor_user_id, created_at DESC);
CREATE INDEX audit_logs_subject_created_idx
    ON audit.audit_logs (subject_user_id, created_at DESC)
    WHERE subject_user_id IS NOT NULL;
CREATE INDEX audit_logs_restaurant_created_idx
    ON audit.audit_logs (restaurant_id, created_at DESC)
    WHERE restaurant_id IS NOT NULL;
CREATE INDEX audit_logs_scope_created_idx
    ON audit.audit_logs (scope, created_at DESC);
CREATE INDEX audit_logs_entity_idx
    ON audit.audit_logs (entity_type, entity_id, created_at DESC);

CREATE TABLE audit.system_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    level VARCHAR(20) NOT NULL,
    service VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    message TEXT NOT NULL,
    correlation_id VARCHAR(100),
    details JSONB NOT NULL DEFAULT '{}'::JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT system_events_level_check CHECK (level IN ('TRACE', 'DEBUG', 'INFO', 'WARN', 'ERROR')),
    CONSTRAINT system_events_service_not_blank_check CHECK (BTRIM(service) <> ''),
    CONSTRAINT system_events_type_not_blank_check CHECK (BTRIM(event_type) <> ''),
    CONSTRAINT system_events_message_not_blank_check CHECK (BTRIM(message) <> ''),
    CONSTRAINT system_events_details_object_check CHECK (jsonb_typeof(details) = 'object')
);

CREATE INDEX system_events_level_created_idx
    ON audit.system_events (level, created_at DESC);
CREATE INDEX system_events_correlation_idx
    ON audit.system_events (correlation_id) WHERE correlation_id IS NOT NULL;

CREATE OR REPLACE FUNCTION audit.prevent_history_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION '% is append-only', TG_TABLE_SCHEMA || '.' || TG_TABLE_NAME;
END;
$$;

CREATE TRIGGER audit_logs_append_only
    BEFORE UPDATE OR DELETE ON audit.audit_logs
    FOR EACH ROW EXECUTE FUNCTION audit.prevent_history_mutation();

CREATE TRIGGER activity_logs_append_only
    BEFORE UPDATE OR DELETE ON audit.activity_logs
    FOR EACH ROW EXECUTE FUNCTION audit.prevent_history_mutation();

CREATE TRIGGER system_events_append_only
    BEFORE UPDATE OR DELETE ON audit.system_events
    FOR EACH ROW EXECUTE FUNCTION audit.prevent_history_mutation();

CREATE TRIGGER task_history_append_only
    BEFORE UPDATE OR DELETE ON operations.task_history
    FOR EACH ROW EXECUTE FUNCTION audit.prevent_history_mutation();

CREATE TRIGGER table_status_history_append_only
    BEFORE UPDATE OR DELETE ON restaurant.table_status_history
    FOR EACH ROW EXECUTE FUNCTION audit.prevent_history_mutation();

CREATE TRIGGER member_permission_history_append_only
    BEFORE UPDATE OR DELETE ON workforce.member_permission_history
    FOR EACH ROW EXECUTE FUNCTION audit.prevent_history_mutation();

COMMENT ON TABLE audit.audit_logs IS
    'Immutable security/business audit with explicit PERSONAL, RESTAURANT or PLATFORM scope.';
