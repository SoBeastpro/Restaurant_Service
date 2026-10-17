package com.restaurant.workforce.member.api;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public final class MemberDtos {

    private MemberDtos() {
    }

    public record EmployeeResponse(
            UUID membershipId,
            UUID userId,
            String firstName,
            String lastName,
            String position,
            String status) {
    }

    public record MemberReferenceResponse(
            UUID membershipId,
            UUID restaurantId,
            UUID userId,
            String role,
            String position,
            String status) {
    }

    public record EmployeeAssignmentResponse(UUID membershipId, String position) {
    }

    public record ValidateEmployeesRequest(@NotNull List<@NotNull UUID> membershipIds) {
    }

    public record ValidateEmployeesResponse(List<EmployeeAssignmentResponse> employees) {
    }
}
