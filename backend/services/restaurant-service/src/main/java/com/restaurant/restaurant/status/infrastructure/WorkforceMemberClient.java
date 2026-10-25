package com.restaurant.restaurant.status.infrastructure;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class WorkforceMemberClient {

    private final RestClient restClient;

    public WorkforceMemberClient(
            RestClient.Builder builder,
            @Value("${services.workforce.url}") String workforceServiceUrl) {
        this.restClient = builder.baseUrl(workforceServiceUrl).build();
    }

    public MemberReference findByUser(UUID restaurantId, UUID userId) {
        return restClient.get()
                .uri("/internal/v1/restaurants/{restaurantId}/members/by-user/{userId}", restaurantId, userId)
                .retrieve()
                .body(MemberReference.class);
    }

    public MemberReference findByMembership(UUID restaurantId, UUID membershipId) {
        return restClient.get()
                .uri("/internal/v1/restaurants/{restaurantId}/members/{membershipId}", restaurantId, membershipId)
                .retrieve()
                .body(MemberReference.class);
    }

    public record MemberReference(
            UUID membershipId,
            UUID restaurantId,
            UUID userId,
            String role,
            String position,
            String status) {
    }
}
