CREATE SCHEMA identity;
CREATE SCHEMA restaurant;
CREATE SCHEMA workforce;
CREATE SCHEMA operations;
CREATE SCHEMA billing;
CREATE SCHEMA notification;
CREATE SCHEMA audit;

CREATE OR REPLACE FUNCTION public.set_updated_at()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$;

COMMENT ON SCHEMA identity IS 'Global accounts, authentication, sessions and RBAC definitions.';
COMMENT ON SCHEMA restaurant IS 'Restaurants, halls, tables and table status history.';
COMMENT ON SCHEMA workforce IS 'Restaurant memberships, invitations and individual permission overrides.';
COMMENT ON SCHEMA operations IS 'Shifts, attendance, tasks, templates and shift reports.';
COMMENT ON SCHEMA billing IS 'Tariff plans and placeholder restaurant subscriptions.';
COMMENT ON SCHEMA notification IS 'User notifications, push registrations and transactional outbox.';
COMMENT ON SCHEMA audit IS 'Restaurant/platform audit, user activity and system events.';
