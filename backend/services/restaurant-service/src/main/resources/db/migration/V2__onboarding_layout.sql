ALTER TABLE restaurants
    ALTER COLUMN address SET NOT NULL;

CREATE UNIQUE INDEX restaurants_one_active_per_director_key
    ON restaurants (director_user_id)
    WHERE status <> 'ARCHIVED';

CREATE TABLE restaurant_opening_hours (
    restaurant_id UUID NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    day_of_week SMALLINT NOT NULL,
    is_closed BOOLEAN NOT NULL DEFAULT FALSE,
    opens_at TIME,
    closes_at TIME,
    closes_next_day BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (restaurant_id, day_of_week),
    CONSTRAINT restaurant_opening_hours_day_check CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT restaurant_opening_hours_values_check CHECK (
        (is_closed AND opens_at IS NULL AND closes_at IS NULL AND NOT closes_next_day)
        OR
        (NOT is_closed AND opens_at IS NOT NULL AND closes_at IS NOT NULL AND opens_at <> closes_at)
    )
);

ALTER TABLE dining_tables
    ADD COLUMN position_x NUMERIC(8, 2) NOT NULL DEFAULT 0,
    ADD COLUMN position_y NUMERIC(8, 2) NOT NULL DEFAULT 0,
    ADD COLUMN shape VARCHAR(20) NOT NULL DEFAULT 'RECTANGLE',
    ADD CONSTRAINT dining_tables_position_x_check CHECK (position_x >= 0),
    ADD CONSTRAINT dining_tables_position_y_check CHECK (position_y >= 0),
    ADD CONSTRAINT dining_tables_shape_check CHECK (shape IN ('ROUND', 'SQUARE', 'RECTANGLE'));

COMMENT ON COLUMN restaurant_opening_hours.closes_next_day IS
    'True when the closing time belongs to the next calendar day, for example 18:00-02:00.';
