package com.restaurant.restaurant.platform.api;

import java.util.List;
import java.util.UUID;

import com.restaurant.foundation.web.ApiException;
import com.restaurant.foundation.web.PageResponse;
import com.restaurant.restaurant.platform.api.PlatformRestaurantDtos.RestaurantResponse;
import com.restaurant.restaurant.platform.api.PlatformRestaurantDtos.UpdateRestaurantStatusRequest;
import com.restaurant.restaurant.platform.application.PlatformRestaurantService;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/platform/restaurants")
class PlatformRestaurantController {

    private final PlatformRestaurantService service;

    PlatformRestaurantController(PlatformRestaurantService service) {
        this.service = service;
    }

    @GetMapping
    PageResponse<RestaurantResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        requireSystemAdmin(jwt);
        return service.list(query, status, page, size);
    }

    @GetMapping("/{restaurantId}")
    RestaurantResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID restaurantId) {
        requireSystemAdmin(jwt);
        return service.get(restaurantId);
    }

    @PatchMapping("/{restaurantId}/status")
    RestaurantResponse updateStatus(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID restaurantId,
            @Valid @RequestBody UpdateRestaurantStatusRequest request) {
        requireSystemAdmin(jwt);
        return service.updateStatus(restaurantId, UUID.fromString(jwt.getSubject()), request);
    }

    private static void requireSystemAdmin(Jwt jwt) {
        List<String> roles = jwt == null ? List.of() : jwt.getClaimAsStringList("platform_roles");
        if (roles == null || !roles.contains("SYSTEM_ADMIN")) {
            throw new ApiException(HttpStatus.FORBIDDEN, "SYSTEM_ADMIN_REQUIRED", "Требуются права системного администратора.");
        }
    }
}
