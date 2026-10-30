package com.restaurant.reporting.platform.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class PlatformMetricsDtos {

    private PlatformMetricsDtos() {
    }

    public record DailyMetricsResponse(
            LocalDate date,
            long restaurants,
            long shiftsOpened,
            long shiftsClosed,
            long tasksCreated,
            long tasksCompleted,
            long tasksCancelled,
            long tasksOverdue,
            long tableStatusChanges) {
    }

    public record PlatformMetricsResponse(
            LocalDate from,
            LocalDate to,
            long usersTotal,
            long activeUsers,
            long blockedUsers,
            long archivedUsers,
            long restaurantsTotal,
            long activeRestaurants,
            long blockedRestaurants,
            long archivedRestaurants,
            long subscriptionsTotal,
            long activeSubscriptions,
            long expiredSubscriptions,
            long cancelledSubscriptions,
            long activeTariffs,
            long archivedTariffs,
            long restaurantsWithData,
            long activeMembers,
            long shiftsOpened,
            long shiftsClosed,
            long tasksCreated,
            long tasksCompleted,
            long tasksCancelled,
            long tasksOverdue,
            long tableStatusChanges,
            BigDecimal taskCompletionRate,
            List<DailyMetricsResponse> daily) {
    }
}
