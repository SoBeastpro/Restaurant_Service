package com.restaurant.billing.platform.api;

import com.restaurant.billing.platform.infrastructure.PlatformBillingRepository;
import com.restaurant.billing.platform.infrastructure.PlatformBillingRepository.BillingMetricsSummary;
import com.restaurant.foundation.security.InternalApiAuthorizer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/platform")
class InternalPlatformBillingController {

    private final PlatformBillingRepository repository;
    private final String internalApiSecret;

    InternalPlatformBillingController(
            PlatformBillingRepository repository,
            @Value("${internal.api-secret}") String internalApiSecret) {
        this.repository = repository;
        this.internalApiSecret = internalApiSecret;
    }

    @GetMapping("/metrics-summary")
    BillingMetricsSummary summary(@RequestHeader HttpHeaders headers) {
        InternalApiAuthorizer.require(headers.getFirst(InternalApiAuthorizer.HEADER), internalApiSecret);
        repository.expireSubscriptions();
        return repository.metricsSummary();
    }
}
