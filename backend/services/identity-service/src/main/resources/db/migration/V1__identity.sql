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

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    login VARCHAR(100) NOT NULL,
    email VARCHAR(320),
    phone VARCHAR(32),
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    role_code VARCHAR(30) NOT NULL,
    position_code VARCHAR(30),
    restaurant_id UUID,
    avatar_object_key VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    email_verified_at TIMESTAMPTZ,
    phone_verified_at TIMESTAMPTZ,
    last_login_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT users_login_not_blank_check CHECK (BTRIM(login) <> ''),
    CONSTRAINT users_first_name_not_blank_check CHECK (BTRIM(first_name) <> ''),
    CONSTRAINT users_last_name_not_blank_check CHECK (BTRIM(last_name) <> ''),
    CONSTRAINT users_role_code_check CHECK (
        role_code IN ('SYSTEM_ADMIN', 'DIRECTOR', 'MANAGER', 'EMPLOYEE')
    ),
    CONSTRAINT users_position_code_check CHECK (
        position_code IS NULL OR position_code IN ('WAITER', 'RUNNER')
    ),
    CONSTRAINT users_employee_position_check CHECK (
        role_code <> 'EMPLOYEE' OR position_code IS NOT NULL
    ),
    CONSTRAINT users_status_check CHECK (status IN ('ACTIVE', 'BLOCKED', 'ARCHIVED'))
);

CREATE UNIQUE INDEX users_login_lower_key ON users (LOWER(login));
CREATE UNIQUE INDEX users_email_lower_key ON users (LOWER(email)) WHERE email IS NOT NULL;
CREATE UNIQUE INDEX users_phone_key ON users (phone) WHERE phone IS NOT NULL;

COMMENT ON COLUMN users.restaurant_id IS
    'External restaurant-service UUID. MVP rule: one account belongs to at most one restaurant.';

CREATE TABLE platform_roles (
    code VARCHAR(30) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500)
);

CREATE TABLE platform_permissions (
    code VARCHAR(100) PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    description VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE platform_role_permissions (
    role_code VARCHAR(30) NOT NULL REFERENCES platform_roles(code) ON DELETE CASCADE,
    permission_code VARCHAR(100) NOT NULL REFERENCES platform_permissions(code) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (role_code, permission_code)
);

CREATE TABLE user_platform_roles (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_code VARCHAR(30) NOT NULL REFERENCES platform_roles(code) ON DELETE RESTRICT,
    assigned_by_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, role_code)
);

CREATE TABLE devices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    device_uid VARCHAR(255) NOT NULL,
    device_name VARCHAR(255),
    platform VARCHAR(20) NOT NULL,
    app_version VARCHAR(50),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_active_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT devices_id_user_key UNIQUE (id, user_id),
    CONSTRAINT devices_user_device_key UNIQUE (user_id, device_uid),
    CONSTRAINT devices_platform_check CHECK (platform IN ('ANDROID', 'IOS', 'WEB')),
    CONSTRAINT devices_last_active_check CHECK (last_active_at >= created_at)
);

CREATE TABLE sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    device_id UUID,
    refresh_token_hash VARCHAR(64) NOT NULL UNIQUE,
    ip_address INET,
    user_agent VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_active_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    revoke_reason VARCHAR(255),
    CONSTRAINT sessions_device_user_fk FOREIGN KEY (device_id, user_id)
        REFERENCES devices(id, user_id) ON DELETE RESTRICT,
    CONSTRAINT sessions_token_hash_length_check CHECK (LENGTH(refresh_token_hash) = 64),
    CONSTRAINT sessions_expiration_check CHECK (expires_at > created_at),
    CONSTRAINT sessions_last_active_check CHECK (last_active_at >= created_at),
    CONSTRAINT sessions_revoke_check CHECK ((revoked_at IS NULL AND revoke_reason IS NULL) OR revoked_at IS NOT NULL)
);

CREATE INDEX sessions_user_active_idx
    ON sessions (user_id, created_at DESC) WHERE revoked_at IS NULL;
CREATE INDEX sessions_active_expiration_idx
    ON sessions (expires_at) WHERE revoked_at IS NULL;

CREATE TABLE contact_verification_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    contact_type VARCHAR(10) NOT NULL,
    destination VARCHAR(320) NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    CONSTRAINT contact_verification_tokens_type_check CHECK (contact_type IN ('EMAIL', 'PHONE')),
    CONSTRAINT contact_verification_tokens_hash_length_check CHECK (LENGTH(token_hash) = 64),
    CONSTRAINT contact_verification_tokens_expiration_check CHECK (expires_at > created_at)
);

CREATE TABLE password_reset_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    CONSTRAINT password_reset_tokens_hash_length_check CHECK (LENGTH(token_hash) = 64),
    CONSTRAINT password_reset_tokens_expiration_check CHECK (expires_at > created_at)
);

CREATE INDEX contact_verification_tokens_user_idx
    ON contact_verification_tokens (user_id, contact_type, created_at DESC) WHERE used_at IS NULL;
CREATE INDEX contact_verification_tokens_expiration_idx
    ON contact_verification_tokens (expires_at) WHERE used_at IS NULL;
CREATE INDEX password_reset_tokens_expiration_idx
    ON password_reset_tokens (expires_at) WHERE used_at IS NULL;

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

CREATE OR REPLACE VIEW effective_platform_permissions AS
SELECT user_role.user_id, role_permission.permission_code
FROM user_platform_roles user_role
JOIN platform_role_permissions role_permission ON role_permission.role_code = user_role.role_code
JOIN users user_row ON user_row.id = user_role.user_id
WHERE user_row.status = 'ACTIVE';

CREATE TRIGGER users_set_updated_at
BEFORE UPDATE ON users
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

INSERT INTO platform_roles (code, name, description) VALUES
    ('SYSTEM_ADMIN', 'Системный администратор', 'Управляет платформой');

INSERT INTO platform_permissions (code, name) VALUES
    ('PLATFORM_USER_MANAGE', 'Управление пользователями платформы'),
    ('PLATFORM_RESTAURANT_MANAGE', 'Управление ресторанами платформы'),
    ('PLATFORM_TARIFF_MANAGE', 'Управление тарифами'),
    ('PLATFORM_METRICS_VIEW', 'Просмотр метрик платформы'),
    ('PLATFORM_AUDIT_VIEW', 'Просмотр аудита платформы'),
    ('PLATFORM_SYSTEM_EVENTS_VIEW', 'Просмотр системных событий');

INSERT INTO platform_role_permissions (role_code, permission_code)
SELECT 'SYSTEM_ADMIN', code FROM platform_permissions;
