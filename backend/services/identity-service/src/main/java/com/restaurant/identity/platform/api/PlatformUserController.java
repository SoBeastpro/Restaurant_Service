package com.restaurant.identity.platform.api;

import java.util.List;
import java.util.UUID;

import com.restaurant.foundation.web.ApiException;
import com.restaurant.foundation.web.PageResponse;
import com.restaurant.identity.platform.api.PlatformUserDtos.UpdateUserStatusRequest;
import com.restaurant.identity.platform.api.PlatformUserDtos.UserResponse;
import com.restaurant.identity.platform.application.PlatformUserService;

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
@RequestMapping("/api/v1/platform/users")
class PlatformUserController {

    private final PlatformUserService service;

    PlatformUserController(PlatformUserService service) {
        this.service = service;
    }

    @GetMapping
    PageResponse<UserResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        requireSystemAdmin(jwt);
        return service.list(query, status, role, page, size);
    }

    @GetMapping("/{userId}")
    UserResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID userId) {
        requireSystemAdmin(jwt);
        return service.get(userId);
    }

    @PatchMapping("/{userId}/status")
    UserResponse updateStatus(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateUserStatusRequest request) {
        requireSystemAdmin(jwt);
        return service.updateStatus(userId, UUID.fromString(jwt.getSubject()), request);
    }

    private static void requireSystemAdmin(Jwt jwt) {
        List<String> roles = jwt == null ? List.of() : jwt.getClaimAsStringList("platform_roles");
        if (roles == null || !roles.contains("SYSTEM_ADMIN")) {
            throw new ApiException(HttpStatus.FORBIDDEN, "SYSTEM_ADMIN_REQUIRED", "Требуются права системного администратора.");
        }
    }
}
