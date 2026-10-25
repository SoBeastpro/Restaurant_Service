package com.restaurant.identity.onboarding.api;

import java.time.Instant;

public final class OnboardingDtos {

    private OnboardingDtos() {
    }

    public record SubscriptionResponse(
            String status,
            Instant expiresAt,
            boolean stub) {
    }
}
