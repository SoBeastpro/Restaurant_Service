package com.restaurant.billing.platform.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.restaurant.billing.platform.api.PlatformBillingDtos.SubscriptionResponse;
import com.restaurant.billing.platform.api.PlatformBillingDtos.TariffResponse;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Repository
public class PlatformBillingRepository {

    public record BillingMetricsSummary(
            long subscriptionsTotal,
            long activeSubscriptions,
            long expiredSubscriptions,
            long cancelledSubscriptions,
            long activeTariffs,
            long archivedTariffs) {
    }

    private static final String SUBSCRIPTION_SELECT = """
            SELECT s.id, s.restaurant_id, s.plan_id, p.code AS plan_code, p.name AS plan_name,
                   s.activated_by_membership_id, s.activated_by_user_id, s.status,
                   s.activation_mode, s.starts_at, s.ends_at, s.cancelled_at,
                   s.created_at, s.updated_at
            FROM subscriptions s
            JOIN tariff_plans p ON p.id = s.plan_id
            """;

    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;

    public PlatformBillingRepository(JdbcClient jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public List<TariffResponse> findTariffs(String status) {
        String where = status == null ? "" : " WHERE status = :status";
        JdbcClient.StatementSpec statement = jdbc.sql("""
                SELECT id, code, name, description, price, currency, duration_days,
                       features::TEXT AS features, status, created_by_user_id, created_at, updated_at
                FROM tariff_plans
                """ + where + " ORDER BY created_at DESC, code");
        if (status != null) {
            statement = statement.param("status", status);
        }
        return statement.query(this::mapTariff).list();
    }

    public Optional<TariffResponse> findTariff(UUID tariffId) {
        return jdbc.sql("""
                        SELECT id, code, name, description, price, currency, duration_days,
                               features::TEXT AS features, status, created_by_user_id, created_at, updated_at
                        FROM tariff_plans WHERE id = :tariffId
                        """)
                .param("tariffId", tariffId)
                .query(this::mapTariff)
                .optional();
    }

    public UUID insertTariff(
            String code,
            String name,
            String description,
            java.math.BigDecimal price,
            String currency,
            int durationDays,
            JsonNode features,
            UUID actorUserId) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO tariff_plans (
                            id, code, name, description, price, currency, duration_days,
                            features, created_by_user_id
                        ) VALUES (
                            :id, :code, :name, :description, :price, :currency, :durationDays,
                            CAST(:features AS JSONB), :actorUserId
                        )
                        """)
                .param("id", id)
                .param("code", code)
                .param("name", name)
                .param("description", description)
                .param("price", price)
                .param("currency", currency)
                .param("durationDays", durationDays)
                .param("features", features.toString())
                .param("actorUserId", actorUserId)
                .update();
        return id;
    }

    public void updateTariff(TariffResponse tariff) {
        jdbc.sql("""
                        UPDATE tariff_plans
                        SET name = :name,
                            description = :description,
                            price = :price,
                            currency = :currency,
                            duration_days = :durationDays,
                            features = CAST(:features AS JSONB),
                            status = :status
                        WHERE id = :id
                        """)
                .param("id", tariff.id())
                .param("name", tariff.name())
                .param("description", tariff.description())
                .param("price", tariff.price())
                .param("currency", tariff.currency())
                .param("durationDays", tariff.durationDays())
                .param("features", tariff.features().toString())
                .param("status", tariff.status())
                .update();
    }

    public void insertTariffOutbox(UUID tariffId, String eventType, String payloadJson) {
        jdbc.sql("""
                        INSERT INTO outbox_events (aggregate_type, aggregate_id, event_type, payload)
                        VALUES ('TARIFF', :tariffId, :eventType, CAST(:payload AS JSONB))
                        """)
                .param("tariffId", tariffId)
                .param("eventType", eventType)
                .param("payload", payloadJson)
                .update();
    }

    public void expireSubscriptions() {
        jdbc.sql("""
                        UPDATE subscriptions
                        SET status = 'EXPIRED'
                        WHERE status = 'ACTIVE' AND ends_at <= CURRENT_TIMESTAMP
                        """).update();
    }

    public BillingMetricsSummary metricsSummary() {
        return jdbc.sql("""
                        SELECT
                            (SELECT COUNT(*) FROM subscriptions) AS subscriptions_total,
                            (SELECT COUNT(*) FROM subscriptions WHERE status = 'ACTIVE') AS active_subscriptions,
                            (SELECT COUNT(*) FROM subscriptions WHERE status = 'EXPIRED') AS expired_subscriptions,
                            (SELECT COUNT(*) FROM subscriptions WHERE status = 'CANCELLED') AS cancelled_subscriptions,
                            (SELECT COUNT(*) FROM tariff_plans WHERE status = 'ACTIVE') AS active_tariffs,
                            (SELECT COUNT(*) FROM tariff_plans WHERE status = 'ARCHIVED') AS archived_tariffs
                        """)
                .query((result, rowNumber) -> new BillingMetricsSummary(
                        result.getLong("subscriptions_total"),
                        result.getLong("active_subscriptions"),
                        result.getLong("expired_subscriptions"),
                        result.getLong("cancelled_subscriptions"),
                        result.getLong("active_tariffs"),
                        result.getLong("archived_tariffs")))
                .single();
    }

    public List<SubscriptionResponse> findSubscriptions(
            UUID restaurantId,
            UUID planId,
            String status,
            int limit,
            int offset) {
        QuerySpec spec = subscriptionSpec(restaurantId, planId, status);
        return jdbc.sql(SUBSCRIPTION_SELECT + spec.where() + " ORDER BY s.created_at DESC, s.id LIMIT :limit OFFSET :offset")
                .params(spec.params())
                .param("limit", limit)
                .param("offset", offset)
                .query(PlatformBillingRepository::mapSubscription)
                .list();
    }

    public long countSubscriptions(UUID restaurantId, UUID planId, String status) {
        QuerySpec spec = subscriptionSpec(restaurantId, planId, status);
        return jdbc.sql("SELECT COUNT(*) FROM subscriptions s " + spec.where())
                .params(spec.params())
                .query(Long.class)
                .single();
    }

    public Optional<SubscriptionResponse> findSubscription(UUID subscriptionId) {
        return jdbc.sql(SUBSCRIPTION_SELECT + " WHERE s.id = :subscriptionId")
                .param("subscriptionId", subscriptionId)
                .query(PlatformBillingRepository::mapSubscription)
                .optional();
    }

    public boolean hasActiveSubscription(UUID restaurantId) {
        return jdbc.sql("""
                        SELECT EXISTS(
                            SELECT 1 FROM subscriptions
                            WHERE restaurant_id = :restaurantId AND status = 'ACTIVE'
                        )
                        """)
                .param("restaurantId", restaurantId)
                .query(Boolean.class)
                .single();
    }

    public UUID insertSubscription(
            UUID restaurantId,
            UUID planId,
            UUID actorUserId,
            String activationMode,
            Instant startsAt,
            Instant endsAt) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO subscriptions (
                            id, restaurant_id, plan_id, activated_by_user_id, status,
                            activation_mode, starts_at, ends_at
                        ) VALUES (
                            :id, :restaurantId, :planId, :actorUserId, 'ACTIVE',
                            :activationMode, :startsAt, :endsAt
                        )
                        """)
                .param("id", id)
                .param("restaurantId", restaurantId)
                .param("planId", planId)
                .param("actorUserId", actorUserId)
                .param("activationMode", activationMode)
                .param("startsAt", OffsetDateTime.ofInstant(startsAt, java.time.ZoneOffset.UTC))
                .param("endsAt", OffsetDateTime.ofInstant(endsAt, java.time.ZoneOffset.UTC))
                .update();
        return id;
    }

    public void extendSubscription(UUID subscriptionId, Instant endsAt) {
        jdbc.sql("UPDATE subscriptions SET ends_at = :endsAt WHERE id = :subscriptionId")
                .param("subscriptionId", subscriptionId)
                .param("endsAt", OffsetDateTime.ofInstant(endsAt, java.time.ZoneOffset.UTC))
                .update();
    }

    public void cancelSubscription(UUID subscriptionId, Instant cancelledAt) {
        jdbc.sql("""
                        UPDATE subscriptions
                        SET status = 'CANCELLED', cancelled_at = :cancelledAt
                        WHERE id = :subscriptionId
                        """)
                .param("subscriptionId", subscriptionId)
                .param("cancelledAt", OffsetDateTime.ofInstant(cancelledAt, java.time.ZoneOffset.UTC))
                .update();
    }

    public void insertSubscriptionEvent(
            UUID subscriptionId,
            UUID actorUserId,
            String eventType,
            String detailsJson) {
        jdbc.sql("""
                        INSERT INTO subscription_events (
                            subscription_id, actor_user_id, event_type, details
                        ) VALUES (
                            :subscriptionId, :actorUserId, :eventType, CAST(:details AS JSONB)
                        )
                        """)
                .param("subscriptionId", subscriptionId)
                .param("actorUserId", actorUserId)
                .param("eventType", eventType)
                .param("details", detailsJson)
                .update();
    }

    public void insertOutbox(UUID subscriptionId, String eventType, String payloadJson) {
        jdbc.sql("""
                        INSERT INTO outbox_events (aggregate_type, aggregate_id, event_type, payload)
                        VALUES ('SUBSCRIPTION', :subscriptionId, :eventType, CAST(:payload AS JSONB))
                        """)
                .param("subscriptionId", subscriptionId)
                .param("eventType", eventType)
                .param("payload", payloadJson)
                .update();
    }

    private TariffResponse mapTariff(ResultSet result, int rowNumber) throws SQLException {
        try {
            return new TariffResponse(
                    result.getObject("id", UUID.class),
                    result.getString("code"),
                    result.getString("name"),
                    result.getString("description"),
                    result.getBigDecimal("price"),
                    result.getString("currency").strip(),
                    result.getInt("duration_days"),
                    objectMapper.readTree(result.getString("features")),
                    result.getString("status"),
                    result.getObject("created_by_user_id", UUID.class),
                    instant(result, "created_at"),
                    instant(result, "updated_at"));
        } catch (Exception exception) {
            throw new SQLException("Invalid tariff features JSON", exception);
        }
    }

    private static SubscriptionResponse mapSubscription(ResultSet result, int rowNumber) throws SQLException {
        return new SubscriptionResponse(
                result.getObject("id", UUID.class),
                result.getObject("restaurant_id", UUID.class),
                result.getObject("plan_id", UUID.class),
                result.getString("plan_code"),
                result.getString("plan_name"),
                result.getObject("activated_by_membership_id", UUID.class),
                result.getObject("activated_by_user_id", UUID.class),
                result.getString("status"),
                result.getString("activation_mode"),
                instant(result, "starts_at"),
                instant(result, "ends_at"),
                instantNullable(result, "cancelled_at"),
                instant(result, "created_at"),
                instant(result, "updated_at"));
    }

    private static QuerySpec subscriptionSpec(UUID restaurantId, UUID planId, String status) {
        StringBuilder where = new StringBuilder("WHERE 1=1");
        java.util.Map<String, Object> params = new java.util.LinkedHashMap<>();
        if (restaurantId != null) {
            where.append(" AND s.restaurant_id = :restaurantId");
            params.put("restaurantId", restaurantId);
        }
        if (planId != null) {
            where.append(" AND s.plan_id = :planId");
            params.put("planId", planId);
        }
        if (status != null) {
            where.append(" AND s.status = :status");
            params.put("status", status);
        }
        return new QuerySpec(where.toString(), params);
    }

    private static Instant instant(ResultSet result, String column) throws SQLException {
        return result.getObject(column, OffsetDateTime.class).toInstant();
    }

    private static Instant instantNullable(ResultSet result, String column) throws SQLException {
        OffsetDateTime value = result.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private record QuerySpec(String where, java.util.Map<String, Object> params) {
    }
}
