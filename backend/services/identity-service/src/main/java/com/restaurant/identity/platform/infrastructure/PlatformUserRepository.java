package com.restaurant.identity.platform.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.restaurant.identity.platform.api.PlatformUserDtos.UserResponse;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class PlatformUserRepository {

    public record UserMetricsSummary(long total, long active, long blocked, long archived) {
    }

    private final JdbcClient jdbc;

    public PlatformUserRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<UserResponse> findUsers(
            String query,
            String status,
            String role,
            int limit,
            int offset) {
        QuerySpec spec = querySpec(query, status, role);
        return jdbc.sql("""
                        SELECT u.id, u.login, u.email, u.phone, u.first_name, u.last_name,
                               u.role_code, u.position_code, u.restaurant_id, u.status,
                               u.last_login_at, u.created_at, u.updated_at,
                               COALESCE(array_agg(upr.role_code ORDER BY upr.role_code)
                                   FILTER (WHERE upr.role_code IS NOT NULL), ARRAY[]::VARCHAR[]) AS platform_roles
                        FROM users u
                        LEFT JOIN user_platform_roles upr ON upr.user_id = u.id
                        """ + spec.where() + """
                        GROUP BY u.id
                        ORDER BY u.created_at DESC, u.id
                        LIMIT :limit OFFSET :offset
                        """)
                .params(spec.params())
                .param("limit", limit)
                .param("offset", offset)
                .query(PlatformUserRepository::mapUser)
                .list();
    }

    public long countUsers(String query, String status, String role) {
        QuerySpec spec = querySpec(query, status, role);
        return jdbc.sql("SELECT COUNT(*) FROM users u " + spec.where())
                .params(spec.params())
                .query(Long.class)
                .single();
    }

    public Optional<UserResponse> findUser(UUID userId) {
        return jdbc.sql("""
                        SELECT u.id, u.login, u.email, u.phone, u.first_name, u.last_name,
                               u.role_code, u.position_code, u.restaurant_id, u.status,
                               u.last_login_at, u.created_at, u.updated_at,
                               COALESCE(array_agg(upr.role_code ORDER BY upr.role_code)
                                   FILTER (WHERE upr.role_code IS NOT NULL), ARRAY[]::VARCHAR[]) AS platform_roles
                        FROM users u
                        LEFT JOIN user_platform_roles upr ON upr.user_id = u.id
                        WHERE u.id = :userId
                        GROUP BY u.id
                        """)
                .param("userId", userId)
                .query(PlatformUserRepository::mapUser)
                .optional();
    }

    public UserMetricsSummary metricsSummary() {
        return jdbc.sql("""
                        SELECT COUNT(*) AS total,
                               COUNT(*) FILTER (WHERE status = 'ACTIVE') AS active,
                               COUNT(*) FILTER (WHERE status = 'BLOCKED') AS blocked,
                               COUNT(*) FILTER (WHERE status = 'ARCHIVED') AS archived
                        FROM users
                        """)
                .query((result, rowNumber) -> new UserMetricsSummary(
                        result.getLong("total"),
                        result.getLong("active"),
                        result.getLong("blocked"),
                        result.getLong("archived")))
                .single();
    }

    public boolean updateStatus(UUID userId, String status) {
        return jdbc.sql("""
                        UPDATE users
                        SET status = :status
                        WHERE id = :userId AND status <> :status
                        """)
                .param("userId", userId)
                .param("status", status)
                .update() == 1;
    }

    public void revokeSessions(UUID userId, String reason) {
        jdbc.sql("""
                        UPDATE sessions
                        SET revoked_at = CURRENT_TIMESTAMP, revoke_reason = :reason
                        WHERE user_id = :userId AND revoked_at IS NULL
                        """)
                .param("userId", userId)
                .param("reason", reason)
                .update();
    }

    public void insertStatusEvent(UUID userId, UUID actorUserId, String oldStatus, String newStatus, String reason) {
        jdbc.sql("""
                        INSERT INTO outbox_events (
                            aggregate_type, aggregate_id, event_type, payload
                        ) VALUES (
                            'USER', :userId, 'identity.user.status-changed.v1',
                            jsonb_strip_nulls(jsonb_build_object(
                                'userId', :userId,
                                'actorUserId', :actorUserId,
                                'oldStatus', :oldStatus,
                                'newStatus', :newStatus,
                                'status', :newStatus,
                                'reason', :reason
                            ))
                        )
                        """)
                .param("userId", userId)
                .param("actorUserId", actorUserId)
                .param("oldStatus", oldStatus)
                .param("newStatus", newStatus)
                .param("reason", reason)
                .update();
    }

    private static QuerySpec querySpec(String query, String status, String role) {
        StringBuilder where = new StringBuilder("WHERE 1=1");
        java.util.Map<String, Object> params = new java.util.LinkedHashMap<>();
        if (query != null) {
            where.append(" AND (LOWER(u.login) LIKE :query OR LOWER(COALESCE(u.email, '')) LIKE :query")
                    .append(" OR LOWER(u.first_name || ' ' || u.last_name) LIKE :query)");
            params.put("query", "%" + query.toLowerCase(java.util.Locale.ROOT) + "%");
        }
        if (status != null) {
            where.append(" AND u.status = :status");
            params.put("status", status);
        }
        if (role != null) {
            where.append(" AND u.role_code = :role");
            params.put("role", role);
        }
        return new QuerySpec(where.toString(), params);
    }

    private static UserResponse mapUser(ResultSet result, int rowNumber) throws SQLException {
        java.sql.Array array = result.getArray("platform_roles");
        String[] roles = array == null ? new String[0] : (String[]) array.getArray();
        return new UserResponse(
                result.getObject("id", UUID.class),
                result.getString("login"),
                result.getString("email"),
                result.getString("phone"),
                result.getString("first_name"),
                result.getString("last_name"),
                result.getString("role_code"),
                result.getString("position_code"),
                result.getObject("restaurant_id", UUID.class),
                result.getString("status"),
                List.of(roles),
                instant(result, "last_login_at"),
                instant(result, "created_at"),
                instant(result, "updated_at"));
    }

    private static Instant instant(ResultSet result, String column) throws SQLException {
        var value = result.getObject(column, java.time.OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private record QuerySpec(String where, java.util.Map<String, Object> params) {
    }
}
