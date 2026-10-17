package com.restaurant.identity.auth.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Size(min = 3, max = 100) String login,
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName,
            @NotBlank @Size(min = 8, max = 72) String password) {
    }

    public record InvitedRegistrationRequest(
            @NotBlank @Size(min = 3, max = 100) String login,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName,
            @NotBlank String role,
            String position,
            UUID restaurantId) {
    }

    public record InvitedRegistrationResult(UUID userId, TokenPair tokens) {
    }

    public record AssignRestaurantRequest(@NotNull UUID restaurantId) {
    }

    public record DirectorRestaurantLink(UUID userId, String firstName, String lastName) {
    }

    public record LoginRequest(
            @NotBlank @Size(max = 100) String login,
            @NotBlank @Size(max = 72) String password) {
    }

    public record RefreshTokenRequest(@NotBlank String refreshToken) {
    }

    public record ChangePasswordRequest(
            @NotBlank @Size(max = 72) String currentPassword,
            @NotBlank @Size(min = 8, max = 72) String newPassword) {
    }

    public record TokenPair(
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresIn) {
    }

    public record UserProfile(
            UUID id,
            String login,
            String email,
            String phone,
            String firstName,
            String lastName,
            String status,
            String role,
            String position,
            UUID restaurantId,
            List<String> platformRoles,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record SessionResponse(
            UUID id,
            boolean current,
            Instant createdAt,
            Instant lastActiveAt,
            Instant expiresAt,
            String ipAddress,
            String userAgent,
            String deviceName,
            String platform,
            String appVersion) {
    }
}
