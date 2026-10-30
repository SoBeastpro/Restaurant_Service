package com.restaurant.audit.platform.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.restaurant.audit.platform.api.PlatformAuditDtos.AuditLogResponse;
import com.restaurant.audit.platform.api.PlatformAuditDtos.SystemEventResponse;
import com.restaurant.audit.platform.application.PlatformAuditService;
import com.restaurant.foundation.web.ApiException;
import com.restaurant.foundation.web.PageResponse;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform")
class PlatformAuditController {

    private final PlatformAuditService service;

    PlatformAuditController(PlatformAuditService service) {
        this.service = service;
    }

    @GetMapping("/audit-log")
    PageResponse<AuditLogResponse> auditLog(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String outcome,
            @RequestParam(required = false) UUID actorUserId,
            @RequestParam(required = false) UUID subjectUserId,
            @RequestParam(required = false) UUID restaurantId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        requireSystemAdmin(jwt);
        return service.auditLogs(action, outcome, actorUserId, subjectUserId, restaurantId, from, to, page, size);
    }

    @GetMapping("/system-events")
    PageResponse<SystemEventResponse> systemEvents(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String level,
            @RequestParam(name = "service", required = false) String serviceName,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        requireSystemAdmin(jwt);
        return service.systemEvents(level, serviceName, eventType, from, to, page, size);
    }

    private static void requireSystemAdmin(Jwt jwt) {
        List<String> roles = jwt == null ? List.of() : jwt.getClaimAsStringList("platform_roles");
        if (roles == null || !roles.contains("SYSTEM_ADMIN")) {
            throw new ApiException(HttpStatus.FORBIDDEN, "SYSTEM_ADMIN_REQUIRED", "Требуются права системного администратора.");
        }
    }
}
