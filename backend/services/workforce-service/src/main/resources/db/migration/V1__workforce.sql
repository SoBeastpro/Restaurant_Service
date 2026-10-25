CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION prevent_history_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION '% is append-only', TG_TABLE_NAME;
END;
$$;

CREATE TABLE restaurant_roles (
    code VARCHAR(30) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    hierarchy_level SMALLINT NOT NULL UNIQUE,
    CONSTRAINT restaurant_roles_code_check CHECK (code IN ('EMPLOYEE', 'MANAGER', 'DIRECTOR'))
);

CREATE TABLE permissions (
    code VARCHAR(100) PRIMARY KEY,
    domain VARCHAR(30) NOT NULL,
    name VARCHAR(150) NOT NULL,
    description VARCHAR(500),
    is_individually_assignable BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT permissions_domain_check CHECK (
        domain IN ('RESTAURANT', 'WORKFORCE', 'OPERATIONS', 'BILLING', 'AUDIT')
    )
);

CREATE TABLE restaurant_role_permissions (
    role_code VARCHAR(30) NOT NULL REFERENCES restaurant_roles(code) ON DELETE CASCADE,
    permission_code VARCHAR(100) NOT NULL REFERENCES permissions(code) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (role_code, permission_code)
);

CREATE TABLE restaurant_members (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    restaurant_id UUID NOT NULL,
    user_id UUID,
    role_code VARCHAR(30) NOT NULL REFERENCES restaurant_roles(code) ON DELETE RESTRICT,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    job_title VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'INVITED',
    last_active_at TIMESTAMPTZ,
    blocked_at TIMESTAMPTZ,
    archived_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT restaurant_members_id_restaurant_key UNIQUE (id, restaurant_id),
    CONSTRAINT restaurant_members_first_name_not_blank_check CHECK (BTRIM(first_name) <> ''),
    CONSTRAINT restaurant_members_last_name_not_blank_check CHECK (BTRIM(last_name) <> ''),
    CONSTRAINT restaurant_members_status_check CHECK (
        status IN ('INVITED', 'ACTIVE', 'INACTIVE', 'BLOCKED', 'ARCHIVED')
    ),
    CONSTRAINT restaurant_members_blocked_at_check CHECK (status <> 'BLOCKED' OR blocked_at IS NOT NULL),
    CONSTRAINT restaurant_members_archived_at_check CHECK (status <> 'ARCHIVED' OR archived_at IS NOT NULL)
);

CREATE UNIQUE INDEX restaurant_members_user_restaurant_key
    ON restaurant_members (user_id, restaurant_id) WHERE user_id IS NOT NULL;
CREATE UNIQUE INDEX restaurant_members_one_director_key
    ON restaurant_members (restaurant_id)
    WHERE role_code = 'DIRECTOR' AND status <> 'ARCHIVED';
CREATE INDEX restaurant_members_restaurant_status_idx
    ON restaurant_members (restaurant_id, status);

CREATE TABLE member_permission_overrides (
    membership_id UUID NOT NULL REFERENCES restaurant_members(id) ON DELETE CASCADE,
    permission_code VARCHAR(100) NOT NULL REFERENCES permissions(code) ON DELETE CASCADE,
    effect VARCHAR(10) NOT NULL,
    changed_by_user_id UUID NOT NULL,
    reason VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (membership_id, permission_code),
    CONSTRAINT member_permission_overrides_effect_check CHECK (effect IN ('GRANT', 'DENY'))
);

CREATE TABLE member_permission_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    membership_id UUID NOT NULL REFERENCES restaurant_members(id) ON DELETE RESTRICT,
    permission_code VARCHAR(100) NOT NULL REFERENCES permissions(code) ON DELETE RESTRICT,
    old_effect VARCHAR(10),
    new_effect VARCHAR(10),
    changed_by_user_id UUID NOT NULL,
    reason VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT member_permission_history_old_effect_check CHECK (
        old_effect IS NULL OR old_effect IN ('GRANT', 'DENY')
    ),
    CONSTRAINT member_permission_history_new_effect_check CHECK (
        new_effect IS NULL OR new_effect IN ('GRANT', 'DENY')
    ),
    CONSTRAINT member_permission_history_change_check CHECK (old_effect IS DISTINCT FROM new_effect)
);

CREATE INDEX member_permission_history_member_created_idx
    ON member_permission_history (membership_id, created_at DESC);

