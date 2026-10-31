package com.restaurant.billing.platform.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import tools.jackson.databind.JsonNode;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class PlatformBillingDtos {

    private PlatformBillingDtos() {
    }

    public record TariffResponse(
            UUID id,
            String code,
            String name,
            String description,
            BigDecimal price,
            String currency,
            int durationDays,
            JsonNode features,
            String status,
            UUID createdByUserId,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record CreateTariffRequest(
            @NotBlank @Size(max = 50) String code,
            @NotBlank @Size(max = 100) String name,
            String description,
            @NotNull @DecimalMin("0.0") BigDecimal price,
            @NotBlank @Pattern(regexp = "[A-Za-z]{3}") String currency,
            @Min(1) @Max(3650) int durationDays,
            JsonNode features) {
    }

    public record UpdateTariffRequest(
            @Size(min = 1, max = 100) String name,
            String description,
            @DecimalMin("0.0") BigDecimal price,
            @Pattern(regexp = "[A-Za-z]{3}") String currency,
            @Min(1) @Max(3650) Integer durationDays,
            JsonNode features,
            String status) {
    }

    public record SubscriptionResponse(
            UUID id,
            UUID restaurantId,
            UUID planId,
            String planCode,
            String planName,
            UUID activatedByMembershipId,
            UUID activatedByUserId,
            String status,
            String activationMode,
            Instant startsAt,
            Instant endsAt,
            Instant cancelledAt,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record ActivateSubscriptionRequest(
            @NotNull UUID restaurantId,
            @NotNull UUID planId,
            Instant startsAt,
            @Min(1) @Max(3650) Integer durationDays,
            String activationMode) {
    }

    public record ExtendSubscriptionRequest(
            @Min(1) @Max(3650) int days,
            @Size(max = 500) String reason) {
    }

    public record CancelSubscriptionRequest(@Size(max = 500) String reason) {
    }
}
