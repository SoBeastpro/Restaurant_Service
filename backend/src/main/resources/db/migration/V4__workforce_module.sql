CREATE TABLE workforce.restaurant_members (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    restaurant_id UUID NOT NULL,
    user_id UUID REFERENCES identity.users(id) ON DELETE RESTRICT,
    role_code VARCHAR(30) NOT NULL
        REFERENCES identity.restaurant_roles(code) ON DELETE RESTRICT,
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
    CONSTRAINT restaurant_members_restaurant_fk
        FOREIGN KEY (restaurant_id)
        REFERENCES restaurant.restaurants(id) ON DELETE RESTRICT
        DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT restaurant_members_first_name_not_blank_check CHECK (BTRIM(first_name) <> ''),
    CONSTRAINT restaurant_members_last_name_not_blank_check CHECK (BTRIM(last_name) <> ''),
    CONSTRAINT restaurant_members_status_check
        CHECK (status IN ('INVITED', 'ACTIVE', 'INACTIVE', 'BLOCKED', 'ARCHIVED')),
    CONSTRAINT restaurant_members_blocked_at_check
        CHECK (status <> 'BLOCKED' OR blocked_at IS NOT NULL),
    CONSTRAINT restaurant_members_archived_at_check
        CHECK (status <> 'ARCHIVED' OR archived_at IS NOT NULL)
);

CREATE UNIQUE INDEX restaurant_members_user_restaurant_key
    ON workforce.restaurant_members (user_id, restaurant_id)
    WHERE user_id IS NOT NULL;
CREATE UNIQUE INDEX restaurant_members_one_director_key
    ON workforce.restaurant_members (restaurant_id)
    WHERE role_code = 'DIRECTOR' AND status <> 'ARCHIVED';
CREATE INDEX restaurant_members_restaurant_status_idx
    ON workforce.restaurant_members (restaurant_id, status);
CREATE INDEX restaurant_members_user_idx
    ON workforce.restaurant_members (user_id) WHERE user_id IS NOT NULL;

ALTER TABLE restaurant.restaurants
    ADD CONSTRAINT restaurants_director_membership_fk
    FOREIGN KEY (director_membership_id, id)
    REFERENCES workforce.restaurant_members(id, restaurant_id)
    ON DELETE RESTRICT
    DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE restaurant.table_status_history
    ADD CONSTRAINT table_status_history_member_restaurant_fk
    FOREIGN KEY (changed_by_membership_id, restaurant_id)
    REFERENCES workforce.restaurant_members(id, restaurant_id)
    ON DELETE RESTRICT;

CREATE TABLE workforce.member_permission_overrides (
    membership_id UUID NOT NULL
        REFERENCES workforce.restaurant_members(id) ON DELETE CASCADE,
    permission_code VARCHAR(100) NOT NULL
        REFERENCES identity.permissions(code) ON DELETE CASCADE,
    effect VARCHAR(10) NOT NULL,
    changed_by_user_id UUID NOT NULL
        REFERENCES identity.users(id) ON DELETE RESTRICT,
    reason VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (membership_id, permission_code),
    CONSTRAINT member_permission_overrides_effect_check CHECK (effect IN ('GRANT', 'DENY'))
);

CREATE TABLE workforce.member_permission_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    membership_id UUID NOT NULL
        REFERENCES workforce.restaurant_members(id) ON DELETE RESTRICT,
    permission_code VARCHAR(100) NOT NULL
        REFERENCES identity.permissions(code) ON DELETE RESTRICT,
    old_effect VARCHAR(10),
    new_effect VARCHAR(10),
    changed_by_user_id UUID NOT NULL
        REFERENCES identity.users(id) ON DELETE RESTRICT,
    reason VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT member_permission_history_old_effect_check
        CHECK (old_effect IS NULL OR old_effect IN ('GRANT', 'DENY')),
    CONSTRAINT member_permission_history_new_effect_check
        CHECK (new_effect IS NULL OR new_effect IN ('GRANT', 'DENY')),
    CONSTRAINT member_permission_history_change_check
        CHECK (old_effect IS DISTINCT FROM new_effect)
);

CREATE INDEX member_permission_history_member_created_idx
    ON workforce.member_permission_history (membership_id, created_at DESC);

CREATE TABLE workforce.invitations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    restaurant_id UUID NOT NULL,
    membership_id UUID NOT NULL,
    created_by_membership_id UUID NOT NULL,
    accepted_by_user_id UUID REFERENCES identity.users(id) ON DELETE RESTRICT,
    replaces_invitation_id UUID REFERENCES workforce.invitations(id) ON DELETE SET NULL,
    email VARCHAR(320) NOT NULL,
    role_code VARCHAR(30) NOT NULL
        REFERENCES identity.restaurant_roles(code) ON DELETE RESTRICT,
    token_hash VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    accepted_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    CONSTRAINT invitations_membership_restaurant_fk
        FOREIGN KEY (membership_id, restaurant_id)
        REFERENCES workforce.restaurant_members(id, restaurant_id) ON DELETE RESTRICT,
    CONSTRAINT invitations_creator_restaurant_fk
        FOREIGN KEY (created_by_membership_id, restaurant_id)
        REFERENCES workforce.restaurant_members(id, restaurant_id) ON DELETE RESTRICT,
    CONSTRAINT invitations_token_hash_key UNIQUE (token_hash),
    CONSTRAINT invitations_token_hash_length_check CHECK (LENGTH(token_hash) = 64),
    CONSTRAINT invitations_email_not_blank_check CHECK (BTRIM(email) <> ''),
    CONSTRAINT invitations_status_check
        CHECK (status IN ('PENDING', 'ACCEPTED', 'EXPIRED', 'REVOKED')),
    CONSTRAINT invitations_expiration_check CHECK (expires_at > created_at),
    CONSTRAINT invitations_acceptance_check
        CHECK (status <> 'ACCEPTED' OR (accepted_at IS NOT NULL AND accepted_by_user_id IS NOT NULL)),
    CONSTRAINT invitations_revocation_check
        CHECK (status <> 'REVOKED' OR revoked_at IS NOT NULL)
);

