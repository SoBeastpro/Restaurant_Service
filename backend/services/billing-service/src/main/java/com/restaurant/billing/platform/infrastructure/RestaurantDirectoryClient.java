package com.restaurant.billing.platform.infrastructure;

import java.util.UUID;

import com.restaurant.foundation.security.InternalApiAuthorizer;
import com.restaurant.foundation.web.ApiException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class RestaurantDirectoryClient {

    private final RestClient restClient;
    private final String internalApiSecret;

    public RestaurantDirectoryClient(
            RestClient.Builder builder,
            @Value("${services.restaurant.url}") String restaurantServiceUrl,
            @Value("${internal.api-secret}") String internalApiSecret) {
        this.restClient = builder.baseUrl(restaurantServiceUrl).build();
        this.internalApiSecret = internalApiSecret;
    }

    public RestaurantStatus requireSubscriptionTarget(UUID restaurantId) {
        try {
            RestaurantStatus restaurant = restClient.get()
                    .uri("/internal/v1/restaurants/{restaurantId}/status", restaurantId)
                    .header(InternalApiAuthorizer.HEADER, internalApiSecret)
                    .retrieve()
                    .body(RestaurantStatus.class);
            if (restaurant == null) {
                throw unavailable();
            }
            if ("ARCHIVED".equals(restaurant.status())) {
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "RESTAURANT_ARCHIVED",
                        "Нельзя активировать подписку для архивированного ресторана.");
            }
            return restaurant;
        } catch (HttpClientErrorException.NotFound exception) {
            throw new ApiException(HttpStatus.NOT_FOUND, "RESTAURANT_NOT_FOUND", "Ресторан не найден.");
        } catch (ApiException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw unavailable();
        }
    }

    private static ApiException unavailable() {
        return new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "RESTAURANT_DIRECTORY_UNAVAILABLE",
                "Сервис ресторанов временно недоступен.");
    }

    public record RestaurantStatus(UUID id, String status) {
    }
}
