package com.restaurant.reporting.platform.infrastructure;

import com.restaurant.foundation.security.InternalApiAuthorizer;
import com.restaurant.foundation.web.ApiException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class PlatformSummaryClient {

    private final RestClient identity;
    private final RestClient restaurant;
    private final RestClient billing;
    private final String internalApiSecret;

    public PlatformSummaryClient(
            RestClient.Builder builder,
            @Value("${services.identity.url}") String identityUrl,
            @Value("${services.restaurant.url}") String restaurantUrl,
            @Value("${services.billing.url}") String billingUrl,
            @Value("${internal.api-secret}") String internalApiSecret) {
        this.identity = builder.clone().baseUrl(identityUrl).build();
        this.restaurant = builder.clone().baseUrl(restaurantUrl).build();
        this.billing = builder.clone().baseUrl(billingUrl).build();
        this.internalApiSecret = internalApiSecret;
    }

    public PlatformSummary load() {
        try {
            return new PlatformSummary(
                    get(identity, UserSummary.class),
                    get(restaurant, RestaurantSummary.class),
                    get(billing, BillingSummary.class));
        } catch (RestClientException | IllegalStateException exception) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "PLATFORM_METRICS_SOURCE_UNAVAILABLE",
                    "Один из источников платформенных метрик временно недоступен.");
        }
    }

    private <T> T get(RestClient client, Class<T> type) {
        T response = client.get()
                .uri("/internal/v1/platform/metrics-summary")
                .header(InternalApiAuthorizer.HEADER, internalApiSecret)
                .retrieve()
                .body(type);
        if (response == null) {
            throw new IllegalStateException("Empty platform summary response");
        }
        return response;
    }

    public record PlatformSummary(UserSummary users, RestaurantSummary restaurants, BillingSummary billing) {
    }

    public record UserSummary(long total, long active, long blocked, long archived) {
    }

    public record RestaurantSummary(long total, long active, long blocked, long archived) {
    }

    public record BillingSummary(
            long subscriptionsTotal,
            long activeSubscriptions,
            long expiredSubscriptions,
            long cancelledSubscriptions,
            long activeTariffs,
            long archivedTariffs) {
    }
}
