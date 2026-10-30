package com.restaurant.audit.platform.application;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.restaurant.audit.platform.api.PlatformAuditDtos.AuditLogResponse;
import com.restaurant.audit.platform.api.PlatformAuditDtos.SystemEventResponse;
import com.restaurant.audit.platform.infrastructure.PlatformAuditRepository;
import com.restaurant.foundation.web.ApiException;
import com.restaurant.foundation.web.PageResponse;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformAuditService {

    private static final List<String> OUTCOMES = List.of("SUCCESS", "DENIED", "FAILED");
    private static final List<String> LEVELS = List.of("TRACE", "DEBUG", "INFO", "WARN", "ERROR");

    private final PlatformAuditRepository repository;

    public PlatformAuditService(PlatformAuditRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> auditLogs(
            String action,
            String outcome,
            UUID actorUserId,
            UUID subjectUserId,
            UUID restaurantId,
            Instant from,
            Instant to,
            int page,
            int size) {
        requirePageAndPeriod(page, size, from, to);
        String normalizedOutcome = normalizeOptional(outcome, OUTCOMES, "AUDIT_OUTCOME_INVALID", "Неизвестный результат аудита.");
        String normalizedAction = cleanOptional(action);
        long total = repository.countAuditLogs(
                normalizedAction, normalizedOutcome, actorUserId, subjectUserId, restaurantId, from, to);
        return PageResponse.of(repository.findAuditLogs(
                        normalizedAction, normalizedOutcome, actorUserId, subjectUserId, restaurantId,
                        from, to, size, page * size),
                page,
                size,
                total);
    }

    @Transactional(readOnly = true)
    public PageResponse<SystemEventResponse> systemEvents(
            String level,
            String service,
            String eventType,
            Instant from,
            Instant to,
            int page,
            int size) {
        requirePageAndPeriod(page, size, from, to);
        String normalizedLevel = normalizeOptional(level, LEVELS, "SYSTEM_EVENT_LEVEL_INVALID", "Неизвестный уровень события.");
        String normalizedService = cleanOptional(service);
        String normalizedType = cleanOptional(eventType);
        long total = repository.countSystemEvents(normalizedLevel, normalizedService, normalizedType, from, to);
        return PageResponse.of(repository.findSystemEvents(
                        normalizedLevel, normalizedService, normalizedType, from, to, size, page * size),
                page,
                size,
                total);
    }

    private static void requirePageAndPeriod(int page, int size, Instant from, Instant to) {
        if (page < 0) {
            throw validation("PAGE_INVALID", "Номер страницы не может быть отрицательным.");
        }
        if (size < 1 || size > 100) {
            throw validation("PAGE_SIZE_INVALID", "Размер страницы должен быть от 1 до 100.");
        }
        if (from != null && to != null && !to.isAfter(from)) {
            throw validation("PERIOD_INVALID", "Конец периода должен быть позже начала.");
        }
    }

    private static String normalizeOptional(String value, List<String> allowed, String code, String message) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.strip().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw validation(code, message);
        }
        return normalized;
    }

    private static String cleanOptional(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static ApiException validation(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }
}
