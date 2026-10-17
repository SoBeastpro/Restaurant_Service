package com.restaurant.notification.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class NotificationRepository {

    private final JdbcClient jdbc;

    public NotificationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(
            UUID id,
            UUID userId,
            UUID restaurantId,
            String type,
            String title,
            String message,
            String payloadJson) {
        jdbc.sql("""
                        INSERT INTO notifications (
                            id, user_id, restaurant_id, type, title, message, payload
                        ) VALUES (
                            :id, :userId, :restaurantId, :type, :title, :message,
                            CAST(:payload AS jsonb)
                        )
                        """)
                .param("id", id)
                .param("userId", userId)
                .param("restaurantId", restaurantId)
                .param("type", type)
                .param("title", title)
                .param("message", message)
                .param("payload", payloadJson)
                .update();
    }

    public Optional<NotificationRecord> find(UUID id, UUID userId) {
        return jdbc.sql(notificationSql() + " WHERE id = :id AND user_id = :userId")
                .param("id", id)
                .param("userId", userId)
                .query(NotificationRepository::map)
                .optional();
    }

    public List<NotificationRecord> findByUser(UUID userId) {
        return jdbc.sql(notificationSql() + " WHERE user_id = :userId ORDER BY created_at DESC, id DESC")
                .param("userId", userId)
                .query(NotificationRepository::map)
                .list();
    }

    public void markRead(UUID id, UUID userId) {
        jdbc.sql("""
                        UPDATE notifications
                        SET read_at = COALESCE(read_at, CURRENT_TIMESTAMP)
                        WHERE id = :id AND user_id = :userId
                        """)
                .param("id", id)
                .param("userId", userId)
                .update();
    }

    public void markAllRead(UUID userId) {
        jdbc.sql("""
                        UPDATE notifications
                        SET read_at = CURRENT_TIMESTAMP
                        WHERE user_id = :userId AND read_at IS NULL
                        """)
                .param("userId", userId)
                .update();
    }

    private static String notificationSql() {
        return """
                SELECT id, user_id, restaurant_id, type, title, message,
                       payload::text AS payload, read_at, created_at
                FROM notifications
                """;
    }

    private static NotificationRecord map(ResultSet result, int rowNumber) throws SQLException {
        return new NotificationRecord(
                result.getObject("id", UUID.class),
                result.getObject("user_id", UUID.class),
                result.getObject("restaurant_id", UUID.class),
                result.getString("type"),
                result.getString("title"),
                result.getString("message"),
                result.getString("payload"),
                nullableInstant(result, "read_at"),
                result.getObject("created_at", OffsetDateTime.class).toInstant());
    }

    private static Instant nullableInstant(ResultSet result, String column) throws SQLException {
        OffsetDateTime value = result.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    public record NotificationRecord(
            UUID id,
            UUID userId,
            UUID restaurantId,
            String type,
            String title,
            String message,
            String payloadJson,
            Instant readAt,
            Instant createdAt) {
    }
}
