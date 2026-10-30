package com.restaurant.billing.platform.application;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.restaurant.billing.platform.api.PlatformBillingDtos.ActivateSubscriptionRequest;
import com.restaurant.billing.platform.api.PlatformBillingDtos.CancelSubscriptionRequest;
import com.restaurant.billing.platform.api.PlatformBillingDtos.CreateTariffRequest;
import com.restaurant.billing.platform.api.PlatformBillingDtos.ExtendSubscriptionRequest;
import com.restaurant.billing.platform.api.PlatformBillingDtos.SubscriptionResponse;
import com.restaurant.billing.platform.api.PlatformBillingDtos.TariffResponse;
import com.restaurant.billing.platform.api.PlatformBillingDtos.UpdateTariffRequest;
import com.restaurant.billing.platform.infrastructure.PlatformBillingRepository;
import com.restaurant.foundation.web.ApiException;
import com.restaurant.foundation.web.PageResponse;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.JsonNodeFactory;

@Service
public class PlatformBillingService {

    private static final List<String> TARIFF_STATUSES = List.of("ACTIVE", "ARCHIVED");
    private static final List<String> SUBSCRIPTION_STATUSES = List.of("ACTIVE", "EXPIRED", "CANCELLED");
    private static final List<String> ACTIVATION_MODES = List.of("STUB", "PAYMENT_PROVIDER");

    private final PlatformBillingRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public PlatformBillingService(PlatformBillingRepository repository, ObjectMapper objectMapper) {
        this(repository, objectMapper, Clock.systemUTC());
    }

    PlatformBillingService(PlatformBillingRepository repository, ObjectMapper objectMapper, Clock clock) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<TariffResponse> listTariffs(String status) {
        return repository.findTariffs(status == null || status.isBlank() ? null : normalize(status, TARIFF_STATUSES,
                "TARIFF_STATUS_INVALID", "Неизвестный статус тарифа."));
    }

    @Transactional(readOnly = true)
    public TariffResponse getTariff(UUID tariffId) {
        return requiredTariff(tariffId);
    }

    @Transactional
    public TariffResponse createTariff(UUID actorUserId, CreateTariffRequest request) {
        String code = request.code().strip().toUpperCase(Locale.ROOT);
        String currency = request.currency().strip().toUpperCase(Locale.ROOT);
        var features = requireObjectFeatures(request.features());
        try {
            UUID id = repository.insertTariff(
                    code,
                    request.name().strip(),
                    cleanOptional(request.description()),
                    request.price(),
                    currency,
                    request.durationDays(),
                    features,
                    actorUserId);
            repository.insertTariffOutbox(id, "billing.tariff.changed.v1", json(java.util.Map.of(
                    "tariffId", id.toString(),
                    "actorUserId", actorUserId.toString(),
                    "code", code,
                    "action", "CREATED",
                    "status", "ACTIVE")));
            return requiredTariff(id);
        } catch (DuplicateKeyException exception) {
            throw conflict("TARIFF_CODE_ALREADY_EXISTS", "Тариф с таким кодом уже существует.");
        }
    }

    @Transactional
    public TariffResponse updateTariff(UUID tariffId, UUID actorUserId, UpdateTariffRequest request) {
        TariffResponse current = requiredTariff(tariffId);
        TariffResponse updated = new TariffResponse(
                current.id(),
                current.code(),
                request.name() == null ? current.name() : request.name().strip(),
                request.description() == null ? current.description() : cleanOptional(request.description()),
                request.price() == null ? current.price() : request.price(),
                request.currency() == null ? current.currency() : request.currency().strip().toUpperCase(Locale.ROOT),
                request.durationDays() == null ? current.durationDays() : request.durationDays(),
                request.features() == null ? current.features() : requireObjectFeatures(request.features()),
                request.status() == null ? current.status() : normalize(request.status(), TARIFF_STATUSES,
                        "TARIFF_STATUS_INVALID", "Неизвестный статус тарифа."),
                current.createdByUserId(),
                current.createdAt(),
                current.updatedAt());
        repository.updateTariff(updated);
        repository.insertTariffOutbox(tariffId, "billing.tariff.changed.v1", json(java.util.Map.of(
                "tariffId", tariffId.toString(),
                "actorUserId", actorUserId.toString(),
                "action", "UPDATED",
                "status", updated.status())));
        return requiredTariff(tariffId);
    }

