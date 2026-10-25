package com.restaurant.workforce.onboarding.api;

import java.util.UUID;

import com.restaurant.workforce.onboarding.application.DirectorMembershipService;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/internal/v1/restaurants")
class DirectorMembershipController {

    private final DirectorMembershipService service;

    DirectorMembershipController(DirectorMembershipService service) {
        this.service = service;
    }

    @PostMapping("/{restaurantId}/director")
    @ResponseStatus(HttpStatus.CREATED)
    void create(
            @PathVariable UUID restaurantId,
            @Valid @RequestBody CreateDirectorRequest request) {
        service.create(
                restaurantId,
                request.userId(),
                request.firstName(),
                request.lastName());
    }

    record CreateDirectorRequest(
            @NotNull UUID userId,
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName) {
    }
}
