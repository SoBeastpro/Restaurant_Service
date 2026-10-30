package com.restaurant.reporting.messaging;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import com.restaurant.reporting.platform.infrastructure.PlatformMetricsRepository;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class ActivityMetricsListener {

    private final PlatformMetricsRepository repository;
    private final ObjectMapper objectMapper;

    public ActivityMetricsListener(PlatformMetricsRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = ReportingMessagingConfiguration.QUEUE)
    @Transactional
    public void consume(Message message) throws Exception {
        JsonNode event = objectMapper.readTree(new String(message.getBody(), StandardCharsets.UTF_8));
        UUID eventId = UUID.fromString(requiredText(event, "eventId"));
        String eventType = requiredText(event, "eventType");
        String producer = requiredText(event, "producer");
        if (!repository.beginInbox(eventId, eventType, producer)) {
            return;
        }

        UUID restaurantId = UUID.fromString(requiredText(event, "restaurantId"));
        var metricDate = Instant.parse(requiredText(event, "occurredAt")).atZone(ZoneOffset.UTC).toLocalDate();
        JsonNode payload = event.path("payload");
        switch (eventType) {
            case "operations.shift.started.v1" -> repository.increment(restaurantId, metricDate, "shifts_opened");
            case "operations.shift.closed.v1" -> repository.increment(restaurantId, metricDate, "shifts_closed");
            case "operations.task.created.v1" -> repository.increment(restaurantId, metricDate, "tasks_created");
            case "operations.task.status-changed.v1" -> {
                String status = requiredText(payload, "status");
                if ("COMPLETED".equals(status)) {
                    repository.increment(restaurantId, metricDate, "tasks_completed");
                } else if ("CANCELLED".equals(status)) {
                    repository.increment(restaurantId, metricDate, "tasks_cancelled");
                }
            }
            case "restaurant.table.status-changed.v1" ->
                    repository.increment(restaurantId, metricDate, "table_status_changes");
            default -> {
                // The queue uses topic bindings; unrelated future versions are acknowledged safely.
            }
        }
        repository.completeInbox(eventId);
    }

    private static String requiredText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.asString().isBlank()) {
            throw new IllegalArgumentException("Missing event field: " + field);
        }
        return value.asString();
    }
}
