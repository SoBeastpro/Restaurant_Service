package com.restaurant.restaurant.status.api;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class TableStatusDtos {

    private TableStatusDtos() {
    }

    public record ChangeTableStatusRequest(
            @NotBlank String status,
            @Size(max = 1000) String comment) {
    }

    public record TableStatusResponse(
            UUID tableId,
            String tableNumber,
            UUID shiftId,
            String status,
            String comment,
            UUID changedByMembershipId,
            Instant changedAt) {
    }

    public record TableStatusHistoryResponse(
            UUID id,
            UUID tableId,
            String tableNumber,
            UUID shiftId,
            UUID changedByMembershipId,
            String oldStatus,
            String newStatus,
            String comment,
            Instant createdAt) {
    }
}
