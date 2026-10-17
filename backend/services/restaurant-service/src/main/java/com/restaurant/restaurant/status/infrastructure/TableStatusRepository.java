package com.restaurant.restaurant.status.infrastructure;

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
public class TableStatusRepository {

    private final JdbcClient jdbc;

    public TableStatusRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<TableStateRecord> findForUpdate(UUID restaurantId, UUID tableId) {
        return jdbc.sql("""
                        SELECT id, restaurant_id, number, status, status_comment
                        FROM dining_tables
                        WHERE restaurant_id = :restaurantId
                          AND id = :tableId
                          AND archived_at IS NULL
                        FOR UPDATE
                        """)
                .param("restaurantId", restaurantId)
                .param("tableId", tableId)
                .query((result, rowNumber) -> new TableStateRecord(
                        result.getObject("id", UUID.class),
                        result.getObject("restaurant_id", UUID.class),
                        result.getString("number"),
                        result.getString("status"),
                        result.getString("status_comment")))
                .optional();
    }

    public UUID changeStatus(
            TableStateRecord table,
            UUID shiftId,
            UUID actorMembershipId,
            String status,
            String comment) {
        jdbc.sql("""
                        UPDATE dining_tables
                        SET status = :status, status_comment = :comment
                        WHERE id = :tableId AND restaurant_id = :restaurantId
                        """)
                .param("status", status)
                .param("comment", comment)
                .param("tableId", table.id())
                .param("restaurantId", table.restaurantId())
                .update();

        UUID historyId = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO table_status_history (
                            id, restaurant_id, table_id, shift_id,
                            changed_by_membership_id, old_status, new_status, comment
                        ) VALUES (
                            :id, :restaurantId, :tableId, :shiftId,
                            :actor, :oldStatus, :newStatus, :comment
                        )
                        """)
                .param("id", historyId)
                .param("restaurantId", table.restaurantId())
                .param("tableId", table.id())
                .param("shiftId", shiftId)
                .param("actor", actorMembershipId)
                .param("oldStatus", table.status())
                .param("newStatus", status)
                .param("comment", comment)
                .update();

        jdbc.sql("""
                        INSERT INTO outbox_events (
                            aggregate_type, aggregate_id, event_type, payload
                        ) VALUES (
                            'DiningTable', :tableId, 'restaurant.table.status-changed.v1',
                            jsonb_strip_nulls(jsonb_build_object(
                                'restaurantId', CAST(:restaurantId AS text),
                                'tableId', CAST(:tableId AS text),
                                'tableNumber', CAST(:tableNumber AS text),
                                'shiftId', CAST(:shiftId AS text),
                                'changedByMembershipId', CAST(:actor AS text),
                                'oldStatus', CAST(:oldStatus AS text),
                                'status', CAST(:newStatus AS text),
                                'comment', CAST(:comment AS text)
                            ))
                        )
                        """)
                .param("restaurantId", table.restaurantId())
                .param("tableId", table.id())
                .param("tableNumber", table.number())
                .param("shiftId", shiftId)
                .param("actor", actorMembershipId)
                .param("oldStatus", table.status())
                .param("newStatus", status)
                .param("comment", comment)
                .update();
        return historyId;
    }

    public Optional<StatusHistoryRecord> findHistory(UUID restaurantId, UUID historyId) {
        return jdbc.sql(historySql() + " WHERE history.restaurant_id = :restaurantId AND history.id = :historyId")
                .param("restaurantId", restaurantId)
                .param("historyId", historyId)
                .query(TableStatusRepository::mapHistory)
                .optional();
    }

    public List<StatusHistoryRecord> findHistory(UUID restaurantId, UUID tableId, UUID shiftId) {
        String shiftFilter = shiftId == null ? "" : " AND history.shift_id = :shiftId";
        var query = jdbc.sql(historySql() + """
                         WHERE history.restaurant_id = :restaurantId
                           AND history.table_id = :tableId
                        """ + shiftFilter + " ORDER BY history.created_at DESC, history.id DESC")
                .param("restaurantId", restaurantId)
                .param("tableId", tableId);
        if (shiftId != null) {
            query = query.param("shiftId", shiftId);
        }
        return query.query(TableStatusRepository::mapHistory).list();
    }

    public List<StatusHistoryRecord> findRestaurantHistory(
            UUID restaurantId,
            UUID tableId,
            UUID shiftId,
            UUID changedByMembershipId,
            Instant from,
            Instant to) {
        StringBuilder sql = new StringBuilder(historySql())
                .append(" WHERE history.restaurant_id = :restaurantId");
        if (tableId != null) {
            sql.append(" AND history.table_id = :tableId");
        }
        if (shiftId != null) {
            sql.append(" AND history.shift_id = :shiftId");
        }
        if (changedByMembershipId != null) {
            sql.append(" AND history.changed_by_membership_id = :actor");
        }
        if (from != null) {
            sql.append(" AND history.created_at >= :from");
        }
        if (to != null) {
            sql.append(" AND history.created_at < :to");
        }
        sql.append(" ORDER BY history.created_at DESC, history.id DESC");

        var query = jdbc.sql(sql.toString()).param("restaurantId", restaurantId);
        if (tableId != null) {
            query = query.param("tableId", tableId);
        }
        if (shiftId != null) {
            query = query.param("shiftId", shiftId);
        }
        if (changedByMembershipId != null) {
            query = query.param("actor", changedByMembershipId);
        }
        if (from != null) {
            query = query.param("from", OffsetDateTime.ofInstant(from, java.time.ZoneOffset.UTC));
        }
        if (to != null) {
            query = query.param("to", OffsetDateTime.ofInstant(to, java.time.ZoneOffset.UTC));
        }
        return query.query(TableStatusRepository::mapHistory).list();
    }

    private static String historySql() {
        return """
                SELECT history.id, history.table_id, tables.number AS table_number,
                       history.shift_id, history.changed_by_membership_id,
                       history.old_status, history.new_status, history.comment,
                       history.created_at
                FROM table_status_history history
                JOIN dining_tables tables
                  ON tables.id = history.table_id
                 AND tables.restaurant_id = history.restaurant_id
                """;
    }

    private static StatusHistoryRecord mapHistory(ResultSet result, int rowNumber) throws SQLException {
        return new StatusHistoryRecord(
                result.getObject("id", UUID.class),
                result.getObject("table_id", UUID.class),
                result.getString("table_number"),
                result.getObject("shift_id", UUID.class),
                result.getObject("changed_by_membership_id", UUID.class),
                result.getString("old_status"),
                result.getString("new_status"),
                result.getString("comment"),
                result.getObject("created_at", OffsetDateTime.class).toInstant());
    }

    public record TableStateRecord(
            UUID id,
            UUID restaurantId,
            String number,
            String status,
            String comment) {
    }

    public record StatusHistoryRecord(
            UUID id,
            UUID tableId,
            String tableNumber,
            UUID shiftId,
            UUID changedByMembershipId,
            String oldStatus,
            String newStatus,
            String comment,
            Instant createdAt) {
    }
}
