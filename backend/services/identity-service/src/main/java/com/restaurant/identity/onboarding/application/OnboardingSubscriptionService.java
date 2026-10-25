package com.restaurant.identity.onboarding.application;

import static com.restaurant.identity.onboarding.api.OnboardingDtos.SubscriptionResponse;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.restaurant.foundation.web.ApiException;
import com.restaurant.identity.auth.infrastructure.IdentityRepository;
import com.restaurant.identity.auth.infrastructure.IdentityRepository.SubscriptionRecord;
import com.restaurant.identity.auth.infrastructure.IdentityRepository.UserRecord;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OnboardingSubscriptionService {

    private static final Duration STUB_SUBSCRIPTION_DURATION = Duration.ofDays(30);

    private final IdentityRepository repository;
    private final Clock clock;

    @Autowired
    public OnboardingSubscriptionService(IdentityRepository repository) {
        this(repository, Clock.systemUTC());
    }

    OnboardingSubscriptionService(IdentityRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public SubscriptionResponse get(UUID userId) {
        requireDirector(userId);
        return response(repository.findSubscription(userId).orElseThrow(this::userNotFound));
    }

    @Transactional
    public SubscriptionResponse activateStub(UUID userId) {
        requireDirector(userId);
        Instant now = clock.instant();
        SubscriptionRecord current = repository.findSubscription(userId).orElseThrow(this::userNotFound);
        if ("ACTIVE".equals(current.status())
                && current.expiresAt() != null
                && current.expiresAt().isAfter(now)) {
            return response(current);
        }
        return response(repository.activateSubscriptionStub(userId, now.plus(STUB_SUBSCRIPTION_DURATION))
                .orElseThrow(this::userNotFound));
    }

    private void requireDirector(UUID userId) {
        UserRecord user = repository.findUserById(userId).orElseThrow(this::userNotFound);
        if (!"DIRECTOR".equals(user.role())) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "DIRECTOR_REQUIRED",
                    "Активировать подписку может только директор ресторана.");
        }
    }

    private static SubscriptionResponse response(SubscriptionRecord subscription) {
        return new SubscriptionResponse(subscription.status(), subscription.expiresAt(), true);
    }

    private ApiException userNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Пользователь не найден.");
    }
}
