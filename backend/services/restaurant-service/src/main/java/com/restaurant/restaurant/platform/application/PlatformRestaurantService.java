package com.restaurant.restaurant.platform.application;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.restaurant.foundation.web.ApiException;
import com.restaurant.foundation.web.PageResponse;
import com.restaurant.restaurant.platform.api.PlatformRestaurantDtos.RestaurantResponse;
import com.restaurant.restaurant.platform.api.PlatformRestaurantDtos.UpdateRestaurantStatusRequest;
import com.restaurant.restaurant.platform.infrastructure.PlatformRestaurantRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformRestaurantService {

    private static final List<String> STATUSES = List.of("ACTIVE", "BLOCKED", "ARCHIVED");

    private final PlatformRestaurantRepository repository;

    public PlatformRestaurantService(PlatformRestaurantRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public PageResponse<RestaurantResponse> list(String query, String status, int page, int size) {
        if (page < 0) {
            throw validation("PAGE_INVALID", "Номер страницы не может быть отрицательным.");
        }
        if (size < 1 || size > 100) {
            throw validation("PAGE_SIZE_INVALID", "Размер страницы должен быть от 1 до 100.");
        }
        String normalizedQuery = query == null || query.isBlank() ? null : query.strip();
        String normalizedStatus = status == null || status.isBlank() ? null : normalizeStatus(status);
        long total = repository.countRestaurants(normalizedQuery, normalizedStatus);
        return PageResponse.of(
                repository.findRestaurants(normalizedQuery, normalizedStatus, size, page * size),
                page,
                size,
                total);
    }

    @Transactional(readOnly = true)
    public RestaurantResponse get(UUID restaurantId) {
        return required(restaurantId);
    }

    @Transactional
    public RestaurantResponse updateStatus(
            UUID restaurantId,
            UUID actorUserId,
            UpdateRestaurantStatusRequest request) {
        RestaurantResponse current = required(restaurantId);
        String status = normalizeStatus(request.status());
        if ("ARCHIVED".equals(current.status()) && !"ARCHIVED".equals(status)) {
            throw conflict("RESTAURANT_ARCHIVED", "Архивированный ресторан нельзя вернуть в работу.");
        }
        if (current.status().equals(status)) {
            return current;
        }
        repository.updateStatus(restaurantId, status);
        repository.insertStatusEvent(
                restaurantId,
                actorUserId,
                current.directorUserId(),
                current.status(),
                status,
                request.reason() == null || request.reason().isBlank() ? null : request.reason().strip());
        return required(restaurantId);
    }

    private RestaurantResponse required(UUID restaurantId) {
        return repository.findRestaurant(restaurantId).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND,
                "PLATFORM_RESTAURANT_NOT_FOUND",
                "Ресторан не найден."));
    }

    private static String normalizeStatus(String value) {
        String normalized = value.strip().toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(normalized)) {
            throw validation("RESTAURANT_STATUS_INVALID", "Неизвестный статус ресторана.");
        }
        return normalized;
    }

    private static ApiException validation(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    private static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }
}
