package com.restaurant.identity.platform.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class PlatformUserDtos {

    private PlatformUserDtos() {
    }

    public record UserResponse(
            UUID id,
            String login,
            String email,
            String phone,
            String firstName,
            String lastName,
            String role,
            String position,
            UUID restaurantId,
            String status,
            List<String> platformRoles,
            Instant lastLoginAt,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record UpdateUserStatusRequest(
            @NotBlank String status,
            @Size(max = 500) String reason) {
    }
}
