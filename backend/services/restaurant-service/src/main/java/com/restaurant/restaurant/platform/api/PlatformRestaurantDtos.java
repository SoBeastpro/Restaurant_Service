package com.restaurant.restaurant.platform.api;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class PlatformRestaurantDtos {

    private PlatformRestaurantDtos() {
    }

    public record RestaurantResponse(
            UUID id,
            UUID directorUserId,
            String name,
            String address,
            String phone,
            String description,
            String status,
            int hallsCount,
            int tablesCount,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record UpdateRestaurantStatusRequest(
            @NotBlank String status,
            @Size(max = 500) String reason) {
    }
}
