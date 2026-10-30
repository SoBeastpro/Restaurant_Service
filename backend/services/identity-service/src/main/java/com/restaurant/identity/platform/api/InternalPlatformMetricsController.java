package com.restaurant.identity.platform.api;

import com.restaurant.foundation.security.InternalApiAuthorizer;
import com.restaurant.identity.platform.infrastructure.PlatformUserRepository;
import com.restaurant.identity.platform.infrastructure.PlatformUserRepository.UserMetricsSummary;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/platform")
class InternalPlatformMetricsController {

    private final PlatformUserRepository repository;
    private final String internalApiSecret;

    InternalPlatformMetricsController(
            PlatformUserRepository repository,
            @Value("${internal.api-secret}") String internalApiSecret) {
        this.repository = repository;
        this.internalApiSecret = internalApiSecret;
    }

    @GetMapping("/metrics-summary")
    UserMetricsSummary summary(@RequestHeader HttpHeaders headers) {
        InternalApiAuthorizer.require(headers.getFirst(InternalApiAuthorizer.HEADER), internalApiSecret);
        return repository.metricsSummary();
    }
}
