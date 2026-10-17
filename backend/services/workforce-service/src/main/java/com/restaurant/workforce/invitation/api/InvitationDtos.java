package com.restaurant.workforce.invitation.api;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class InvitationDtos {

    private InvitationDtos() {
    }

    public record CreateInvitationRequest(
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName,
            @NotBlank String role,
            String position) {
    }

    public record AcceptInvitationRequest(
            @NotBlank @Size(min = 3, max = 100) String login,
            @NotBlank @Size(min = 8, max = 72) String password) {
    }

    public record InvitationResponse(
            UUID id,
            UUID employeeId,
            UUID restaurantId,
            String firstName,
            String lastName,
            String role,
            String position,
            String status,
            String invitationUrl,
            String qrContent,
            Instant expiresAt) {
    }

    public record PublicInvitationResponse(
            UUID restaurantId,
            String firstName,
            String lastName,
            String role,
            String position,
            Instant expiresAt) {
    }

    public record TokenPair(
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresIn) {
    }
}
