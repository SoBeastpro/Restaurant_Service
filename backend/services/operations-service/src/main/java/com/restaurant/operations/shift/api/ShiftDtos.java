package com.restaurant.operations.shift.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class ShiftDtos {

    private ShiftDtos() {
    }

    public record CreateShiftRequest(
            @NotNull Instant scheduledStartAt,
            @NotNull Instant scheduledEndAt) {
    }

    public record ConfigureShiftRequest(@NotNull List<@Valid ParticipantRequest> participants) {
    }

    public record ParticipantRequest(
            @NotNull UUID membershipId,
            @NotEmpty List<@NotNull UUID> tableIds) {
    }

    public record CreateTaskRequest(
            @NotBlank @Size(max = 200) String description,
            @NotNull UUID assignedToMembershipId,
            @NotBlank String deadlineType,
            Instant deadlineAt) {
    }

    public record ChangeTaskStatusRequest(
            @NotBlank String status,
            @Size(max = 1000) String comment) {
    }

    public record AssignTaskRequest(@NotNull UUID assignedToMembershipId) {
    }

    public record AssignCarriedTaskRequest(
            @NotNull UUID assignedToMembershipId,
            @NotBlank String deadlineType,
            Instant deadlineAt) {
    }

    public record CloseShiftRequest(@NotNull List<@Valid TaskResolutionRequest> unfinishedTasks) {
    }

    public record TaskResolutionRequest(
            @NotNull UUID taskId,
            @NotBlank String action,
            @Size(max = 1000) String comment) {
    }

    public record ShiftResponse(
            UUID id,
            UUID restaurantId,
            String name,
            Instant scheduledStartAt,
            Instant scheduledEndAt,
            Instant actualStartedAt,
            Instant actualClosedAt,
            String status,
            UUID responsibleMembershipId,
            List<ParticipantResponse> participants,
            List<TaskResponse> tasks) {
    }

    public record ShiftSummaryResponse(
            UUID id,
            String name,
            Instant scheduledStartAt,
            Instant scheduledEndAt,
            Instant actualStartedAt,
            Instant actualClosedAt,
            String status,
            UUID responsibleMembershipId,
            int employeeCount,
            int taskCount,
            int completedTaskCount) {
    }

    public record ShiftReportResponse(
            UUID shiftId,
            UUID restaurantId,
            Instant actualStartedAt,
            Instant actualClosedAt,
            UUID managerMembershipId,
            List<UUID> employeeMembershipIds,
            int totalTasks,
            int completedTasks,
            int cancelledTasks,
            int carriedOverTasks) {
    }

    public record ParticipantResponse(UUID membershipId, String position, List<UUID> tableIds) {
    }

    public record TaskResponse(
            UUID id,
            UUID shiftId,
            String description,
            UUID assignedToMembershipId,
            String status,
            String deadlineType,
            Instant deadlineAt,
            Instant completedAt,
            String cancellationReason,
            Instant createdAt) {
    }

    public record TaskHistoryResponse(
            UUID id,
            String eventType,
            UUID actorMembershipId,
            String oldStatus,
            String newStatus,
            UUID oldAssigneeMembershipId,
            UUID newAssigneeMembershipId,
            String comment,
            Instant createdAt) {
    }

    public record CurrentShiftResponse(
            UUID id,
            UUID restaurantId,
            Instant scheduledStartAt,
            Instant scheduledEndAt,
            Instant actualStartedAt,
            String status,
            List<UUID> assignedTableIds,
            List<TaskResponse> tasks) {
    }
}
