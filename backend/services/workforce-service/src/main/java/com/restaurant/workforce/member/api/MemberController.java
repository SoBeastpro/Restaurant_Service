package com.restaurant.workforce.member.api;

import static com.restaurant.workforce.member.api.MemberDtos.EmployeeResponse;
import static com.restaurant.workforce.member.api.MemberDtos.MemberReferenceResponse;
import static com.restaurant.workforce.member.api.MemberDtos.ValidateEmployeesRequest;
import static com.restaurant.workforce.member.api.MemberDtos.ValidateEmployeesResponse;

import java.util.List;
import java.util.UUID;

import com.restaurant.foundation.web.ApiException;
import com.restaurant.workforce.member.application.MemberService;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping
class MemberController {

    private final MemberService service;

    MemberController(MemberService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/restaurants/{restaurantId}/employees")
    List<EmployeeResponse> listEmployees(
            @PathVariable UUID restaurantId,
            @AuthenticationPrincipal Jwt jwt) {
        requireRestaurantClaim(jwt, restaurantId);
        return service.listEmployees(restaurantId, UUID.fromString(jwt.getSubject()));
    }

    @GetMapping("/internal/v1/restaurants/{restaurantId}/members/by-user/{userId}")
    MemberReferenceResponse findByUser(@PathVariable UUID restaurantId, @PathVariable UUID userId) {
        return service.requireActiveMember(restaurantId, userId);
    }

    @GetMapping("/internal/v1/restaurants/{restaurantId}/members/{membershipId}")
    MemberReferenceResponse findByMembership(
            @PathVariable UUID restaurantId,
            @PathVariable UUID membershipId) {
        return service.requireActiveMembership(restaurantId, membershipId);
    }

    @GetMapping("/internal/v1/restaurants/{restaurantId}/members/managers")
    List<MemberReferenceResponse> activeManagers(@PathVariable UUID restaurantId) {
        return service.activeManagers(restaurantId);
    }

    @PostMapping("/internal/v1/restaurants/{restaurantId}/members/validate-employees")
    ValidateEmployeesResponse validateEmployees(
            @PathVariable UUID restaurantId,
            @Valid @RequestBody ValidateEmployeesRequest request) {
        return new ValidateEmployeesResponse(service.validateEmployees(restaurantId, request.membershipIds()));
    }

    private static void requireRestaurantClaim(Jwt jwt, UUID restaurantId) {
        String role = jwt.getClaimAsString("role");
        String claimedRestaurant = jwt.getClaimAsString("restaurant_id");
        if (!List.of("DIRECTOR", "MANAGER").contains(role)
                || !restaurantId.toString().equals(claimedRestaurant)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "WORKFORCE_ACCESS_REQUIRED", "Нет доступа к сотрудникам.");
        }
    }
}
