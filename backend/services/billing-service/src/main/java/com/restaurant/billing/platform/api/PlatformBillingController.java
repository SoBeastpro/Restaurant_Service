package com.restaurant.billing.platform.api;

import java.util.List;
import java.util.UUID;

import com.restaurant.billing.platform.api.PlatformBillingDtos.ActivateSubscriptionRequest;
import com.restaurant.billing.platform.api.PlatformBillingDtos.CancelSubscriptionRequest;
import com.restaurant.billing.platform.api.PlatformBillingDtos.CreateTariffRequest;
import com.restaurant.billing.platform.api.PlatformBillingDtos.ExtendSubscriptionRequest;
import com.restaurant.billing.platform.api.PlatformBillingDtos.SubscriptionResponse;
import com.restaurant.billing.platform.api.PlatformBillingDtos.TariffResponse;
import com.restaurant.billing.platform.api.PlatformBillingDtos.UpdateTariffRequest;
import com.restaurant.billing.platform.application.PlatformBillingService;
import com.restaurant.foundation.web.ApiException;
import com.restaurant.foundation.web.PageResponse;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/platform")
class PlatformBillingController {

    private final PlatformBillingService service;

    PlatformBillingController(PlatformBillingService service) {
        this.service = service;
    }

    @GetMapping("/tariffs")
    List<TariffResponse> tariffs(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) String status) {
        requireSystemAdmin(jwt);
        return service.listTariffs(status);
    }

    @GetMapping("/tariffs/{tariffId}")
    TariffResponse tariff(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID tariffId) {
        requireSystemAdmin(jwt);
        return service.getTariff(tariffId);
    }

    @PostMapping("/tariffs")
    @ResponseStatus(HttpStatus.CREATED)
    TariffResponse createTariff(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateTariffRequest request) {
        requireSystemAdmin(jwt);
        return service.createTariff(userId(jwt), request);
    }

    @PatchMapping("/tariffs/{tariffId}")
    TariffResponse updateTariff(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID tariffId,
            @Valid @RequestBody UpdateTariffRequest request) {
        requireSystemAdmin(jwt);
        return service.updateTariff(tariffId, userId(jwt), request);
    }

    @DeleteMapping("/tariffs/{tariffId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void archiveTariff(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID tariffId) {
        requireSystemAdmin(jwt);
        service.archiveTariff(tariffId, userId(jwt));
    }

    @GetMapping("/subscriptions")
    PageResponse<SubscriptionResponse> subscriptions(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) UUID restaurantId,
            @RequestParam(required = false) UUID planId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        requireSystemAdmin(jwt);
        return service.listSubscriptions(restaurantId, planId, status, page, size);
    }

    @GetMapping("/subscriptions/{subscriptionId}")
    SubscriptionResponse subscription(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID subscriptionId) {
        requireSystemAdmin(jwt);
        return service.getSubscription(subscriptionId);
    }

    @PostMapping("/subscriptions")
    @ResponseStatus(HttpStatus.CREATED)
    SubscriptionResponse activate(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ActivateSubscriptionRequest request) {
        requireSystemAdmin(jwt);
        return service.activate(userId(jwt), request);
    }

    @PostMapping("/subscriptions/{subscriptionId}/extend")
    SubscriptionResponse extend(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID subscriptionId,
            @Valid @RequestBody ExtendSubscriptionRequest request) {
        requireSystemAdmin(jwt);
        return service.extend(subscriptionId, userId(jwt), request);
    }

    @PostMapping("/subscriptions/{subscriptionId}/cancel")
    SubscriptionResponse cancel(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID subscriptionId,
            @Valid @RequestBody CancelSubscriptionRequest request) {
        requireSystemAdmin(jwt);
        return service.cancel(subscriptionId, userId(jwt), request);
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private static void requireSystemAdmin(Jwt jwt) {
        List<String> roles = jwt == null ? List.of() : jwt.getClaimAsStringList("platform_roles");
        if (roles == null || !roles.contains("SYSTEM_ADMIN")) {
            throw new ApiException(HttpStatus.FORBIDDEN, "SYSTEM_ADMIN_REQUIRED", "Требуются права системного администратора.");
        }
    }
}
