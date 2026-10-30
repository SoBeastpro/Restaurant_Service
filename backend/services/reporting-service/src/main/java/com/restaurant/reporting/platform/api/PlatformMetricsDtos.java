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
