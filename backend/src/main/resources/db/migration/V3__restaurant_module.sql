CREATE TABLE restaurant.restaurants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(200) NOT NULL,
    address VARCHAR(500),
    phone VARCHAR(32),
    description TEXT,
    logo_object_key VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    director_membership_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT restaurants_name_not_blank_check CHECK (BTRIM(name) <> ''),
    CONSTRAINT restaurants_status_check CHECK (status IN ('ACTIVE', 'BLOCKED', 'ARCHIVED')),
    CONSTRAINT restaurants_director_membership_key UNIQUE (director_membership_id)
);

CREATE INDEX restaurants_status_idx ON restaurant.restaurants (status);

CREATE TABLE restaurant.halls (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    restaurant_id UUID NOT NULL
        REFERENCES restaurant.restaurants(id) ON DELETE CASCADE,
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
    CONSTRAINT halls_layout_settings_object_check
        CHECK (jsonb_typeof(layout_settings) = 'object'),
    CONSTRAINT halls_status_check CHECK (status IN ('ACTIVE', 'ARCHIVED'))
);

CREATE INDEX halls_restaurant_order_idx
    ON restaurant.halls (restaurant_id, display_order);

CREATE TABLE restaurant.dining_tables (
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
    CONSTRAINT dining_tables_hall_restaurant_fk
        FOREIGN KEY (hall_id, restaurant_id)
        REFERENCES restaurant.halls(id, restaurant_id) ON DELETE CASCADE,
    CONSTRAINT dining_tables_number_not_blank_check CHECK (BTRIM(number) <> ''),
    CONSTRAINT dining_tables_seats_check CHECK (seats_count > 0),
    CONSTRAINT dining_tables_display_order_check CHECK (display_order >= 0),
    CONSTRAINT dining_tables_status_check
        CHECK (status IN ('READY', 'NEEDS_CLEANING', 'NEEDS_SERVING', 'PROBLEM')),
    CONSTRAINT dining_tables_problem_comment_check
        CHECK (status <> 'PROBLEM' OR NULLIF(BTRIM(status_comment), '') IS NOT NULL)
);

CREATE INDEX dining_tables_restaurant_status_idx
    ON restaurant.dining_tables (restaurant_id, status)
    WHERE archived_at IS NULL;
CREATE INDEX dining_tables_hall_order_idx
    ON restaurant.dining_tables (hall_id, display_order)
    WHERE archived_at IS NULL;

CREATE TABLE restaurant.table_status_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    restaurant_id UUID NOT NULL,
    table_id UUID NOT NULL,
    changed_by_membership_id UUID NOT NULL,
    old_status VARCHAR(30),
    new_status VARCHAR(30) NOT NULL,
    comment VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT table_status_history_table_restaurant_fk
        FOREIGN KEY (table_id, restaurant_id)
        REFERENCES restaurant.dining_tables(id, restaurant_id) ON DELETE RESTRICT,
    CONSTRAINT table_status_history_old_status_check
        CHECK (old_status IS NULL OR old_status IN ('READY', 'NEEDS_CLEANING', 'NEEDS_SERVING', 'PROBLEM')),
    CONSTRAINT table_status_history_new_status_check
        CHECK (new_status IN ('READY', 'NEEDS_CLEANING', 'NEEDS_SERVING', 'PROBLEM')),
    CONSTRAINT table_status_history_problem_comment_check
        CHECK (new_status <> 'PROBLEM' OR NULLIF(BTRIM(comment), '') IS NOT NULL)
);

CREATE INDEX table_status_history_table_created_idx
    ON restaurant.table_status_history (table_id, created_at DESC);
CREATE INDEX table_status_history_restaurant_created_idx
    ON restaurant.table_status_history (restaurant_id, created_at DESC);

CREATE TRIGGER restaurants_set_updated_at
    BEFORE UPDATE ON restaurant.restaurants
    FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

CREATE TRIGGER halls_set_updated_at
    BEFORE UPDATE ON restaurant.halls
    FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

CREATE TRIGGER dining_tables_set_updated_at
    BEFORE UPDATE ON restaurant.dining_tables
    FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

COMMENT ON COLUMN restaurant.halls.layout_settings IS
    'Responsive grid settings only. Absolute x/y coordinates are calculated by the mobile client.';
COMMENT ON COLUMN restaurant.dining_tables.status IS
    'Business state only; UI colours are derived by the client and are never stored.';
