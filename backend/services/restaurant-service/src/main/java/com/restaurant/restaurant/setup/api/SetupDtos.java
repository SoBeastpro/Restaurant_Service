package com.restaurant.restaurant.setup.api;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class SetupDtos {

    private SetupDtos() {
    }

    public record CreateRestaurantRequest(
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(max = 500) String address,
            @NotNull @Size(min = 7, max = 7) List<@Valid OpeningHoursRequest> openingHours) {
    }

    public record OpeningHoursRequest(
            @NotNull DayOfWeek dayOfWeek,
            boolean closed,
            LocalTime opensAt,
            LocalTime closesAt) {
    }

    public record OpeningHoursResponse(
            DayOfWeek dayOfWeek,
            boolean closed,
            LocalTime opensAt,
            LocalTime closesAt,
            boolean closesNextDay) {
    }

    public record RestaurantResponse(
            UUID id,
            String name,
            String address,
            String status,
            List<OpeningHoursResponse> openingHours,
            Instant createdAt) {
    }

    public record CreateHallRequest(
            @NotBlank @Size(max = 100) String name,
            @Min(0) Integer displayOrder) {
    }

    public record HallResponse(
            UUID id,
            UUID restaurantId,
            String name,
            int displayOrder,
            String status) {
    }

    public record CreateTableRequest(
            @NotBlank @Size(max = 20) String number,
            @Min(1) @Max(100) int seatsCount,
            @NotNull @DecimalMin("0.0") BigDecimal positionX,
            @NotNull @DecimalMin("0.0") BigDecimal positionY,
            @NotBlank String shape) {
    }

    public record UpdateTableRequest(
            @Size(min = 1, max = 20) String number,
            @Min(1) @Max(100) Integer seatsCount,
            @DecimalMin("0.0") BigDecimal positionX,
            @DecimalMin("0.0") BigDecimal positionY,
            String shape) {
    }

    public record TableResponse(
            UUID id,
            UUID restaurantId,
            UUID hallId,
            String number,
            int seatsCount,
            BigDecimal positionX,
            BigDecimal positionY,
            String shape,
            String status) {
    }
}
