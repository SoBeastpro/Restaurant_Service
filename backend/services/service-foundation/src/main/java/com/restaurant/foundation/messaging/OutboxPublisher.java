package com.restaurant.foundation.messaging;

import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

public class OutboxPublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(OutboxPublisher.class);

    private final JdbcClient jdbc;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final String producer;
    private final int batchSize;

    public OutboxPublisher(
            JdbcClient jdbc,
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper,
            @Value("${spring.application.name}") String producer,
            @Value("${restaurant.outbox.batch-size:50}") int batchSize) {
        this.jdbc = jdbc;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.producer = producer;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${restaurant.outbox.poll-interval:500ms}")
    @Transactional
    public void publishAvailable() {
        List<OutboxRecord> events = jdbc.sql("""
                        SELECT id, aggregate_type, aggregate_id, event_type, event_version,
                               payload::TEXT AS payload, headers::TEXT AS headers, occurred_at
                        FROM outbox_events
                        WHERE published_at IS NULL AND available_at <= CURRENT_TIMESTAMP
                        ORDER BY occurred_at, id
                        FOR UPDATE SKIP LOCKED
                        LIMIT :limit
                        """)
                .param("limit", batchSize)
                .query(OutboxPublisher::map)
                .list();
        for (OutboxRecord event : events) {
            publish(event);
        }
    }

    private void publish(OutboxRecord event) {
        try {
            String body = objectMapper.writeValueAsString(envelope(event));
            rabbitTemplate.send(
                    DomainEventTopology.EXCHANGE,
                    event.eventType(),
                    MessageBuilder.withBody(body.getBytes(StandardCharsets.UTF_8))
                            .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                            .setMessageId(event.id().toString())
                            .setHeader("eventType", event.eventType())
                            .build());
            jdbc.sql("""
                            UPDATE outbox_events
                            SET published_at = CURRENT_TIMESTAMP,
                                attempts_count = attempts_count + 1,
                                last_error = NULL
                            WHERE id = :id
                            """)
                    .param("id", event.id())
                    .update();
        } catch (Exception exception) {
            String error = exception.getMessage() == null
                    ? exception.getClass().getSimpleName()
                    : exception.getMessage();
            jdbc.sql("""
                            UPDATE outbox_events
                            SET attempts_count = attempts_count + 1,
                                last_error = LEFT(:error, 2000),
                                available_at = CURRENT_TIMESTAMP
                                    + LEAST(INTERVAL '5 minutes',
                                            INTERVAL '5 seconds' * POWER(2, LEAST(attempts_count, 6)))
                            WHERE id = :id
                            """)
                    .param("id", event.id())
                    .param("error", error)
                    .update();
            LOGGER.warn("Cannot publish outbox event {} ({})", event.id(), event.eventType(), exception);
        }
    }

    private ObjectNode envelope(OutboxRecord event) throws Exception {
        JsonNode parsedPayload = objectMapper.readTree(event.payload());
        ObjectNode payload = parsedPayload.isObject()
                ? (ObjectNode) parsedPayload
                : objectMapper.createObjectNode();
        enrichAggregateId(payload, event.aggregateType(), event.aggregateId());

        ObjectNode envelope = objectMapper.createObjectNode();
        envelope.put("eventId", event.id().toString());
        envelope.put("eventType", event.eventType());
        envelope.put("eventVersion", event.eventVersion());
        envelope.put("occurredAt", event.occurredAt().toInstant().toString());
        envelope.put("producer", producer);
        envelope.put("aggregateType", event.aggregateType());
        envelope.put("aggregateId", event.aggregateId().toString());
        envelope.put("correlationId", correlationId(event).toString());
        copyText(payload, envelope, "actorUserId");
        copyText(payload, envelope, "restaurantId");
        envelope.set("payload", payload);
        return envelope;
    }

    private UUID correlationId(OutboxRecord event) {
        try {
            JsonNode headers = objectMapper.readTree(event.headers());
            JsonNode value = headers.get("correlationId");
            return value == null ? event.id() : UUID.fromString(value.asString());
        } catch (Exception ignored) {
            return event.id();
        }
    }

    private static void enrichAggregateId(ObjectNode payload, String aggregateType, UUID aggregateId) {
        String normalized = aggregateType.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
        String field = switch (normalized) {
            case "user" -> "userId";
            case "restaurant" -> "restaurantId";
            case "subscription" -> "subscriptionId";
            case "tariff", "tariffplan" -> "tariffId";
            case "shift" -> "shiftId";
            case "task" -> "taskId";
            case "diningtable", "table" -> "tableId";
            case "notification" -> "notificationId";
            default -> "aggregateId";
        };
        if (!payload.has(field)) {
            payload.put(field, aggregateId.toString());
        }
    }

    private static void copyText(ObjectNode from, ObjectNode to, String field) {
        JsonNode value = from.get(field);
        if (value != null && !value.isNull()) {
            to.put(field, value.asString());
        }
    }

    private static OutboxRecord map(ResultSet result, int rowNumber) throws SQLException {
        return new OutboxRecord(
                result.getObject("id", UUID.class),
                result.getString("aggregate_type"),
                result.getObject("aggregate_id", UUID.class),
                result.getString("event_type"),
                result.getInt("event_version"),
                result.getString("payload"),
                result.getString("headers"),
                result.getObject("occurred_at", OffsetDateTime.class));
    }

    private record OutboxRecord(
            UUID id,
            String aggregateType,
            UUID aggregateId,
            String eventType,
            int eventVersion,
            String payload,
            String headers,
            OffsetDateTime occurredAt) {
    }
}
