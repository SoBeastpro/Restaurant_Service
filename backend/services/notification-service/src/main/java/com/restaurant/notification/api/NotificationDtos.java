package com.restaurant.notification.api;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class NotificationDtos {

    private NotificationDtos() {
    }

    public record CreateNotificationRequest(
            @NotNull UUID userId,
            UUID restaurantId,
            @NotBlank @Size(max = 50) String type,
            @NotBlank @Size(max = 200) String title,
            @NotBlank String message,
            @NotNull Map<String, Object> payload) {
    }

    public record NotificationResponse(
            UUID id,
            UUID restaurantId,
            String type,
            String title,
            String message,
            Map<String, Object> payload,
            Instant readAt,
            Instant createdAt) {
    }
}
