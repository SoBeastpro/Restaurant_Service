package com.restaurant.restaurant.status.api;

import static com.restaurant.restaurant.status.api.TableStatusDtos.TableStatusHistoryResponse;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.restaurant.foundation.web.ApiException;
import com.restaurant.restaurant.status.application.TableStatusService;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/restaurants/{restaurantId}/tables/status-history")
class RestaurantTableHistoryController {

    private final TableStatusService service;

    RestaurantTableHistoryController(TableStatusService service) {
        this.service = service;
    }

    @GetMapping
    List<TableStatusHistoryResponse> history(
            @PathVariable UUID restaurantId,
            @RequestParam(required = false) UUID tableId,
            @RequestParam(required = false) UUID shiftId,
            @RequestParam(required = false) UUID changedByMembershipId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @AuthenticationPrincipal Jwt jwt) {
        String role = jwt.getClaimAsString("role");
        if (!"DIRECTOR".equals(role)
                && !restaurantId.toString().equals(jwt.getClaimAsString("restaurant_id"))) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "TABLE_STATUS_ACCESS_REQUIRED",
                    "Нет доступа к ресторану.");
        }
        return service.restaurantHistory(
                restaurantId,
                tableId,
                shiftId,
                changedByMembershipId,
                from,
                to,
                UUID.fromString(jwt.getSubject()));
    }
}
