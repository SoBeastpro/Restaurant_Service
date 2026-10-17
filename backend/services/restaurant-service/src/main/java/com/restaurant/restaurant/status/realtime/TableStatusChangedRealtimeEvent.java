package com.restaurant.restaurant.status.realtime;

import java.time.Instant;
import java.util.UUID;

public record TableStatusChangedRealtimeEvent(
        UUID restaurantId,
        UUID tableId,
        String tableNumber,
        UUID shiftId,
        String oldStatus,
        String newStatus,
        String comment,
        UUID changedByMembershipId,
        Instant changedAt) {
}
