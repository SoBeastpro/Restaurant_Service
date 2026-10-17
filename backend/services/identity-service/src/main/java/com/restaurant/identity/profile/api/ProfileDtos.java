package com.restaurant.identity.profile.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public final class ProfileDtos {

    private ProfileDtos() {
    }

    public record UpdateProfileRequest(
            @Email @Size(max = 320) String email,
            @Size(min = 1, max = 100) String firstName,
            @Size(min = 1, max = 100) String lastName) {
    }
}
