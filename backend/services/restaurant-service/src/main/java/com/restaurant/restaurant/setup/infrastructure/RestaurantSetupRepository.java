package com.restaurant.restaurant.setup.infrastructure;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class RestaurantSetupRepository {

    private final JdbcClient jdbc;

    public RestaurantSetupRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public boolean hasActiveRestaurant(UUID directorUserId) {
        return jdbc.sql("""
                        SELECT EXISTS(
                            SELECT 1 FROM restaurants
                            WHERE director_user_id = :directorUserId AND status <> 'ARCHIVED'
                        )
                        """)
                .param("directorUserId", directorUserId)
                .query(Boolean.class)
                .single();
    }

    public void insertRestaurant(UUID id, UUID directorUserId, String name, String address) {
        jdbc.sql("""
                        INSERT INTO restaurants (id, director_user_id, name, address)
                        VALUES (:id, :directorUserId, :name, :address)
                        """)
                .param("id", id)
                .param("directorUserId", directorUserId)
                .param("name", name)
                .param("address", address)
                .update();
    }

    public void insertOpeningHours(
            UUID restaurantId,
            DayOfWeek day,
            boolean closed,
            LocalTime opensAt,
            LocalTime closesAt,
            boolean closesNextDay) {
        jdbc.sql("""
                        INSERT INTO restaurant_opening_hours (
                            restaurant_id, day_of_week, is_closed, opens_at, closes_at, closes_next_day
                        ) VALUES (
                            :restaurantId, :day, :closed, :opensAt, :closesAt, :closesNextDay
                        )
                        """)
                .param("restaurantId", restaurantId)
                .param("day", day.getValue())
                .param("closed", closed)
                .param("opensAt", opensAt)
                .param("closesAt", closesAt)
                .param("closesNextDay", closesNextDay)
                .update();
    }

    public Optional<RestaurantRecord> findRestaurant(UUID restaurantId) {
        return jdbc.sql("""
                        SELECT id, director_user_id, name, address, status, created_at
                        FROM restaurants
                        WHERE id = :restaurantId AND status <> 'ARCHIVED'
                        """)
                .param("restaurantId", restaurantId)
                .query((result, rowNumber) -> new RestaurantRecord(
                        result.getObject("id", UUID.class),
                        result.getObject("director_user_id", UUID.class),
                        result.getString("name"),
                        result.getString("address"),
                        result.getString("status"),
                        instant(result, "created_at")))
                .optional();
    }

    public List<OpeningHoursRecord> findOpeningHours(UUID restaurantId) {
        return jdbc.sql("""
                        SELECT day_of_week, is_closed, opens_at, closes_at, closes_next_day
                        FROM restaurant_opening_hours
                        WHERE restaurant_id = :restaurantId
                        ORDER BY day_of_week
                        """)
                .param("restaurantId", restaurantId)
                .query((result, rowNumber) -> new OpeningHoursRecord(
                        DayOfWeek.of(result.getInt("day_of_week")),
                        result.getBoolean("is_closed"),
                        result.getObject("opens_at", LocalTime.class),
                        result.getObject("closes_at", LocalTime.class),
                        result.getBoolean("closes_next_day")))
                .list();
    }

    public void insertHall(UUID id, UUID restaurantId, String name, int displayOrder) {
        jdbc.sql("""
                        INSERT INTO halls (id, restaurant_id, name, display_order)
                        VALUES (:id, :restaurantId, :name, :displayOrder)
                        """)
                .param("id", id)
                .param("restaurantId", restaurantId)
                .param("name", name)
                .param("displayOrder", displayOrder)
                .update();
    }

    public Optional<HallRecord> findHall(UUID restaurantId, UUID hallId) {
        return jdbc.sql("""
                        SELECT id, restaurant_id, name, display_order, status
                        FROM halls
                        WHERE restaurant_id = :restaurantId AND id = :hallId AND status = 'ACTIVE'
                        """)
                .param("restaurantId", restaurantId)
                .param("hallId", hallId)
                .query(RestaurantSetupRepository::mapHall)
                .optional();
    }

    public List<HallRecord> findHalls(UUID restaurantId) {
        return jdbc.sql("""
                        SELECT id, restaurant_id, name, display_order, status
                        FROM halls
                        WHERE restaurant_id = :restaurantId AND status = 'ACTIVE'
                        ORDER BY display_order, created_at
                        """)
                .param("restaurantId", restaurantId)
                .query(RestaurantSetupRepository::mapHall)
                .list();
    }

    public void insertTable(
            UUID id,
            UUID restaurantId,
            UUID hallId,
            String number,
            int seatsCount,
            BigDecimal positionX,
            BigDecimal positionY,
            String shape) {
        jdbc.sql("""
                        INSERT INTO dining_tables (
                            id, restaurant_id, hall_id, number, seats_count,
                            position_x, position_y, shape
                        ) VALUES (
                            :id, :restaurantId, :hallId, :number, :seatsCount,
                            :positionX, :positionY, :shape
                        )
                        """)
                .param("id", id)
                .param("restaurantId", restaurantId)
                .param("hallId", hallId)
                .param("number", number)
                .param("seatsCount", seatsCount)
                .param("positionX", positionX)
                .param("positionY", positionY)
                .param("shape", shape)
                .update();
    }

    public Optional<TableRecord> findTable(UUID restaurantId, UUID tableId) {
        return jdbc.sql(tableSql() + " WHERE restaurant_id = :restaurantId AND id = :tableId AND archived_at IS NULL")
                .param("restaurantId", restaurantId)
                .param("tableId", tableId)
                .query(RestaurantSetupRepository::mapTable)
                .optional();
    }

    public List<TableRecord> findTables(UUID restaurantId) {
        return jdbc.sql(tableSql() + " WHERE restaurant_id = :restaurantId AND archived_at IS NULL ORDER BY display_order, created_at")
                .param("restaurantId", restaurantId)
                .query(RestaurantSetupRepository::mapTable)
                .list();
    }

    public List<UUID> findActiveTableIds(UUID restaurantId, List<UUID> tableIds) {
        if (tableIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("""
                        SELECT id
                        FROM dining_tables
                        WHERE restaurant_id = :restaurantId
                          AND id IN (:tableIds)
                          AND archived_at IS NULL
                        ORDER BY id
                        """)
                .param("restaurantId", restaurantId)
                .param("tableIds", tableIds)
                .query(UUID.class)
                .list();
    }

    public List<UUID> findAllActiveTableIds(UUID restaurantId) {
        return jdbc.sql("""
                        SELECT id
                        FROM dining_tables
                        WHERE restaurant_id = :restaurantId
                          AND archived_at IS NULL
                        ORDER BY id
                        """)
                .param("restaurantId", restaurantId)
                .query(UUID.class)
                .list();
    }

    public void updateTable(TableRecord table) {
        jdbc.sql("""
                        UPDATE dining_tables
                        SET number = :number,
                            seats_count = :seatsCount,
                            position_x = :positionX,
                            position_y = :positionY,
                            shape = :shape
                        WHERE id = :id AND restaurant_id = :restaurantId AND archived_at IS NULL
                        """)
                .param("number", table.number())
                .param("seatsCount", table.seatsCount())
                .param("positionX", table.positionX())
                .param("positionY", table.positionY())
                .param("shape", table.shape())
                .param("id", table.id())
                .param("restaurantId", table.restaurantId())
                .update();
    }

    private static String tableSql() {
        return """
                SELECT id, restaurant_id, hall_id, number, seats_count,
                       position_x, position_y, shape, status
                FROM dining_tables
                """;
    }

    private static HallRecord mapHall(ResultSet result, int rowNumber) throws SQLException {
        return new HallRecord(
                result.getObject("id", UUID.class),
                result.getObject("restaurant_id", UUID.class),
                result.getString("name"),
                result.getInt("display_order"),
                result.getString("status"));
    }

    private static TableRecord mapTable(ResultSet result, int rowNumber) throws SQLException {
        return new TableRecord(
                result.getObject("id", UUID.class),
                result.getObject("restaurant_id", UUID.class),
                result.getObject("hall_id", UUID.class),
                result.getString("number"),
                result.getInt("seats_count"),
                result.getBigDecimal("position_x"),
                result.getBigDecimal("position_y"),
                result.getString("shape"),
                result.getString("status"));
    }

    private static Instant instant(ResultSet result, String column) throws SQLException {
        return result.getObject(column, OffsetDateTime.class).toInstant();
    }

    public record RestaurantRecord(
            UUID id,
            UUID directorUserId,
            String name,
            String address,
            String status,
            Instant createdAt) {
    }

    public record OpeningHoursRecord(
            DayOfWeek dayOfWeek,
            boolean closed,
            LocalTime opensAt,
            LocalTime closesAt,
            boolean closesNextDay) {
    }

    public record HallRecord(UUID id, UUID restaurantId, String name, int displayOrder, String status) {
    }

    public record TableRecord(
            UUID id,
            UUID restaurantId,
            UUID hallId,
            String number,
            int seatsCount,
            BigDecimal positionX,
            BigDecimal positionY,
            String shape,
            String status) {
    }
}
