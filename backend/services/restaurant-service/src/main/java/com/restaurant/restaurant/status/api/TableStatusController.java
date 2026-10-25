package com.restaurant.restaurant.status.api;

import static com.restaurant.restaurant.status.api.TableStatusDtos.ChangeTableStatusRequest;
import static com.restaurant.restaurant.status.api.TableStatusDtos.TableStatusHistoryResponse;
import static com.restaurant.restaurant.status.api.TableStatusDtos.TableStatusResponse;

import java.util.List;
import java.util.UUID;

import com.restaurant.foundation.web.ApiException;
import com.restaurant.restaurant.status.application.TableStatusService;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/restaurants/{restaurantId}/tables/{tableId}")
class TableStatusController {

    private final TableStatusService service;

    TableStatusController(TableStatusService service) {
        this.service = service;
    }

    @PostMapping("/status-changes")
    TableStatusResponse change(
            @PathVariable UUID restaurantId,
            @PathVariable UUID tableId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ChangeTableStatusRequest request) {
        requireRestaurantClaim(jwt, restaurantId);
        return service.change(restaurantId, tableId, UUID.fromString(jwt.getSubject()), request);
    }

    @GetMapping("/status-history")
    List<TableStatusHistoryResponse> history(
            @PathVariable UUID restaurantId,
            @PathVariable UUID tableId,
            @RequestParam(required = false) UUID shiftId,
            @AuthenticationPrincipal Jwt jwt) {
        requireRestaurantClaim(jwt, restaurantId);
        return service.history(restaurantId, tableId, shiftId, UUID.fromString(jwt.getSubject()));
    }

    private static void requireRestaurantClaim(Jwt jwt, UUID restaurantId) {
        String role = jwt.getClaimAsString("role");
        if ("DIRECTOR".equals(role)) {
            return;
        }
        if (!restaurantId.toString().equals(jwt.getClaimAsString("restaurant_id"))) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "TABLE_STATUS_ACCESS_REQUIRED",
                    "Нет доступа к ресторану.");
        }
    }
}
