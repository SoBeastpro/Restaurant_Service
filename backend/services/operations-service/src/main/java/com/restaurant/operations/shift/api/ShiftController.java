package com.restaurant.operations.shift.api;

import static com.restaurant.operations.shift.api.ShiftDtos.ConfigureShiftRequest;
import static com.restaurant.operations.shift.api.ShiftDtos.AssignCarriedTaskRequest;
import static com.restaurant.operations.shift.api.ShiftDtos.AssignTaskRequest;
import static com.restaurant.operations.shift.api.ShiftDtos.ChangeTaskStatusRequest;
import static com.restaurant.operations.shift.api.ShiftDtos.CloseShiftRequest;
import static com.restaurant.operations.shift.api.ShiftDtos.CreateShiftRequest;
import static com.restaurant.operations.shift.api.ShiftDtos.CreateTaskRequest;
import static com.restaurant.operations.shift.api.ShiftDtos.CurrentShiftResponse;
import static com.restaurant.operations.shift.api.ShiftDtos.ShiftReportResponse;
import static com.restaurant.operations.shift.api.ShiftDtos.ShiftResponse;
import static com.restaurant.operations.shift.api.ShiftDtos.ShiftSummaryResponse;
import static com.restaurant.operations.shift.api.ShiftDtos.TaskResponse;
import static com.restaurant.operations.shift.api.ShiftDtos.TaskHistoryResponse;

import java.util.List;
import java.util.UUID;

import com.restaurant.foundation.web.ApiException;
import com.restaurant.operations.shift.application.ShiftService;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1")
class ShiftController {

    private final ShiftService service;

    ShiftController(ShiftService service) {
        this.service = service;
    }

    @PostMapping("/restaurants/{restaurantId}/shifts")
    @ResponseStatus(HttpStatus.CREATED)
    ShiftResponse create(
            @PathVariable UUID restaurantId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateShiftRequest request) {
        requireManagementClaim(jwt, restaurantId);
        return service.create(restaurantId, userId(jwt), request);
    }

    @GetMapping("/restaurants/{restaurantId}/shifts")
    List<ShiftSummaryResponse> list(
            @PathVariable UUID restaurantId,
            @AuthenticationPrincipal Jwt jwt) {
        requireManagementClaim(jwt, restaurantId);
        return service.list(restaurantId, userId(jwt));
    }

    @GetMapping("/restaurants/{restaurantId}/shifts/{shiftId}")
    ShiftResponse get(
            @PathVariable UUID restaurantId,
            @PathVariable UUID shiftId,
            @AuthenticationPrincipal Jwt jwt) {
        requireManagementClaim(jwt, restaurantId);
        return service.get(restaurantId, shiftId, userId(jwt));
    }

    @PutMapping("/restaurants/{restaurantId}/shifts/{shiftId}/participants")
    ShiftResponse configure(
            @PathVariable UUID restaurantId,
            @PathVariable UUID shiftId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ConfigureShiftRequest request) {
        requireManagementClaim(jwt, restaurantId);
        return service.configure(restaurantId, shiftId, userId(jwt), request);
    }

    @PostMapping("/restaurants/{restaurantId}/shifts/{shiftId}/tasks")
    @ResponseStatus(HttpStatus.CREATED)
    TaskResponse createTask(
            @PathVariable UUID restaurantId,
            @PathVariable UUID shiftId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateTaskRequest request) {
        requireManagementClaim(jwt, restaurantId);
        return service.createTask(restaurantId, shiftId, userId(jwt), request);
    }

    @PostMapping("/restaurants/{restaurantId}/shifts/{shiftId}/start")
    ShiftResponse start(
            @PathVariable UUID restaurantId,
            @PathVariable UUID shiftId,
            @AuthenticationPrincipal Jwt jwt) {
        requireManagementClaim(jwt, restaurantId);
        return service.start(restaurantId, shiftId, userId(jwt));
    }