CREATE TABLE invitations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    restaurant_id UUID NOT NULL,
    membership_id UUID NOT NULL,
    created_by_membership_id UUID NOT NULL,
    accepted_by_user_id UUID,
    replaces_invitation_id UUID REFERENCES invitations(id) ON DELETE SET NULL,
    email VARCHAR(320),
    role_code VARCHAR(30) NOT NULL REFERENCES restaurant_roles(code) ON DELETE RESTRICT,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    accepted_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    CONSTRAINT invitations_membership_restaurant_fk FOREIGN KEY (membership_id, restaurant_id)
        REFERENCES restaurant_members(id, restaurant_id) ON DELETE RESTRICT,
    CONSTRAINT invitations_creator_restaurant_fk FOREIGN KEY (created_by_membership_id, restaurant_id)
        REFERENCES restaurant_members(id, restaurant_id) ON DELETE RESTRICT,
    CONSTRAINT invitations_token_hash_length_check CHECK (LENGTH(token_hash) = 64),
    CONSTRAINT invitations_status_check CHECK (status IN ('PENDING', 'ACCEPTED', 'EXPIRED', 'REVOKED')),
    CONSTRAINT invitations_expiration_check CHECK (expires_at > created_at),
    CONSTRAINT invitations_acceptance_check CHECK (
        status <> 'ACCEPTED' OR (accepted_at IS NOT NULL AND accepted_by_user_id IS NOT NULL)
    ),
    CONSTRAINT invitations_revocation_check CHECK (status <> 'REVOKED' OR revoked_at IS NOT NULL)
);

CREATE UNIQUE INDEX invitations_one_pending_per_member_key
    ON invitations (membership_id) WHERE status = 'PENDING';
CREATE INDEX invitations_restaurant_status_created_idx
    ON invitations (restaurant_id, status, created_at DESC);
CREATE INDEX invitations_pending_expiration_idx
    ON invitations (expires_at) WHERE status = 'PENDING';

CREATE TABLE outbox_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(150) NOT NULL,
    event_version INTEGER NOT NULL DEFAULT 1,
    payload JSONB NOT NULL,
    headers JSONB NOT NULL DEFAULT '{}'::JSONB,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    available_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMPTZ,
    attempts_count INTEGER NOT NULL DEFAULT 0,
    last_error VARCHAR(2000),
    CONSTRAINT outbox_payload_object_check CHECK (jsonb_typeof(payload) = 'object'),
    CONSTRAINT outbox_headers_object_check CHECK (jsonb_typeof(headers) = 'object'),
    CONSTRAINT outbox_attempts_check CHECK (attempts_count >= 0)
);

CREATE INDEX outbox_unpublished_idx
    ON outbox_events (available_at, occurred_at) WHERE published_at IS NULL;

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

CREATE OR REPLACE VIEW effective_member_permissions AS
SELECT permission_set.membership_id, permission_set.permission_code
FROM (
    SELECT member.id AS membership_id, role_permission.permission_code
    FROM restaurant_members member
    JOIN restaurant_role_permissions role_permission ON role_permission.role_code = member.role_code
    WHERE member.status IN ('ACTIVE', 'INACTIVE')
      AND NOT EXISTS (
          SELECT 1
          FROM member_permission_overrides override_permission
          WHERE override_permission.membership_id = member.id
            AND override_permission.permission_code = role_permission.permission_code
            AND override_permission.effect = 'DENY'
      )
    UNION
    SELECT override_permission.membership_id, override_permission.permission_code
    FROM member_permission_overrides override_permission
    JOIN restaurant_members member ON member.id = override_permission.membership_id
    WHERE override_permission.effect = 'GRANT'
      AND member.status IN ('ACTIVE', 'INACTIVE')
) permission_set;

CREATE OR REPLACE FUNCTION validate_permission_override()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM permissions
        WHERE code = NEW.permission_code
          AND is_individually_assignable
    ) THEN
        RAISE EXCEPTION 'Permission % cannot be assigned individually', NEW.permission_code;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER restaurant_members_set_updated_at
BEFORE UPDATE ON restaurant_members
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER member_permission_overrides_set_updated_at
BEFORE UPDATE ON member_permission_overrides
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER member_permission_overrides_validate
BEFORE INSERT OR UPDATE OF permission_code ON member_permission_overrides
FOR EACH ROW EXECUTE FUNCTION validate_permission_override();

CREATE TRIGGER member_permission_history_append_only
BEFORE UPDATE OR DELETE ON member_permission_history
FOR EACH ROW EXECUTE FUNCTION prevent_history_mutation();

INSERT INTO restaurant_roles (code, name, description, hierarchy_level) VALUES
    ('EMPLOYEE', 'Сотрудник', 'Исполнитель задач ресторана', 10),
    ('MANAGER', 'Менеджер', 'Управляет операционной работой ресторана', 20),
    ('DIRECTOR', 'Директор', 'Директор ресторана', 30);

