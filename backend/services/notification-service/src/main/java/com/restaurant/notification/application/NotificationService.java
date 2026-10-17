package com.restaurant.notification.application;

import static com.restaurant.notification.api.NotificationDtos.CreateNotificationRequest;
import static com.restaurant.notification.api.NotificationDtos.NotificationResponse;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.restaurant.foundation.web.ApiException;
import com.restaurant.notification.infrastructure.NotificationRepository;
import com.restaurant.notification.infrastructure.NotificationRepository.NotificationRecord;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;

@Service
public class NotificationService {

    private final NotificationRepository repository;
    private final ObjectMapper objectMapper;

    public NotificationService(NotificationRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public NotificationResponse create(CreateNotificationRequest request) {
        UUID id = UUID.randomUUID();
        repository.insert(
                id,
                request.userId(),
                request.restaurantId(),
                request.type().strip(),
                request.title().strip(),
                request.message().strip(),
                objectMapper.writeValueAsString(request.payload()));
        return response(required(id, request.userId()));
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> list(UUID userId) {
        return repository.findByUser(userId).stream().map(this::response).toList();
    }

    @Transactional
    public NotificationResponse markRead(UUID userId, UUID notificationId) {
        required(notificationId, userId);
        repository.markRead(notificationId, userId);
        return response(required(notificationId, userId));
    }

    @Transactional
    public void markAllRead(UUID userId) {
        repository.markAllRead(userId);
    }

    private NotificationRecord required(UUID id, UUID userId) {
        return repository.find(id, userId).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND,
                "NOTIFICATION_NOT_FOUND",
                "Уведомление не найдено."));
    }

    @SuppressWarnings("unchecked")
    private NotificationResponse response(NotificationRecord notification) {
        Map<String, Object> payload = objectMapper.readValue(notification.payloadJson(), Map.class);
        return new NotificationResponse(
                notification.id(),
                notification.restaurantId(),
                notification.type(),
                notification.title(),
                notification.message(),
                payload,
                notification.readAt(),
                notification.createdAt());
    }
}
