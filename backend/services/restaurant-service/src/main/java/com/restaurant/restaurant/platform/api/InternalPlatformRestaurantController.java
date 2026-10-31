package com.restaurant.restaurant.platform.api;

import java.util.UUID;

import com.restaurant.foundation.security.InternalApiAuthorizer;
import com.restaurant.foundation.web.ApiException;
import com.restaurant.restaurant.platform.infrastructure.PlatformRestaurantRepository;
import com.restaurant.restaurant.platform.infrastructure.PlatformRestaurantRepository.RestaurantMetricsSummary;
import com.restaurant.restaurant.platform.infrastructure.PlatformRestaurantRepository.RestaurantStatus;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1")
class InternalPlatformRestaurantController {

    private final PlatformRestaurantRepository repository;
    private final String internalApiSecret;

    InternalPlatformRestaurantController(
            PlatformRestaurantRepository repository,
            @Value("${internal.api-secret}") String internalApiSecret) {
        this.repository = repository;
        this.internalApiSecret = internalApiSecret;
    }

    @GetMapping("/platform/metrics-summary")
    RestaurantMetricsSummary summary(@RequestHeader HttpHeaders headers) {
        authorize(headers);
        return repository.metricsSummary();
    }

    @GetMapping("/restaurants/{restaurantId}/status")
    RestaurantStatus status(@PathVariable UUID restaurantId, @RequestHeader HttpHeaders headers) {
        authorize(headers);
        return repository.findStatus(restaurantId).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND, "RESTAURANT_NOT_FOUND", "Ресторан не найден."));
    }

    private void authorize(HttpHeaders headers) {
        InternalApiAuthorizer.require(headers.getFirst(InternalApiAuthorizer.HEADER), internalApiSecret);
    }
}
