CREATE TABLE identity.users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    login VARCHAR(100) NOT NULL,
    email VARCHAR(320),
    phone VARCHAR(32),
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
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
    CONSTRAINT users_status_check CHECK (status IN ('ACTIVE', 'BLOCKED', 'ARCHIVED'))
);

CREATE UNIQUE INDEX users_login_lower_key ON identity.users (LOWER(login));
CREATE UNIQUE INDEX users_email_lower_key
    ON identity.users (LOWER(email)) WHERE email IS NOT NULL;
CREATE UNIQUE INDEX users_phone_key
    ON identity.users (phone) WHERE phone IS NOT NULL;
CREATE INDEX users_status_idx ON identity.users (status);

CREATE TABLE identity.restaurant_roles (
    code VARCHAR(30) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    hierarchy_level SMALLINT NOT NULL,
    CONSTRAINT restaurant_roles_code_check
        CHECK (code IN ('EMPLOYEE', 'MANAGER', 'DIRECTOR')),
    CONSTRAINT restaurant_roles_hierarchy_level_key UNIQUE (hierarchy_level)
);

INSERT INTO identity.restaurant_roles (code, name, description, hierarchy_level)
VALUES
    ('EMPLOYEE', 'Сотрудник', 'Исполнитель задач ресторана', 10),
    ('MANAGER', 'Менеджер', 'Управляет операционной работой ресторана', 20),
    ('DIRECTOR', 'Директор', 'Единственный директор и владелец ресторана', 30);

CREATE TABLE identity.platform_roles (
    code VARCHAR(30) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    CONSTRAINT platform_roles_code_check CHECK (code = 'SYSTEM_ADMIN')
);

INSERT INTO identity.platform_roles (code, name, description)
VALUES ('SYSTEM_ADMIN', 'Системный администратор', 'Управляет платформой и директорами');

CREATE TABLE identity.permissions (
    code VARCHAR(100) PRIMARY KEY,
    module VARCHAR(30) NOT NULL,
    name VARCHAR(150) NOT NULL,
    description VARCHAR(500),
    is_individually_assignable BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT permissions_module_check
        CHECK (module IN ('IDENTITY', 'RESTAURANT', 'WORKFORCE', 'OPERATIONS', 'BILLING', 'NOTIFICATION', 'AUDIT', 'PLATFORM'))
);

CREATE TABLE identity.restaurant_role_permissions (
    role_code VARCHAR(30) NOT NULL
        REFERENCES identity.restaurant_roles(code) ON DELETE CASCADE,
    permission_code VARCHAR(100) NOT NULL
        REFERENCES identity.permissions(code) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (role_code, permission_code)
);

CREATE TABLE identity.platform_role_permissions (
    role_code VARCHAR(30) NOT NULL
        REFERENCES identity.platform_roles(code) ON DELETE CASCADE,
    permission_code VARCHAR(100) NOT NULL
        REFERENCES identity.permissions(code) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (role_code, permission_code)
);

CREATE TABLE identity.user_platform_roles (
    user_id UUID NOT NULL REFERENCES identity.users(id) ON DELETE CASCADE,
    role_code VARCHAR(30) NOT NULL
        REFERENCES identity.platform_roles(code) ON DELETE RESTRICT,
    assigned_by_user_id UUID REFERENCES identity.users(id) ON DELETE SET NULL,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, role_code)
);

CREATE TABLE identity.devices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES identity.users(id) ON DELETE CASCADE,
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

CREATE INDEX devices_user_last_active_idx
    ON identity.devices (user_id, last_active_at DESC);

CREATE TABLE identity.sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES identity.users(id) ON DELETE CASCADE,
    device_id UUID,
    refresh_token_hash VARCHAR(64) NOT NULL,
    ip_address INET,
    user_agent VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_active_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    revoke_reason VARCHAR(255),
    CONSTRAINT sessions_device_user_fk
        FOREIGN KEY (device_id, user_id)
        REFERENCES identity.devices(id, user_id) ON DELETE RESTRICT,
    CONSTRAINT sessions_refresh_token_hash_key UNIQUE (refresh_token_hash),
    CONSTRAINT sessions_token_hash_length_check CHECK (LENGTH(refresh_token_hash) = 64),
    CONSTRAINT sessions_expiration_check CHECK (expires_at > created_at),
    CONSTRAINT sessions_last_active_check CHECK (last_active_at >= created_at),
    CONSTRAINT sessions_revoke_check
        CHECK ((revoked_at IS NULL AND revoke_reason IS NULL) OR revoked_at IS NOT NULL)
);

CREATE INDEX sessions_user_active_idx
    ON identity.sessions (user_id, expires_at)
    WHERE revoked_at IS NULL;
CREATE INDEX sessions_expires_idx ON identity.sessions (expires_at);

CREATE TABLE identity.contact_verification_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES identity.users(id) ON DELETE CASCADE,
    contact_type VARCHAR(10) NOT NULL,
    destination VARCHAR(320) NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    CONSTRAINT contact_verification_tokens_hash_key UNIQUE (token_hash),
    CONSTRAINT contact_verification_tokens_type_check
        CHECK (contact_type IN ('EMAIL', 'PHONE')),
    CONSTRAINT contact_verification_tokens_hash_length_check CHECK (LENGTH(token_hash) = 64),
    CONSTRAINT contact_verification_tokens_expiration_check CHECK (expires_at > created_at)
);

CREATE INDEX contact_verification_tokens_user_idx
    ON identity.contact_verification_tokens (user_id, contact_type, expires_at DESC);

CREATE TABLE identity.password_reset_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES identity.users(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    CONSTRAINT password_reset_tokens_hash_key UNIQUE (token_hash),
    CONSTRAINT password_reset_tokens_hash_length_check CHECK (LENGTH(token_hash) = 64),
    CONSTRAINT password_reset_tokens_expiration_check CHECK (expires_at > created_at)
);

CREATE INDEX password_reset_tokens_user_idx
    ON identity.password_reset_tokens (user_id, expires_at DESC);

CREATE TRIGGER users_set_updated_at
    BEFORE UPDATE ON identity.users
    FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

COMMENT ON TABLE identity.users IS 'Global account. Restaurant roles are stored only in workforce.restaurant_members.';
COMMENT ON TABLE identity.user_platform_roles IS 'Global platform roles; currently only SYSTEM_ADMIN.';
COMMENT ON COLUMN identity.sessions.refresh_token_hash IS 'SHA-256 hash; raw refresh tokens are never persisted.';
