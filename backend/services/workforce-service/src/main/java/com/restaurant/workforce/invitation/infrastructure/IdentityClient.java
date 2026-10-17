package com.restaurant.workforce.invitation.infrastructure;

import java.util.UUID;

import com.restaurant.workforce.invitation.api.InvitationDtos.TokenPair;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class IdentityClient {

    private final RestClient restClient;

    public IdentityClient(RestClient.Builder builder, @Value("${services.identity.url}") String identityUrl) {
        this.restClient = builder.baseUrl(identityUrl).build();
    }

    public InvitedRegistrationResult register(InvitedRegistrationRequest request) {
        return restClient.post()
                .uri("/internal/v1/users/invitations")
                .body(request)
                .retrieve()
                .body(InvitedRegistrationResult.class);
    }

    public record InvitedRegistrationRequest(
            String login,
            String password,
            String firstName,
            String lastName,
            String role,
            String position,
            UUID restaurantId) {
    }

    public record InvitedRegistrationResult(UUID userId, TokenPair tokens) {
    }
}
