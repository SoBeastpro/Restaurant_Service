package com.restaurant.reporting.platform.api;

import java.time.LocalDate;
import java.util.List;

import com.restaurant.foundation.web.ApiException;
import com.restaurant.reporting.platform.api.PlatformMetricsDtos.PlatformMetricsResponse;
import com.restaurant.reporting.platform.application.PlatformMetricsService;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform/metrics")
class PlatformMetricsController {

    private final PlatformMetricsService service;

    PlatformMetricsController(PlatformMetricsService service) {
        this.service = service;
    }

    @GetMapping
    PlatformMetricsResponse get(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        requireSystemAdmin(jwt);
        return service.get(from, to);
    }

    private static void requireSystemAdmin(Jwt jwt) {
        List<String> roles = jwt == null ? List.of() : jwt.getClaimAsStringList("platform_roles");
        if (roles == null || !roles.contains("SYSTEM_ADMIN")) {
            throw new ApiException(HttpStatus.FORBIDDEN, "SYSTEM_ADMIN_REQUIRED", "Требуются права системного администратора.");
        }
    }
}
