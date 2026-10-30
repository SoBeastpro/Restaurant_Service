package com.restaurant.reporting.platform.application;

import java.time.LocalDate;

import com.restaurant.foundation.web.ApiException;
import com.restaurant.reporting.platform.api.PlatformMetricsDtos.PlatformMetricsResponse;
import com.restaurant.reporting.platform.infrastructure.PlatformMetricsRepository;
import com.restaurant.reporting.platform.infrastructure.PlatformSummaryClient;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformMetricsService {

    private final PlatformMetricsRepository repository;
    private final PlatformSummaryClient summaryClient;

    public PlatformMetricsService(PlatformMetricsRepository repository, PlatformSummaryClient summaryClient) {
        this.repository = repository;
        this.summaryClient = summaryClient;
    }

    @Transactional(readOnly = true)
    public PlatformMetricsResponse get(LocalDate from, LocalDate to) {
        LocalDate resolvedTo = to == null ? LocalDate.now() : to;
        LocalDate resolvedFrom = from == null ? resolvedTo.minusDays(29) : from;
        if (resolvedTo.isBefore(resolvedFrom)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PERIOD_INVALID", "Конец периода должен быть не раньше начала.");
        }
        if (resolvedFrom.plusYears(1).isBefore(resolvedTo)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PERIOD_TOO_LARGE", "Период метрик не может превышать один год.");
        }
        PlatformMetricsResponse activity = repository.metrics(resolvedFrom, resolvedTo);
        var summary = summaryClient.load();
        return new PlatformMetricsResponse(
                activity.from(),
                activity.to(),
                summary.users().total(),
                summary.users().active(),
                summary.users().blocked(),
                summary.users().archived(),
                summary.restaurants().total(),
                summary.restaurants().active(),
                summary.restaurants().blocked(),
                summary.restaurants().archived(),
                summary.billing().subscriptionsTotal(),
                summary.billing().activeSubscriptions(),
                summary.billing().expiredSubscriptions(),
                summary.billing().cancelledSubscriptions(),
                summary.billing().activeTariffs(),
                summary.billing().archivedTariffs(),
                activity.restaurantsWithData(),
                activity.activeMembers(),
                activity.shiftsOpened(),
                activity.shiftsClosed(),
                activity.tasksCreated(),
                activity.tasksCompleted(),
                activity.tasksCancelled(),
                activity.tasksOverdue(),
                activity.tableStatusChanges(),
                activity.taskCompletionRate(),
                activity.daily());
    }
}
