package com.restaurant.restaurant.setup.infrastructure;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class IdentityOnboardingClient {

    private final RestClient restClient;

    public IdentityOnboardingClient(
            RestClient.Builder builder,
            @Value("${services.identity.url}") String identityServiceUrl) {
        this.restClient = builder.baseUrl(identityServiceUrl).build();
    }

    public DirectorProfile assignRestaurant(UUID userId, UUID restaurantId) {
        return restClient.post()
                .uri("/internal/v1/users/{userId}/restaurant", userId)
                .body(new AssignRestaurantRequest(restaurantId))
                .retrieve()
                .body(DirectorProfile.class);
    }

    public record AssignRestaurantRequest(UUID restaurantId) {
    }

    public record DirectorProfile(UUID userId, String firstName, String lastName) {
    }
}
