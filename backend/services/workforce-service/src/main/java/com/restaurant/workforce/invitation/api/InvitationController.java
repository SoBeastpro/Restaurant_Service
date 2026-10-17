package com.restaurant.workforce.invitation.api;

import static com.restaurant.workforce.invitation.api.InvitationDtos.AcceptInvitationRequest;
import static com.restaurant.workforce.invitation.api.InvitationDtos.CreateInvitationRequest;
import static com.restaurant.workforce.invitation.api.InvitationDtos.InvitationResponse;
import static com.restaurant.workforce.invitation.api.InvitationDtos.PublicInvitationResponse;
import static com.restaurant.workforce.invitation.api.InvitationDtos.TokenPair;

import java.util.List;
import java.util.UUID;

import com.restaurant.foundation.web.ApiException;
import com.restaurant.workforce.invitation.application.InvitationService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1")
class InvitationController {

    private final InvitationService invitationService;

    InvitationController(InvitationService invitationService) {
        this.invitationService = invitationService;
    }

    @PostMapping("/restaurants/{restaurantId}/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    InvitationResponse create(
            @PathVariable UUID restaurantId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateInvitationRequest request) {
        requireInviterClaim(jwt, restaurantId);
        return invitationService.create(restaurantId, userId(jwt), request);
    }

    @GetMapping("/restaurants/{restaurantId}/invitations")
    List<InvitationResponse> list(
            @PathVariable UUID restaurantId,
            @AuthenticationPrincipal Jwt jwt) {
        requireInviterClaim(jwt, restaurantId);
        return invitationService.list(restaurantId, userId(jwt));
    }

    @DeleteMapping("/restaurants/{restaurantId}/invitations/{invitationId}")
    ResponseEntity<Void> revoke(
            @PathVariable UUID restaurantId,
            @PathVariable UUID invitationId,
            @AuthenticationPrincipal Jwt jwt) {
        requireInviterClaim(jwt, restaurantId);
        invitationService.revoke(restaurantId, invitationId, userId(jwt));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/restaurants/{restaurantId}/invitations/{invitationId}/resend")
    InvitationResponse reissue(
            @PathVariable UUID restaurantId,
            @PathVariable UUID invitationId,
            @AuthenticationPrincipal Jwt jwt) {
        requireInviterClaim(jwt, restaurantId);
        return invitationService.reissue(restaurantId, invitationId, userId(jwt));
    }

    @GetMapping("/invitations/{token}")
    PublicInvitationResponse preview(@PathVariable String token) {
        return invitationService.preview(token);
    }

    @PostMapping("/invitations/{token}/accept")
    TokenPair accept(
            @PathVariable String token,
            @Valid @RequestBody AcceptInvitationRequest request) {
        return invitationService.accept(token, request);
    }

    private static void requireInviterClaim(Jwt jwt, UUID restaurantId) {
        String role = jwt == null ? null : jwt.getClaimAsString("role");
        if (jwt == null
                || (!"DIRECTOR".equals(role) && !"MANAGER".equals(role))
                || !restaurantId.toString().equals(jwt.getClaimAsString("restaurant_id"))) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "INVITATION_ACCESS_REQUIRED",
                    "Недостаточно прав для управления приглашениями ресторана.");
        }
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
