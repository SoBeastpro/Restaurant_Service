package com.restaurant.audit.platform.api;

import java.time.Instant;
import java.util.UUID;

import tools.jackson.databind.JsonNode;

public final class PlatformAuditDtos {

    private PlatformAuditDtos() {
    }

    public record AuditLogResponse(
            UUID id,
            UUID eventId,
            UUID actorUserId,
            UUID actorMembershipId,
            UUID subjectUserId,
            UUID restaurantId,
            String scope,
            String action,
            String entityType,
            UUID entityId,
            String outcome,
            String ipAddress,
            String userAgent,
            UUID correlationId,
            JsonNode details,
            Instant createdAt) {
    }

    public record SystemEventResponse(
            UUID id,
            String level,
            String service,
            String eventType,
            String message,
            UUID correlationId,
            JsonNode details,
            Instant createdAt) {
    }
}