    @Transactional
    public void archiveTariff(UUID tariffId, UUID actorUserId) {
        TariffResponse current = requiredTariff(tariffId);
        if ("ARCHIVED".equals(current.status())) {
            return;
        }
        repository.updateTariff(new TariffResponse(
                current.id(), current.code(), current.name(), current.description(), current.price(),
                current.currency(), current.durationDays(), current.features(), "ARCHIVED",
                current.createdByUserId(), current.createdAt(), current.updatedAt()));
        repository.insertTariffOutbox(tariffId, "billing.tariff.changed.v1", json(java.util.Map.of(
                "tariffId", tariffId.toString(),
                "actorUserId", actorUserId.toString(),
                "action", "ARCHIVED",
                "status", "ARCHIVED")));
    }

    @Transactional
    public PageResponse<SubscriptionResponse> listSubscriptions(
            UUID restaurantId,
            UUID planId,
            String status,
            int page,
            int size) {
        requirePage(page, size);
        repository.expireSubscriptions();
        String normalizedStatus = status == null || status.isBlank() ? null : normalize(
                status, SUBSCRIPTION_STATUSES, "SUBSCRIPTION_STATUS_INVALID", "Неизвестный статус подписки.");
        long total = repository.countSubscriptions(restaurantId, planId, normalizedStatus);
        return PageResponse.of(
                repository.findSubscriptions(restaurantId, planId, normalizedStatus, size, page * size),
                page,
                size,
                total);
    }

    @Transactional
    public SubscriptionResponse getSubscription(UUID subscriptionId) {
        repository.expireSubscriptions();
        return requiredSubscription(subscriptionId);
    }

    @Transactional
    public SubscriptionResponse activate(UUID actorUserId, ActivateSubscriptionRequest request) {
        repository.expireSubscriptions();
        TariffResponse tariff = requiredTariff(request.planId());
        if (!"ACTIVE".equals(tariff.status())) {
            throw conflict("TARIFF_ARCHIVED", "Нельзя активировать подписку на архивный тариф.");
        }
        if (repository.hasActiveSubscription(request.restaurantId())) {
            throw conflict("ACTIVE_SUBSCRIPTION_ALREADY_EXISTS", "У ресторана уже есть активная подписка.");
        }
        String mode = request.activationMode() == null || request.activationMode().isBlank()
                ? "STUB"
                : normalize(request.activationMode(), ACTIVATION_MODES,
                        "ACTIVATION_MODE_INVALID", "Неизвестный способ активации подписки.");
        Instant startsAt = request.startsAt() == null ? clock.instant() : request.startsAt();
        int days = request.durationDays() == null ? tariff.durationDays() : request.durationDays();
        Instant endsAt = startsAt.plus(days, ChronoUnit.DAYS);
        UUID id;
        try {
            id = repository.insertSubscription(
                    request.restaurantId(), tariff.id(), actorUserId, mode, startsAt, endsAt);
        } catch (DuplicateKeyException exception) {
            throw conflict("ACTIVE_SUBSCRIPTION_ALREADY_EXISTS", "У ресторана уже есть активная подписка.");
        }
        String details = json(java.util.Map.of("days", days, "planId", tariff.id().toString()));
        repository.insertSubscriptionEvent(id, actorUserId, "ACTIVATED", details);
        repository.insertOutbox(id, "billing.subscription.status-changed.v1", json(java.util.Map.of(
                "subscriptionId", id.toString(),
                "restaurantId", request.restaurantId().toString(),
                "status", "ACTIVE",
                "endsAt", endsAt.toString())));
        return requiredSubscription(id);
    }