CREATE UNIQUE INDEX invitations_one_pending_per_member_key
    ON workforce.invitations (membership_id) WHERE status = 'PENDING';
CREATE INDEX invitations_restaurant_status_idx
    ON workforce.invitations (restaurant_id, status);
CREATE INDEX invitations_email_lower_idx ON workforce.invitations (LOWER(email));
CREATE INDEX invitations_expires_idx
    ON workforce.invitations (expires_at) WHERE status = 'PENDING';

CREATE OR REPLACE VIEW workforce.effective_member_permissions AS
SELECT member_permissions.membership_id, member_permissions.permission_code
FROM (
    SELECT member.id AS membership_id, role_permission.permission_code
    FROM workforce.restaurant_members member
    JOIN identity.restaurant_role_permissions role_permission
      ON role_permission.role_code = member.role_code
    WHERE member.status IN ('ACTIVE', 'INACTIVE')
      AND NOT EXISTS (
          SELECT 1
          FROM workforce.member_permission_overrides override_permission
          WHERE override_permission.membership_id = member.id
            AND override_permission.permission_code = role_permission.permission_code
            AND override_permission.effect = 'DENY'
      )

    UNION

    SELECT override_permission.membership_id, override_permission.permission_code
    FROM workforce.member_permission_overrides override_permission
    JOIN workforce.restaurant_members member ON member.id = override_permission.membership_id
    WHERE override_permission.effect = 'GRANT'
      AND member.status IN ('ACTIVE', 'INACTIVE')
) member_permissions;

CREATE OR REPLACE FUNCTION workforce.validate_director_membership()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    target_membership_id UUID;
    target_restaurant_id UUID;
    target_role_code VARCHAR(30);
    target_status VARCHAR(20);
BEGIN
    IF TG_TABLE_SCHEMA = 'restaurant' THEN
        target_membership_id := NEW.director_membership_id;
        target_restaurant_id := NEW.id;

        SELECT role_code, status
        INTO target_role_code, target_status
        FROM workforce.restaurant_members
        WHERE id = target_membership_id
          AND restaurant_id = target_restaurant_id;

        IF target_role_code IS DISTINCT FROM 'DIRECTOR' OR target_status = 'ARCHIVED' THEN
            RAISE EXCEPTION 'Restaurant % must reference its DIRECTOR membership', target_restaurant_id;
        END IF;
    ELSE
        IF NEW.role_code = 'DIRECTOR' AND NEW.status <> 'ARCHIVED' AND NOT EXISTS (
            SELECT 1
            FROM restaurant.restaurants restaurant_row
            WHERE restaurant_row.id = NEW.restaurant_id
              AND restaurant_row.director_membership_id = NEW.id
        ) THEN
            RAISE EXCEPTION 'DIRECTOR membership % must be referenced by its restaurant', NEW.id;
        END IF;

        IF (NEW.role_code <> 'DIRECTOR' OR NEW.status = 'ARCHIVED') AND EXISTS (
            SELECT 1
            FROM restaurant.restaurants restaurant_row
            WHERE restaurant_row.director_membership_id = NEW.id
        ) THEN
            RAISE EXCEPTION 'A restaurant director membership cannot have role %', NEW.role_code;
        END IF;
    END IF;

    RETURN NEW;
END;
$$;

CREATE CONSTRAINT TRIGGER restaurants_validate_director
    AFTER INSERT OR UPDATE OF director_membership_id ON restaurant.restaurants
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION workforce.validate_director_membership();

CREATE CONSTRAINT TRIGGER restaurant_members_validate_director
    AFTER INSERT OR UPDATE ON workforce.restaurant_members
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION workforce.validate_director_membership();

CREATE TRIGGER restaurant_members_set_updated_at
    BEFORE UPDATE ON workforce.restaurant_members
    FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

CREATE TRIGGER member_permission_overrides_set_updated_at
    BEFORE UPDATE ON workforce.member_permission_overrides
    FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

CREATE OR REPLACE FUNCTION workforce.validate_permission_override()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM identity.permissions permission
        WHERE permission.code = NEW.permission_code
          AND permission.is_individually_assignable = TRUE
    ) THEN
        RAISE EXCEPTION 'Permission % cannot be assigned individually', NEW.permission_code;
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER member_permission_overrides_validate
    BEFORE INSERT OR UPDATE OF permission_code ON workforce.member_permission_overrides
    FOR EACH ROW EXECUTE FUNCTION workforce.validate_permission_override();

COMMENT ON TABLE workforce.restaurant_members IS
    'Restaurant-scoped profile and role. user_id stays nullable until an invitation is accepted.';
COMMENT ON VIEW workforce.effective_member_permissions IS
    'Default role permissions after applying per-member GRANT/DENY overrides.';
