CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$;

CREATE TABLE restaurants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    director_user_id UUID NOT NULL,
    name VARCHAR(200) NOT NULL,
    address VARCHAR(500),
    phone VARCHAR(32),
    description TEXT,
    logo_object_key VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT restaurants_name_not_blank_check CHECK (BTRIM(name) <> ''),
    CONSTRAINT restaurants_status_check CHECK (status IN ('ACTIVE', 'BLOCKED', 'ARCHIVED'))
);

CREATE INDEX restaurants_director_user_idx ON restaurants (director_user_id);
CREATE INDEX restaurants_status_idx ON restaurants (status);

CREATE TABLE halls (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    restaurant_id UUID NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    display_order INTEGER NOT NULL DEFAULT 0,
    layout_settings JSONB NOT NULL DEFAULT '{}'::JSONB,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT halls_id_restaurant_key UNIQUE (id, restaurant_id),
    CONSTRAINT halls_restaurant_name_key UNIQUE (restaurant_id, name),
    CONSTRAINT halls_name_not_blank_check CHECK (BTRIM(name) <> ''),
    CONSTRAINT halls_display_order_check CHECK (display_order >= 0),
    CONSTRAINT halls_layout_settings_object_check CHECK (jsonb_typeof(layout_settings) = 'object'),
    CONSTRAINT halls_status_check CHECK (status IN ('ACTIVE', 'ARCHIVED'))
);

CREATE TABLE dining_tables (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    restaurant_id UUID NOT NULL,
    hall_id UUID NOT NULL,
    number VARCHAR(20) NOT NULL,
    seats_count SMALLINT NOT NULL,
    display_order INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(30) NOT NULL DEFAULT 'READY',
    status_comment VARCHAR(1000),
    archived_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT dining_tables_id_restaurant_key UNIQUE (id, restaurant_id),
    CONSTRAINT dining_tables_hall_number_key UNIQUE (hall_id, number),
    CONSTRAINT dining_tables_hall_restaurant_fk FOREIGN KEY (hall_id, restaurant_id)
        REFERENCES halls(id, restaurant_id) ON DELETE CASCADE,
    CONSTRAINT dining_tables_number_not_blank_check CHECK (BTRIM(number) <> ''),
    CONSTRAINT dining_tables_seats_check CHECK (seats_count > 0),
    CONSTRAINT dining_tables_display_order_check CHECK (display_order >= 0),
    CONSTRAINT dining_tables_status_check CHECK (status IN ('READY', 'NEEDS_CLEANING', 'NEEDS_SERVING', 'PROBLEM')),
    CONSTRAINT dining_tables_problem_comment_check CHECK (status <> 'PROBLEM' OR NULLIF(BTRIM(status_comment), '') IS NOT NULL)
);

CREATE TABLE table_status_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    restaurant_id UUID NOT NULL,
    table_id UUID NOT NULL,
    changed_by_membership_id UUID NOT NULL,
    old_status VARCHAR(30),
    new_status VARCHAR(30) NOT NULL,
    comment VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT table_status_history_table_restaurant_fk FOREIGN KEY (table_id, restaurant_id)
        REFERENCES dining_tables(id, restaurant_id) ON DELETE RESTRICT,
    CONSTRAINT table_status_history_old_status_check CHECK (old_status IS NULL OR old_status IN ('READY', 'NEEDS_CLEANING', 'NEEDS_SERVING', 'PROBLEM')),
    CONSTRAINT table_status_history_new_status_check CHECK (new_status IN ('READY', 'NEEDS_CLEANING', 'NEEDS_SERVING', 'PROBLEM')),
    CONSTRAINT table_status_history_problem_comment_check CHECK (new_status <> 'PROBLEM' OR NULLIF(BTRIM(comment), '') IS NOT NULL)
);

CREATE INDEX halls_restaurant_order_idx ON halls (restaurant_id, display_order);
CREATE INDEX dining_tables_restaurant_status_idx ON dining_tables (restaurant_id, status) WHERE archived_at IS NULL;
CREATE INDEX dining_tables_hall_order_idx ON dining_tables (hall_id, display_order) WHERE archived_at IS NULL;
CREATE INDEX table_status_history_table_created_idx ON table_status_history (table_id, created_at DESC);
CREATE INDEX table_status_history_restaurant_created_idx ON table_status_history (restaurant_id, created_at DESC);

CREATE TRIGGER restaurants_set_updated_at BEFORE UPDATE ON restaurants FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER halls_set_updated_at BEFORE UPDATE ON halls FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER dining_tables_set_updated_at BEFORE UPDATE ON dining_tables FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE OR REPLACE FUNCTION prevent_history_mutation()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION '% is append-only', TG_TABLE_NAME;
END;
$$;

CREATE TRIGGER table_status_history_append_only BEFORE UPDATE OR DELETE ON table_status_history
FOR EACH ROW EXECUTE FUNCTION prevent_history_mutation();

COMMENT ON COLUMN restaurants.director_user_id IS 'External identity-service user UUID; intentionally has no cross-database FK.';
COMMENT ON COLUMN table_status_history.changed_by_membership_id IS 'External workforce-service membership UUID.';
