package com.restaurant.audit.platform.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.restaurant.audit.platform.api.PlatformAuditDtos.AuditLogResponse;
import com.restaurant.audit.platform.api.PlatformAuditDtos.SystemEventResponse;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import tools.jackson.databind.ObjectMapper;

@Repository
public class PlatformAuditRepository {

    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;

    public PlatformAuditRepository(JdbcClient jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public List<AuditLogResponse> findAuditLogs(
            String action,
            String outcome,
            UUID actorUserId,
            UUID subjectUserId,
            UUID restaurantId,
            Instant from,
            Instant to,
            int limit,
            int offset) {
        QuerySpec spec = auditSpec(action, outcome, actorUserId, subjectUserId, restaurantId, from, to);
        return jdbc.sql("""
                        SELECT id, event_id, actor_user_id, actor_membership_id, subject_user_id,
                               restaurant_id, scope, action, entity_type, entity_id, outcome,
                               host(ip_address) AS ip_address, user_agent, correlation_id,
                               details::TEXT AS details, created_at
                        FROM audit_logs
                        """ + spec.where() + " ORDER BY created_at DESC, id LIMIT :limit OFFSET :offset")
                .params(spec.params())
                .param("limit", limit)
                .param("offset", offset)
                .query(this::mapAudit)
                .list();
    }

    public long countAuditLogs(
            String action,
            String outcome,
            UUID actorUserId,
            UUID subjectUserId,
            UUID restaurantId,
            Instant from,
            Instant to) {
        QuerySpec spec = auditSpec(action, outcome, actorUserId, subjectUserId, restaurantId, from, to);
        return jdbc.sql("SELECT COUNT(*) FROM audit_logs " + spec.where())
                .params(spec.params())
                .query(Long.class)
                .single();
    }

    public List<SystemEventResponse> findSystemEvents(
            String level,
            String service,
            String eventType,
            Instant from,
            Instant to,
            int limit,
            int offset) {
        QuerySpec spec = eventSpec(level, service, eventType, from, to);
        return jdbc.sql("""
                        SELECT id, level, service, event_type, message, correlation_id,
                               details::TEXT AS details, created_at
                        FROM system_events
                        """ + spec.where() + " ORDER BY created_at DESC, id LIMIT :limit OFFSET :offset")
                .params(spec.params())
                .param("limit", limit)
                .param("offset", offset)
                .query(this::mapEvent)
                .list();
    }

    public long countSystemEvents(String level, String service, String eventType, Instant from, Instant to) {
        QuerySpec spec = eventSpec(level, service, eventType, from, to);
        return jdbc.sql("SELECT COUNT(*) FROM system_events " + spec.where())
                .params(spec.params())
                .query(Long.class)
                .single();
    }

    private static QuerySpec auditSpec(
            String action,
            String outcome,
            UUID actorUserId,
            UUID subjectUserId,
            UUID restaurantId,
            Instant from,
            Instant to) {
        StringBuilder where = new StringBuilder("WHERE scope = 'PLATFORM'");
        java.util.Map<String, Object> params = new java.util.LinkedHashMap<>();
        if (action != null) {
            where.append(" AND action = :action");
            params.put("action", action);
        }
        if (outcome != null) {
            where.append(" AND outcome = :outcome");
            params.put("outcome", outcome);
        }
        if (actorUserId != null) {
            where.append(" AND actor_user_id = :actorUserId");
            params.put("actorUserId", actorUserId);
        }
        if (subjectUserId != null) {
            where.append(" AND subject_user_id = :subjectUserId");
            params.put("subjectUserId", subjectUserId);
        }
        if (restaurantId != null) {
            where.append(" AND restaurant_id = :restaurantId");
            params.put("restaurantId", restaurantId);
        }
        addPeriod(where, params, from, to);
        return new QuerySpec(where.toString(), params);
    }

    private static QuerySpec eventSpec(String level, String service, String eventType, Instant from, Instant to) {
        StringBuilder where = new StringBuilder("WHERE 1=1");
        java.util.Map<String, Object> params = new java.util.LinkedHashMap<>();
        if (level != null) {
            where.append(" AND level = :level");
            params.put("level", level);
        }
        if (service != null) {
            where.append(" AND service = :service");
            params.put("service", service);
        }
        if (eventType != null) {
            where.append(" AND event_type = :eventType");
            params.put("eventType", eventType);
        }
        addPeriod(where, params, from, to);
        return new QuerySpec(where.toString(), params);
    }

    private static void addPeriod(
            StringBuilder where,
            java.util.Map<String, Object> params,
            Instant from,
            Instant to) {
        if (from != null) {
            where.append(" AND created_at >= :from");
            params.put("from", OffsetDateTime.ofInstant(from, java.time.ZoneOffset.UTC));
        }
        if (to != null) {
            where.append(" AND created_at < :to");
            params.put("to", OffsetDateTime.ofInstant(to, java.time.ZoneOffset.UTC));
        }
    }

    private AuditLogResponse mapAudit(ResultSet result, int rowNumber) throws SQLException {
        try {
            return new AuditLogResponse(
                    result.getObject("id", UUID.class),
                    result.getObject("event_id", UUID.class),
                    result.getObject("actor_user_id", UUID.class),
                    result.getObject("actor_membership_id", UUID.class),
                    result.getObject("subject_user_id", UUID.class),
                    result.getObject("restaurant_id", UUID.class),
                    result.getString("scope"),
                    result.getString("action"),
                    result.getString("entity_type"),
                    result.getObject("entity_id", UUID.class),
                    result.getString("outcome"),
                    result.getString("ip_address"),
                    result.getString("user_agent"),
                    result.getObject("correlation_id", UUID.class),
                    objectMapper.readTree(result.getString("details")),
                    result.getObject("created_at", OffsetDateTime.class).toInstant());
        } catch (Exception exception) {
            throw new SQLException("Invalid audit details JSON", exception);
        }
    }

    private SystemEventResponse mapEvent(ResultSet result, int rowNumber) throws SQLException {
        try {
            return new SystemEventResponse(
                    result.getObject("id", UUID.class),
                    result.getString("level"),
                    result.getString("service"),
                    result.getString("event_type"),
                    result.getString("message"),
                    result.getObject("correlation_id", UUID.class),
                    objectMapper.readTree(result.getString("details")),
                    result.getObject("created_at", OffsetDateTime.class).toInstant());
        } catch (Exception exception) {
            throw new SQLException("Invalid system event details JSON", exception);
        }
    }

    private record QuerySpec(String where, java.util.Map<String, Object> params) {
    }
}
