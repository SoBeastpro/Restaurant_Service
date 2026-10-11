CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN NEW.updated_at = CURRENT_TIMESTAMP; RETURN NEW; END;
$$;

CREATE TABLE tariff_plans (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    price NUMERIC(12, 2) NOT NULL DEFAULT 0,
    currency CHAR(3) NOT NULL DEFAULT 'RUB',
    duration_days INTEGER NOT NULL DEFAULT 30,
    features JSONB NOT NULL DEFAULT '{}'::JSONB,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_by_user_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT tariff_plans_code_not_blank_check CHECK (BTRIM(code) <> ''),
    CONSTRAINT tariff_plans_name_not_blank_check CHECK (BTRIM(name) <> ''),
    CONSTRAINT tariff_plans_price_check CHECK (price >= 0),
    CONSTRAINT tariff_plans_duration_check CHECK (duration_days > 0),
    CONSTRAINT tariff_plans_features_object_check CHECK (jsonb_typeof(features) = 'object'),
    CONSTRAINT tariff_plans_status_check CHECK (status IN ('ACTIVE', 'ARCHIVED'))
);

CREATE TABLE subscriptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    restaurant_id UUID NOT NULL,
    plan_id UUID NOT NULL REFERENCES tariff_plans(id) ON DELETE RESTRICT,
    activated_by_membership_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    activation_mode VARCHAR(20) NOT NULL DEFAULT 'STUB',
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    cancelled_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT subscriptions_status_check CHECK (status IN ('ACTIVE', 'EXPIRED', 'CANCELLED')),
    CONSTRAINT subscriptions_activation_mode_check CHECK (activation_mode IN ('STUB', 'PAYMENT_PROVIDER')),
    CONSTRAINT subscriptions_period_check CHECK (ends_at > starts_at),
    CONSTRAINT subscriptions_cancelled_at_check CHECK (status <> 'CANCELLED' OR cancelled_at IS NOT NULL)
);

CREATE UNIQUE INDEX subscriptions_one_active_per_restaurant_key ON subscriptions (restaurant_id) WHERE status = 'ACTIVE';
CREATE INDEX subscriptions_restaurant_history_idx ON subscriptions (restaurant_id, starts_at DESC);
CREATE INDEX subscriptions_ends_idx ON subscriptions (ends_at) WHERE status = 'ACTIVE';

CREATE TABLE subscription_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subscription_id UUID NOT NULL REFERENCES subscriptions(id) ON DELETE RESTRICT,
    actor_user_id UUID,
    event_type VARCHAR(20) NOT NULL,
    details JSONB NOT NULL DEFAULT '{}'::JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT subscription_events_type_check CHECK (event_type IN ('ACTIVATED', 'EXTENDED', 'EXPIRED', 'CANCELLED')),
    CONSTRAINT subscription_events_details_object_check CHECK (jsonb_typeof(details) = 'object')
);

CREATE INDEX subscription_events_subscription_created_idx ON subscription_events (subscription_id, created_at DESC);
CREATE TRIGGER tariff_plans_set_updated_at BEFORE UPDATE ON tariff_plans FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER subscriptions_set_updated_at BEFORE UPDATE ON subscriptions FOR EACH ROW EXECUTE FUNCTION set_updated_at();

INSERT INTO tariff_plans (id, code, name, description, price, duration_days, features) VALUES
    ('00000000-0000-0000-0000-000000000100', 'MONTHLY_PLACEHOLDER',
     'Условная месячная подписка', 'Временный тариф до подключения платёжного провайдера',
     0, 30, '{"placeholder": true}'::JSONB);

COMMENT ON COLUMN subscriptions.restaurant_id IS 'External restaurant-service UUID; no cross-database FK.';
COMMENT ON COLUMN subscriptions.activated_by_membership_id IS 'External workforce-service membership UUID.';
