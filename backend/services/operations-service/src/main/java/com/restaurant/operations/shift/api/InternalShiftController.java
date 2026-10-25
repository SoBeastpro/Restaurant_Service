package com.restaurant.operations.shift.api;

import java.util.UUID;

import com.restaurant.foundation.web.ApiException;
import com.restaurant.operations.shift.infrastructure.ShiftRepository;
import com.restaurant.operations.shift.infrastructure.ShiftRepository.TableAssignmentRecord;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/restaurants/{restaurantId}/active-shift/tables")
class InternalShiftController {

    private final ShiftRepository repository;

    InternalShiftController(ShiftRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/{tableId}/assignments")
    TableAssignmentsResponse assignments(
            @PathVariable UUID restaurantId,
            @PathVariable UUID tableId) {
        TableAssignmentRecord assignment = repository.findActiveTableAssignments(restaurantId, tableId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.CONFLICT,
                        "TABLE_NOT_ASSIGNED_IN_ACTIVE_SHIFT",
                        "Стол не закреплён в открытой смене."));
        return new TableAssignmentsResponse(
                assignment.shiftId(),
                assignment.managerMembershipId(),
                assignment.waiterMembershipId(),
                assignment.runnerMembershipId());
    }

    record TableAssignmentsResponse(
            UUID shiftId,
            UUID managerMembershipId,
            UUID waiterMembershipId,
            UUID runnerMembershipId) {
    }
}
