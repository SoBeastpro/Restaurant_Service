package com.restaurant.workforce.onboarding.application;

import java.util.UUID;

import com.restaurant.foundation.web.ApiException;
import com.restaurant.workforce.onboarding.infrastructure.DirectorMembershipRepository;
import com.restaurant.workforce.onboarding.infrastructure.DirectorMembershipRepository.DirectorRecord;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DirectorMembershipService {

    private final DirectorMembershipRepository repository;

    public DirectorMembershipService(DirectorMembershipRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void create(UUID restaurantId, UUID userId, String firstName, String lastName) {
        DirectorRecord existing = repository.findByRestaurant(restaurantId).orElse(null);
        if (existing != null) {
            if (existing.userId().equals(userId)) {
                return;
            }
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "DIRECTOR_ALREADY_EXISTS",
                    "У ресторана уже есть другой директор.");
        }
        repository.insert(
                restaurantId,
                userId,
                firstName.strip(),
                lastName.strip());
    }
}
