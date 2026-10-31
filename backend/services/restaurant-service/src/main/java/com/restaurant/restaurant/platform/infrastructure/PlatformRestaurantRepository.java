package com.restaurant.restaurant.platform.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.restaurant.restaurant.platform.api.PlatformRestaurantDtos.RestaurantResponse;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class PlatformRestaurantRepository {

    public record RestaurantMetricsSummary(long total, long active, long blocked, long archived) {
    }

    public record RestaurantStatus(UUID id, String status) {
    }

    private static final String SELECT = """
            SELECT r.id, r.director_user_id, r.name, r.address, r.phone, r.description,
                   r.status, r.created_at, r.updated_at,
                   (SELECT COUNT(*) FROM halls h WHERE h.restaurant_id = r.id AND h.status = 'ACTIVE') AS halls_count,
                   (SELECT COUNT(*) FROM dining_tables t WHERE t.restaurant_id = r.id AND t.archived_at IS NULL) AS tables_count
            FROM restaurants r
            """;

    private final JdbcClient jdbc;

    public PlatformRestaurantRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<RestaurantResponse> findRestaurants(String query, String status, int limit, int offset) {
        QuerySpec spec = querySpec(query, status);
        return jdbc.sql(SELECT + spec.where() + " ORDER BY r.created_at DESC, r.id LIMIT :limit OFFSET :offset")
                .params(spec.params())
                .param("limit", limit)
                .param("offset", offset)
                .query(PlatformRestaurantRepository::map)
                .list();
    }

    public long countRestaurants(String query, String status) {
        QuerySpec spec = querySpec(query, status);
        return jdbc.sql("SELECT COUNT(*) FROM restaurants r " + spec.where())
                .params(spec.params())
                .query(Long.class)
                .single();
    }

    public Optional<RestaurantResponse> findRestaurant(UUID restaurantId) {
        return jdbc.sql(SELECT + " WHERE r.id = :restaurantId")
                .param("restaurantId", restaurantId)
                .query(PlatformRestaurantRepository::map)
                .optional();
    }

    public Optional<RestaurantStatus> findStatus(UUID restaurantId) {
        return jdbc.sql("SELECT id, status FROM restaurants WHERE id = :restaurantId")
                .param("restaurantId", restaurantId)
                .query((result, rowNumber) -> new RestaurantStatus(
                        result.getObject("id", UUID.class), result.getString("status")))
                .optional();
    }

    public RestaurantMetricsSummary metricsSummary() {
        return jdbc.sql("""
                        SELECT COUNT(*) AS total,
                               COUNT(*) FILTER (WHERE status = 'ACTIVE') AS active,
                               COUNT(*) FILTER (WHERE status = 'BLOCKED') AS blocked,
                               COUNT(*) FILTER (WHERE status = 'ARCHIVED') AS archived
                        FROM restaurants
                        """)
                .query((result, rowNumber) -> new RestaurantMetricsSummary(
                        result.getLong("total"),
                        result.getLong("active"),
                        result.getLong("blocked"),
                        result.getLong("archived")))
                .single();
    }

    public void updateStatus(UUID restaurantId, String status) {
        jdbc.sql("UPDATE restaurants SET status = :status WHERE id = :restaurantId")
                .param("restaurantId", restaurantId)
                .param("status", status)
                .update();
    }

    public void insertStatusEvent(
            UUID restaurantId,
            UUID actorUserId,
            UUID directorUserId,
            String oldStatus,
            String newStatus,
            String reason) {
        jdbc.sql("""
                        INSERT INTO outbox_events (aggregate_type, aggregate_id, event_type, payload)
                        VALUES (
                            'RESTAURANT', :restaurantId, 'restaurant.restaurant.status-changed.v1',
                            jsonb_strip_nulls(jsonb_build_object(
                                'restaurantId', :restaurantId,
                                'actorUserId', :actorUserId,
                                'directorUserId', :directorUserId,
                                'oldStatus', :oldStatus,
                                'newStatus', :newStatus,
                                'status', :newStatus,
                                'reason', :reason
                            ))
                        )
                        """)
                .param("restaurantId", restaurantId)
                .param("actorUserId", actorUserId)
                .param("directorUserId", directorUserId)
                .param("oldStatus", oldStatus)
                .param("newStatus", newStatus)
                .param("reason", reason)
                .update();
    }

    private static QuerySpec querySpec(String query, String status) {
        StringBuilder where = new StringBuilder("WHERE 1=1");
        java.util.Map<String, Object> params = new java.util.LinkedHashMap<>();
        if (query != null) {
            where.append(" AND (LOWER(r.name) LIKE :query OR LOWER(COALESCE(r.address, '')) LIKE :query)");
            params.put("query", "%" + query.toLowerCase(java.util.Locale.ROOT) + "%");
        }
        if (status != null) {
            where.append(" AND r.status = :status");
            params.put("status", status);
        }
        return new QuerySpec(where.toString(), params);
    }

    private static RestaurantResponse map(ResultSet result, int rowNumber) throws SQLException {
        return new RestaurantResponse(
                result.getObject("id", UUID.class),
                result.getObject("director_user_id", UUID.class),
                result.getString("name"),
                result.getString("address"),
                result.getString("phone"),
                result.getString("description"),
                result.getString("status"),
                result.getInt("halls_count"),
                result.getInt("tables_count"),
                instant(result, "created_at"),
                instant(result, "updated_at"));
    }

    private static Instant instant(ResultSet result, String column) throws SQLException {
        return result.getObject(column, java.time.OffsetDateTime.class).toInstant();
    }

    private record QuerySpec(String where, java.util.Map<String, Object> params) {
    }
}
