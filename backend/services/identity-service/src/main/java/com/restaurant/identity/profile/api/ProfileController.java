package com.restaurant.identity.profile.api;

import java.util.UUID;

import com.restaurant.identity.auth.api.AuthDtos.UserProfile;
import com.restaurant.identity.auth.application.AuthService;
import com.restaurant.identity.profile.api.ProfileDtos.UpdateProfileRequest;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/me")
class ProfileController {

    private final AuthService authService;

    ProfileController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping
    UserProfile getProfile(@AuthenticationPrincipal Jwt jwt) {
        return authService.getProfile(UUID.fromString(jwt.getSubject()));
    }

    @PatchMapping
    UserProfile updateProfile(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateProfileRequest request) {
        return authService.updateProfile(UUID.fromString(jwt.getSubject()), request);
    }
}
