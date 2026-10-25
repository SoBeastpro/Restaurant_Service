package com.restaurant.identity.onboarding.api;

import static com.restaurant.identity.onboarding.api.OnboardingDtos.SubscriptionResponse;

import java.util.UUID;

import com.restaurant.identity.onboarding.application.OnboardingSubscriptionService;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/onboarding/subscription")
class OnboardingController {

    private final OnboardingSubscriptionService subscriptionService;

    OnboardingController(OnboardingSubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @GetMapping
    SubscriptionResponse get(@AuthenticationPrincipal Jwt jwt) {
        return subscriptionService.get(UUID.fromString(jwt.getSubject()));
    }

    @PostMapping("/activate")
    SubscriptionResponse activate(@AuthenticationPrincipal Jwt jwt) {
        return subscriptionService.activateStub(UUID.fromString(jwt.getSubject()));
    }
}
