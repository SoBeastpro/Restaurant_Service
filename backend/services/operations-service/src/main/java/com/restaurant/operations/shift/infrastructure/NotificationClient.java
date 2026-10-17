package com.restaurant.operations.shift.infrastructure;

import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class NotificationClient {

    private final RestClient restClient;

    public NotificationClient(
            RestClient.Builder builder,
            @Value("${services.notification.url}") String notificationServiceUrl) {
        this.restClient = builder.baseUrl(notificationServiceUrl).build();
    }

    public void create(
            UUID userId,
            UUID restaurantId,
            String type,
            String title,
            String message,
            Map<String, Object> payload) {
        restClient.post()
                .uri("/internal/v1/notifications")
                .body(new CreateNotificationRequest(userId, restaurantId, type, title, message, payload))
                .retrieve()
                .toBodilessEntity();
    }

    private record CreateNotificationRequest(
            UUID userId,
            UUID restaurantId,
            String type,
            String title,
            String message,
            Map<String, Object> payload) {
    }
}
