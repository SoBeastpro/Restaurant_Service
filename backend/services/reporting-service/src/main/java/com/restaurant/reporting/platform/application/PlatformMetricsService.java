package com.restaurant.reporting.platform.application;

import java.time.LocalDate;

import com.restaurant.foundation.web.ApiException;
import com.restaurant.reporting.platform.api.PlatformMetricsDtos.PlatformMetricsResponse;
import com.restaurant.reporting.platform.infrastructure.PlatformMetricsRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformMetricsService {

    private final PlatformMetricsRepository repository;

    public PlatformMetricsService(PlatformMetricsRepository repository) {
        this.repository = repository;
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
        return repository.metrics(resolvedFrom, resolvedTo);
    }
}
