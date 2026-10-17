package com.restaurant.notification.api;

import static com.restaurant.notification.api.NotificationDtos.CreateNotificationRequest;
import static com.restaurant.notification.api.NotificationDtos.NotificationResponse;

import java.util.List;
import java.util.UUID;

import com.restaurant.notification.application.NotificationService;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping
class NotificationController {

    private final NotificationService service;

    NotificationController(NotificationService service) {
        this.service = service;
    }

    @PostMapping("/internal/v1/notifications")
    @ResponseStatus(HttpStatus.CREATED)
    NotificationResponse create(@Valid @RequestBody CreateNotificationRequest request) {
        return service.create(request);
    }

    @GetMapping("/api/v1/me/notifications")
    List<NotificationResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return service.list(userId(jwt));
    }

    @PatchMapping("/api/v1/me/notifications/{notificationId}/read")
    NotificationResponse markRead(
            @PathVariable UUID notificationId,
            @AuthenticationPrincipal Jwt jwt) {
        return service.markRead(userId(jwt), notificationId);
    }

    @PostMapping("/api/v1/me/notifications/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void markAllRead(@AuthenticationPrincipal Jwt jwt) {
        service.markAllRead(userId(jwt));
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
