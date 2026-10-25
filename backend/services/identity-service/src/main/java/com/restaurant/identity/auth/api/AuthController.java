package com.restaurant.identity.auth.api;

import java.util.List;
import java.util.UUID;

import com.restaurant.identity.auth.api.AuthDtos.ChangePasswordRequest;
import com.restaurant.identity.auth.api.AuthDtos.LoginRequest;
import com.restaurant.identity.auth.api.AuthDtos.RefreshTokenRequest;
import com.restaurant.identity.auth.api.AuthDtos.RegisterRequest;
import com.restaurant.identity.auth.api.AuthDtos.SessionResponse;
import com.restaurant.identity.auth.api.AuthDtos.TokenPair;
import com.restaurant.identity.auth.api.AuthDtos.UserProfile;
import com.restaurant.identity.auth.application.AuthService;
import com.restaurant.identity.auth.application.AuthService.RequestContext;

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
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

    private final AuthService authService;

    AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    ResponseEntity<UserProfile> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    TokenPair login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return authService.login(request, new RequestContext(
                httpRequest.getRemoteAddr(),
                httpRequest.getHeader("User-Agent")));
    }

    @PostMapping("/refresh")
    TokenPair refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(@AuthenticationPrincipal Jwt jwt) {
        authService.logout(userId(jwt), sessionId(jwt));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout-all")
    ResponseEntity<Void> logoutAll(@AuthenticationPrincipal Jwt jwt) {
        authService.logoutAll(userId(jwt));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password/change")
    ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(userId(jwt), request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/sessions")
    List<SessionResponse> sessions(@AuthenticationPrincipal Jwt jwt) {
        return authService.sessions(userId(jwt), sessionId(jwt));
    }

    @DeleteMapping("/sessions/{sessionId}")
    ResponseEntity<Void> deleteSession(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID sessionId) {
        authService.deleteSession(userId(jwt), sessionId);
        return ResponseEntity.noContent().build();
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private static UUID sessionId(Jwt jwt) {
        String value = jwt.getClaimAsString("sid");
        return value == null ? null : UUID.fromString(value);
    }
}