    @Transactional
    public SubscriptionResponse extend(
            UUID subscriptionId,
            UUID actorUserId,
            ExtendSubscriptionRequest request) {
        repository.expireSubscriptions();
        SubscriptionResponse current = requiredSubscription(subscriptionId);
        if (!"ACTIVE".equals(current.status())) {
            throw conflict("SUBSCRIPTION_NOT_ACTIVE", "Продлить можно только активную подписку.");
        }
        Instant base = current.endsAt().isAfter(clock.instant()) ? current.endsAt() : clock.instant();
        Instant endsAt = base.plus(request.days(), ChronoUnit.DAYS);
        repository.extendSubscription(subscriptionId, endsAt);
        repository.insertSubscriptionEvent(subscriptionId, actorUserId, "EXTENDED", json(java.util.Map.of(
                "days", request.days(),
                "reason", request.reason() == null ? "" : request.reason().strip(),
                "endsAt", endsAt.toString())));
        repository.insertOutbox(subscriptionId, "billing.subscription.status-changed.v1", json(java.util.Map.of(
                "subscriptionId", subscriptionId.toString(),
                "restaurantId", current.restaurantId().toString(),
                "status", "ACTIVE",
                "endsAt", endsAt.toString())));
        return requiredSubscription(subscriptionId);
    }

    @Transactional
    public SubscriptionResponse cancel(
            UUID subscriptionId,
            UUID actorUserId,
            CancelSubscriptionRequest request) {
        SubscriptionResponse current = requiredSubscription(subscriptionId);
        if ("CANCELLED".equals(current.status())) {
            return current;
        }
        if (!"ACTIVE".equals(current.status())) {
            throw conflict("SUBSCRIPTION_NOT_ACTIVE", "Отменить можно только активную подписку.");
        }
        Instant cancelledAt = clock.instant();
        repository.cancelSubscription(subscriptionId, cancelledAt);
        String reason = cleanOptional(request.reason());
        repository.insertSubscriptionEvent(subscriptionId, actorUserId, "CANCELLED", json(
                reason == null ? java.util.Map.of() : java.util.Map.of("reason", reason)));
        repository.insertOutbox(subscriptionId, "billing.subscription.status-changed.v1", json(java.util.Map.of(
                "subscriptionId", subscriptionId.toString(),
                "restaurantId", current.restaurantId().toString(),
                "status", "CANCELLED")));
        return requiredSubscription(subscriptionId);
    }

    private TariffResponse requiredTariff(UUID tariffId) {
        return repository.findTariff(tariffId).orElseThrow(() -> notFound(
                "TARIFF_NOT_FOUND", "Тариф не найден."));
    }

    private SubscriptionResponse requiredSubscription(UUID subscriptionId) {
        return repository.findSubscription(subscriptionId).orElseThrow(() -> notFound(
                "SUBSCRIPTION_NOT_FOUND", "Подписка не найдена."));
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot serialize billing event", exception);
        }
    }

    private static tools.jackson.databind.JsonNode requireObjectFeatures(tools.jackson.databind.JsonNode features) {
        if (features == null) {
            return JsonNodeFactory.instance.objectNode();
        }
        if (!features.isObject()) {
            throw validation("TARIFF_FEATURES_INVALID", "Поле features должно быть JSON-объектом.");
        }
        return features;
    }

    private static void requirePage(int page, int size) {
        if (page < 0) {
            throw validation("PAGE_INVALID", "Номер страницы не может быть отрицательным.");
        }
        if (size < 1 || size > 100) {
            throw validation("PAGE_SIZE_INVALID", "Размер страницы должен быть от 1 до 100.");
        }
    }

    private static String normalize(String value, List<String> allowed, String code, String message) {
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

    private static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    private static ApiException notFound(String code, String message) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message);
    }
}
