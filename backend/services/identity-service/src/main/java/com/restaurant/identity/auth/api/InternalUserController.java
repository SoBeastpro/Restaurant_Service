package com.restaurant.identity.auth.api;

import com.restaurant.identity.auth.api.AuthDtos.InvitedRegistrationRequest;
import com.restaurant.identity.auth.api.AuthDtos.InvitedRegistrationResult;
import com.restaurant.identity.auth.api.AuthDtos.AssignRestaurantRequest;
import com.restaurant.identity.auth.api.AuthDtos.DirectorRestaurantLink;
import com.restaurant.identity.auth.application.AuthService;
import com.restaurant.identity.auth.application.AuthService.RequestContext;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/internal/v1/users")
class InternalUserController {

    private final AuthService authService;

    InternalUserController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    InvitedRegistrationResult registerInvited(
            @Valid @RequestBody InvitedRegistrationRequest request,
            HttpServletRequest httpRequest) {
        return authService.registerInvited(request, new RequestContext(
                httpRequest.getRemoteAddr(),
                "workforce-service"));
    }

    @PostMapping("/{userId}/restaurant")
    DirectorRestaurantLink assignRestaurant(
            @PathVariable java.util.UUID userId,
            @Valid @RequestBody AssignRestaurantRequest request) {
        return authService.assignRestaurant(userId, request.restaurantId());
    }
}
