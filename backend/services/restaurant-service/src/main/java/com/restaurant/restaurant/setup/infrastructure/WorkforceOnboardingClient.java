package com.restaurant.restaurant.setup.infrastructure;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class WorkforceOnboardingClient {

    private final RestClient restClient;

    public WorkforceOnboardingClient(
            RestClient.Builder builder,
            @Value("${services.workforce.url}") String workforceServiceUrl) {
        this.restClient = builder.baseUrl(workforceServiceUrl).build();
    }

    public void createDirector(UUID restaurantId, UUID userId, String firstName, String lastName) {
        restClient.post()
                .uri("/internal/v1/restaurants/{restaurantId}/director", restaurantId)
                .body(new CreateDirectorRequest(userId, firstName, lastName))
                .retrieve()
                .toBodilessEntity();
    }

    public record CreateDirectorRequest(UUID userId, String firstName, String lastName) {
    }
}