    @PostMapping("/restaurants/{restaurantId}/shifts/{shiftId}/close")
    ShiftReportResponse close(
            @PathVariable UUID restaurantId,
            @PathVariable UUID shiftId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CloseShiftRequest request) {
        requireManagementClaim(jwt, restaurantId);
        return service.close(restaurantId, shiftId, userId(jwt), request);
    }

    @GetMapping("/restaurants/{restaurantId}/shifts/{shiftId}/report")
    ShiftReportResponse report(
            @PathVariable UUID restaurantId,
            @PathVariable UUID shiftId,
            @AuthenticationPrincipal Jwt jwt) {
        requireManagementClaim(jwt, restaurantId);
        return service.getReport(restaurantId, shiftId, userId(jwt));
    }

    @PostMapping("/restaurants/{restaurantId}/tasks/{taskId}/status-changes")
    TaskResponse changeTaskStatus(
            @PathVariable UUID restaurantId,
            @PathVariable UUID taskId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ChangeTaskStatusRequest request) {
        requireRestaurantClaim(jwt, restaurantId);
        return service.changeTaskStatus(restaurantId, taskId, userId(jwt), request);
    }

    @PostMapping("/restaurants/{restaurantId}/tasks/{taskId}/assign")
    TaskResponse assignTask(
            @PathVariable UUID restaurantId,
            @PathVariable UUID taskId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AssignTaskRequest request) {
        requireManagementClaim(jwt, restaurantId);
        return service.assignTask(restaurantId, taskId, userId(jwt), request);
    }

    @GetMapping("/restaurants/{restaurantId}/tasks/pending-carry-over")
    List<TaskResponse> pendingCarryOver(
            @PathVariable UUID restaurantId,
            @AuthenticationPrincipal Jwt jwt) {
        requireManagementClaim(jwt, restaurantId);
        return service.pendingCarryOver(restaurantId, userId(jwt));
    }

    @GetMapping("/restaurants/{restaurantId}/tasks/{taskId}/history")
    List<TaskHistoryResponse> taskHistory(
            @PathVariable UUID restaurantId,
            @PathVariable UUID taskId,
            @AuthenticationPrincipal Jwt jwt) {
        requireManagementClaim(jwt, restaurantId);
        return service.taskHistory(restaurantId, taskId, userId(jwt));
    }

    @PostMapping("/restaurants/{restaurantId}/shifts/{shiftId}/tasks/{taskId}/assign-carried")
    TaskResponse assignCarriedTask(
            @PathVariable UUID restaurantId,
            @PathVariable UUID shiftId,
            @PathVariable UUID taskId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AssignCarriedTaskRequest request) {
        requireManagementClaim(jwt, restaurantId);
        return service.assignCarriedTask(restaurantId, shiftId, taskId, userId(jwt), request);
    }

    @GetMapping("/me/current-shift")
    CurrentShiftResponse current(@AuthenticationPrincipal Jwt jwt) {
        String restaurantId = jwt.getClaimAsString("restaurant_id");
        if (restaurantId == null) {
            throw forbidden();
        }
        return service.current(UUID.fromString(restaurantId), userId(jwt));
    }

    private static void requireManagementClaim(Jwt jwt, UUID restaurantId) {
        String role = jwt.getClaimAsString("role");
        if (!List.of("DIRECTOR", "MANAGER").contains(role)
                || !restaurantId.toString().equals(jwt.getClaimAsString("restaurant_id"))) {
            throw forbidden();
        }
    }

    private static void requireRestaurantClaim(Jwt jwt, UUID restaurantId) {
        if (!restaurantId.toString().equals(jwt.getClaimAsString("restaurant_id"))) {
            throw forbidden();
        }
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private static ApiException forbidden() {
        return new ApiException(HttpStatus.FORBIDDEN, "SHIFT_ACCESS_REQUIRED", "Нет доступа к смене.");
    }
}