INSERT INTO permissions (code, domain, name, is_individually_assignable) VALUES
    ('RESTAURANT_VIEW', 'RESTAURANT', 'Просмотр ресторана', TRUE),
    ('RESTAURANT_EDIT', 'RESTAURANT', 'Редактирование ресторана', TRUE),
    ('RESTAURANT_ARCHIVE', 'RESTAURANT', 'Архивирование ресторана', TRUE),
    ('HALL_VIEW', 'RESTAURANT', 'Просмотр залов', TRUE),
    ('HALL_MANAGE', 'RESTAURANT', 'Управление залами', TRUE),
    ('TABLE_VIEW', 'RESTAURANT', 'Просмотр столов', TRUE),
    ('TABLE_MANAGE', 'RESTAURANT', 'Управление столами', TRUE),
    ('TABLE_STATUS_SET_READY', 'RESTAURANT', 'Установка состояния стола READY', TRUE),
    ('TABLE_STATUS_SET_NEEDS_CLEANING', 'RESTAURANT', 'Установка состояния NEEDS_CLEANING', TRUE),
    ('TABLE_STATUS_SET_NEEDS_SERVING', 'RESTAURANT', 'Установка состояния NEEDS_SERVING', TRUE),
    ('TABLE_STATUS_SET_PROBLEM', 'RESTAURANT', 'Установка состояния PROBLEM', TRUE),
    ('TABLE_STATUS_HISTORY_VIEW', 'RESTAURANT', 'Просмотр истории столов', TRUE),
    ('MEMBER_VIEW', 'WORKFORCE', 'Просмотр сотрудников', TRUE),
    ('MEMBER_MANAGE_EMPLOYEE', 'WORKFORCE', 'Управление сотрудниками', TRUE),
    ('MEMBER_MANAGE_MANAGER', 'WORKFORCE', 'Управление менеджерами', TRUE),
    ('INVITATION_MANAGE_EMPLOYEE', 'WORKFORCE', 'Приглашение сотрудников', TRUE),
    ('INVITATION_MANAGE_ALL', 'WORKFORCE', 'Приглашение сотрудников и менеджеров', TRUE),
    ('MEMBER_PERMISSION_MANAGE_EMPLOYEE', 'WORKFORCE', 'Изменение прав сотрудников', FALSE),
    ('MEMBER_PERMISSION_MANAGE_MANAGER', 'WORKFORCE', 'Изменение прав менеджеров', FALSE),
    ('SHIFT_VIEW_SELF', 'OPERATIONS', 'Просмотр своих смен', TRUE),
    ('SHIFT_VIEW_ALL', 'OPERATIONS', 'Просмотр всех смен', TRUE),
    ('SHIFT_MANAGE', 'OPERATIONS', 'Управление сменами', TRUE),
    ('SHIFT_SELF_COMPLETE', 'OPERATIONS', 'Личное завершение смены', TRUE),
    ('SHIFT_REPORT_VIEW', 'OPERATIONS', 'Просмотр отчётов смен', TRUE),
    ('TASK_VIEW_OWN', 'OPERATIONS', 'Просмотр своих задач', TRUE),
    ('TASK_VIEW_ALL', 'OPERATIONS', 'Просмотр всех задач', TRUE),
    ('TASK_EXECUTE', 'OPERATIONS', 'Выполнение задач', TRUE),
    ('TASK_MANAGE', 'OPERATIONS', 'Управление задачами', TRUE),
    ('TASK_TEMPLATE_MANAGE', 'OPERATIONS', 'Управление шаблонами', TRUE),
    ('TASK_CARRY_OVER', 'OPERATIONS', 'Перенос задач', TRUE),
    ('DASHBOARD_VIEW_SELF', 'OPERATIONS', 'Персональный дашборд', TRUE),
    ('DASHBOARD_VIEW_RESTAURANT', 'OPERATIONS', 'Дашборд ресторана', TRUE),
    ('STATISTICS_VIEW_RESTAURANT', 'OPERATIONS', 'Статистика ресторана', TRUE),
    ('SUBSCRIPTION_VIEW', 'BILLING', 'Просмотр подписки', TRUE),
    ('SUBSCRIPTION_ACTIVATE', 'BILLING', 'Активация подписки', TRUE),
    ('AUDIT_VIEW_SELF', 'AUDIT', 'Просмотр своих действий', TRUE),
    ('AUDIT_VIEW_RESTAURANT', 'AUDIT', 'Просмотр аудита ресторана', TRUE);

INSERT INTO restaurant_role_permissions (role_code, permission_code) VALUES
    ('EMPLOYEE', 'RESTAURANT_VIEW'),
    ('EMPLOYEE', 'TABLE_VIEW'),
    ('EMPLOYEE', 'SHIFT_VIEW_SELF'),
    ('EMPLOYEE', 'SHIFT_SELF_COMPLETE'),
    ('EMPLOYEE', 'TASK_VIEW_OWN'),
    ('EMPLOYEE', 'TASK_EXECUTE'),
    ('EMPLOYEE', 'DASHBOARD_VIEW_SELF'),
    ('EMPLOYEE', 'AUDIT_VIEW_SELF');

INSERT INTO restaurant_role_permissions (role_code, permission_code)
SELECT 'MANAGER', code
FROM permissions
WHERE code NOT IN (
    'RESTAURANT_EDIT',
    'RESTAURANT_ARCHIVE',
    'HALL_MANAGE',
    'TABLE_MANAGE',
    'MEMBER_MANAGE_MANAGER',
    'INVITATION_MANAGE_ALL',
    'MEMBER_PERMISSION_MANAGE_MANAGER',
    'SUBSCRIPTION_VIEW',
    'SUBSCRIPTION_ACTIVATE'
);

INSERT INTO restaurant_role_permissions (role_code, permission_code)
SELECT 'DIRECTOR', code FROM permissions;

COMMENT ON COLUMN restaurant_members.restaurant_id IS
    'External restaurant-service UUID; no cross-database FK.';
COMMENT ON COLUMN restaurant_members.user_id IS
    'External identity-service UUID; nullable until invitation acceptance.';
