package com.restaurant.restaurant.setup.api;

import static com.restaurant.restaurant.setup.api.SetupDtos.CreateHallRequest;
import static com.restaurant.restaurant.setup.api.SetupDtos.CreateRestaurantRequest;
import static com.restaurant.restaurant.setup.api.SetupDtos.CreateTableRequest;
import static com.restaurant.restaurant.setup.api.SetupDtos.HallResponse;
import static com.restaurant.restaurant.setup.api.SetupDtos.RestaurantResponse;
import static com.restaurant.restaurant.setup.api.SetupDtos.TableResponse;
import static com.restaurant.restaurant.setup.api.SetupDtos.UpdateTableRequest;

import java.util.List;
import java.util.UUID;

import com.restaurant.foundation.web.ApiException;
import com.restaurant.restaurant.setup.application.RestaurantSetupService;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1")
class RestaurantSetupController {

    private final RestaurantSetupService service;

    RestaurantSetupController(RestaurantSetupService service) {
        this.service = service;
    }

    @PostMapping("/restaurants")
    @ResponseStatus(HttpStatus.CREATED)
    RestaurantResponse createRestaurant(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateRestaurantRequest request) {
        if (!"DIRECTOR".equals(role(jwt))) {
            throw forbidden();
        }
        return service.createRestaurant(userId(jwt), request);
    }

    @GetMapping("/restaurants/{restaurantId}")
    RestaurantResponse getRestaurant(@PathVariable UUID restaurantId, @AuthenticationPrincipal Jwt jwt) {
        requireEditorClaim(jwt, restaurantId);
        return service.getRestaurant(restaurantId, userId(jwt), role(jwt));
    }

    @PostMapping("/restaurants/{restaurantId}/halls")
    @ResponseStatus(HttpStatus.CREATED)
    HallResponse createHall(
            @PathVariable UUID restaurantId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateHallRequest request) {
        requireEditorClaim(jwt, restaurantId);
        return service.createHall(restaurantId, userId(jwt), role(jwt), request);
    }

    @GetMapping("/restaurants/{restaurantId}/halls")
    List<HallResponse> listHalls(@PathVariable UUID restaurantId, @AuthenticationPrincipal Jwt jwt) {
        requireEditorClaim(jwt, restaurantId);
        return service.listHalls(restaurantId, userId(jwt), role(jwt));
    }

    @GetMapping("/restaurants/{restaurantId}/halls/{hallId}")
    HallResponse getHall(
            @PathVariable UUID restaurantId,
            @PathVariable UUID hallId,
            @AuthenticationPrincipal Jwt jwt) {
        requireEditorClaim(jwt, restaurantId);
        return service.getHall(restaurantId, hallId, userId(jwt), role(jwt));
    }

    @PostMapping("/restaurants/{restaurantId}/halls/{hallId}/tables")
    @ResponseStatus(HttpStatus.CREATED)
    TableResponse createTable(
            @PathVariable UUID restaurantId,
            @PathVariable UUID hallId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateTableRequest request) {
        requireEditorClaim(jwt, restaurantId);
        return service.createTable(restaurantId, hallId, userId(jwt), role(jwt), request);
    }

    @GetMapping("/restaurants/{restaurantId}/tables")
    List<TableResponse> listTables(@PathVariable UUID restaurantId, @AuthenticationPrincipal Jwt jwt) {
        requireEditorClaim(jwt, restaurantId);
        return service.listTables(restaurantId, userId(jwt), role(jwt));
    }

    @GetMapping("/restaurants/{restaurantId}/tables/{tableId}")
    TableResponse getTable(
            @PathVariable UUID restaurantId,
            @PathVariable UUID tableId,
            @AuthenticationPrincipal Jwt jwt) {
        requireEditorClaim(jwt, restaurantId);
        return service.getTable(restaurantId, tableId, userId(jwt), role(jwt));
    }

    @PatchMapping("/restaurants/{restaurantId}/tables/{tableId}")
    TableResponse updateTable(
            @PathVariable UUID restaurantId,
            @PathVariable UUID tableId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateTableRequest request) {
        requireEditorClaim(jwt, restaurantId);
        return service.updateTable(restaurantId, tableId, userId(jwt), role(jwt), request);
    }

    private static void requireEditorClaim(Jwt jwt, UUID restaurantId) {
        String role = role(jwt);
        if ("DIRECTOR".equals(role)) {
            return;
        }
        if ("MANAGER".equals(role)
                && restaurantId.toString().equals(jwt.getClaimAsString("restaurant_id"))) {
            return;
        }
        throw forbidden();
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private static String role(Jwt jwt) {
        return jwt == null ? null : jwt.getClaimAsString("role");
    }

    private static ApiException forbidden() {
        return new ApiException(HttpStatus.FORBIDDEN, "RESTAURANT_ACCESS_REQUIRED", "Нет доступа к ресторану.");
    }
}
