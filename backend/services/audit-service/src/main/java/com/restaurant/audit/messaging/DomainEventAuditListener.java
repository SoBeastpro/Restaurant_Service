package com.restaurant.audit.messaging;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import com.restaurant.audit.platform.infrastructure.PlatformAuditRepository;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class DomainEventAuditListener {

    private static final Set<String> PLATFORM_EVENTS = Set.of(
            "identity.user.status-changed.v1",
            "restaurant.restaurant.status-changed.v1",
            "billing.tariff.changed.v1",
            "billing.subscription.status-changed.v1");

    private final PlatformAuditRepository repository;
    private final ObjectMapper objectMapper;

    public DomainEventAuditListener(PlatformAuditRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = AuditMessagingConfiguration.QUEUE)
    @Transactional
    public void consume(Message message) throws Exception {
        JsonNode envelope = objectMapper.readTree(new String(message.getBody(), StandardCharsets.UTF_8));
        UUID eventId = UUID.fromString(requiredText(envelope, "eventId"));
        String eventType = requiredText(envelope, "eventType");
        String producer = requiredText(envelope, "producer");
        if (!repository.beginInbox(eventId, eventType, producer)) {
            return;
        }

        JsonNode payload = envelope.path("payload");
        UUID actorUserId = uuid(envelope, "actorUserId");
        UUID restaurantId = uuid(envelope, "restaurantId");
        UUID subjectUserId = uuid(payload, "userId");
        UUID entityId = uuid(envelope, "aggregateId");
        UUID correlationId = uuid(envelope, "correlationId");
        Instant occurredAt = Instant.parse(requiredText(envelope, "occurredAt"));
        String aggregateType = text(envelope, "aggregateType");
        String scope = PLATFORM_EVENTS.contains(eventType) && actorUserId != null
                ? "PLATFORM"
                : restaurantId == null ? "PERSONAL" : "RESTAURANT";
        String action = eventType.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_");

        repository.insertAuditEvent(
                eventId,
                actorUserId,
                subjectUserId,
                restaurantId,
                scope,
                action,
                aggregateType,
                entityId,
                correlationId,
                payload.toString(),
                occurredAt);
        repository.insertSystemEvent(
                "INFO",
                producer,
                eventType,
                "Обработано доменное событие " + eventType,
                correlationId,
                payload.toString(),
                occurredAt);
        repository.completeInbox(eventId);
    }

    private static String requiredText(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing event field: " + field);
        }
        return value;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }

    private static UUID uuid(JsonNode node, String field) {
        String value = text(node, field);
        return value == null || value.isBlank() ? null : UUID.fromString(value);
    }
}
