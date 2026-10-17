package com.restaurant.workforce.invitation.application;

import static com.restaurant.workforce.invitation.api.InvitationDtos.AcceptInvitationRequest;
import static com.restaurant.workforce.invitation.api.InvitationDtos.CreateInvitationRequest;
import static com.restaurant.workforce.invitation.api.InvitationDtos.InvitationResponse;
import static com.restaurant.workforce.invitation.api.InvitationDtos.PublicInvitationResponse;
import static com.restaurant.workforce.invitation.api.InvitationDtos.TokenPair;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.restaurant.foundation.web.ApiException;
import com.restaurant.workforce.invitation.infrastructure.IdentityClient;
import com.restaurant.workforce.invitation.infrastructure.IdentityClient.InvitedRegistrationRequest;
import com.restaurant.workforce.invitation.infrastructure.IdentityClient.InvitedRegistrationResult;
import com.restaurant.workforce.invitation.infrastructure.InvitationRepository;
import com.restaurant.workforce.invitation.infrastructure.InvitationRepository.InvitationRecord;
import com.restaurant.workforce.invitation.infrastructure.InvitationRepository.InviterRecord;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientResponseException;

@Service
public class InvitationService {

    private static final Duration INVITATION_TTL = Duration.ofHours(1);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String INVITATION_PREFIX = "rest://invite/";

    private final InvitationRepository repository;
    private final IdentityClient identityClient;
    private final Clock clock;

    @Autowired
    public InvitationService(InvitationRepository repository, IdentityClient identityClient) {
        this(repository, identityClient, Clock.systemUTC());
    }

    InvitationService(InvitationRepository repository, IdentityClient identityClient, Clock clock) {
        this.repository = repository;
        this.identityClient = identityClient;
        this.clock = clock;
    }

    @Transactional
    public InvitationResponse create(
            UUID restaurantId,
            UUID directorUserId,
            CreateInvitationRequest request) {
        InviterRecord inviter = requiredInviterMembership(restaurantId, directorUserId);
        String role = normalizeRole(request.role());
        ensureCanManage(inviter.role(), role);
        String position = normalizePosition(role, request.position());
        UUID membershipId = UUID.randomUUID();
        repository.insertMember(
                membershipId,
                restaurantId,
                role,
                cleanName(request.firstName()),
                cleanName(request.lastName()),
                position);
        return createInvitation(restaurantId, membershipId, inviter.membershipId(), null, role);
    }

    @Transactional
    public List<InvitationResponse> list(UUID restaurantId, UUID directorUserId) {
        InviterRecord inviter = requiredInviterMembership(restaurantId, directorUserId);
        repository.expirePending(restaurantId);
        String invitedRole = "MANAGER".equals(inviter.role()) ? "EMPLOYEE" : null;
        return repository.findAll(restaurantId, invitedRole).stream()
                .map(record -> response(record, null))
                .toList();
    }

    @Transactional
    public void revoke(UUID restaurantId, UUID invitationId, UUID directorUserId) {
        InviterRecord inviter = requiredInviterMembership(restaurantId, directorUserId);
        InvitationRecord invitation = repository.findById(restaurantId, invitationId)
                .orElseThrow(InvitationService::notFound);
        ensureCanManage(inviter.role(), invitation.role());
        if ("ACCEPTED".equals(invitation.status())) {
            throw conflict("INVITATION_ALREADY_ACCEPTED", "Принятое приглашение нельзя отозвать.");
        }
        if ("PENDING".equals(invitation.status())) {
            repository.revoke(restaurantId, invitationId);
        }
    }

    @Transactional
    public InvitationResponse reissue(UUID restaurantId, UUID invitationId, UUID directorUserId) {
        InviterRecord inviter = requiredInviterMembership(restaurantId, directorUserId);
        repository.expirePending(restaurantId);
        InvitationRecord previous = repository.findById(restaurantId, invitationId)
                .orElseThrow(InvitationService::notFound);
        ensureCanManage(inviter.role(), previous.role());
        if ("ACCEPTED".equals(previous.status())) {
            throw conflict("INVITATION_ALREADY_ACCEPTED", "Принятое приглашение нельзя перевыпустить.");
        }
        repository.revoke(restaurantId, invitationId);
        return createInvitation(
                restaurantId,
                previous.membershipId(),
                inviter.membershipId(),
                previous.id(),
                previous.role());
    }

    @Transactional
    public PublicInvitationResponse preview(String rawToken) {
        InvitationRecord invitation = activeInvitation(rawToken);
        return new PublicInvitationResponse(
                invitation.restaurantId(),
                invitation.firstName(),
                invitation.lastName(),
                invitation.role(),
                invitation.position(),
                invitation.expiresAt());
    }

    @Transactional
    public TokenPair accept(String rawToken, AcceptInvitationRequest request) {
        InvitationRecord invitation = activeInvitation(rawToken);
        InvitedRegistrationResult registration;
        try {
            registration = identityClient.register(new InvitedRegistrationRequest(
                    request.login(),
                    request.password(),
                    invitation.firstName(),
                    invitation.lastName(),
                    invitation.role(),
                    invitation.position(),
                    invitation.restaurantId()));
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 409) {
                throw conflict("ACCOUNT_ALREADY_EXISTS", "Аккаунт с таким логином уже существует.");
            }
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "IDENTITY_SERVICE_UNAVAILABLE",
                    "Не удалось создать аккаунт сотрудника.");
        }
        if (registration == null || registration.userId() == null || registration.tokens() == null) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "IDENTITY_SERVICE_INVALID_RESPONSE",
                    "Сервис аккаунтов вернул некорректный ответ.");
        }
        if (!repository.accept(invitation.id(), invitation.membershipId(), registration.userId())) {
            throw conflict("INVITATION_ALREADY_USED", "Приглашение уже использовано.");
        }
        return registration.tokens();
    }

    private InvitationResponse createInvitation(
            UUID restaurantId,
            UUID membershipId,
            UUID directorMembershipId,
            UUID replacesInvitationId,
            String role) {
        UUID invitationId = UUID.randomUUID();
        String rawToken = newToken();
        Instant expiresAt = clock.instant().plus(INVITATION_TTL);
        repository.insertInvitation(
                invitationId,
                restaurantId,
                membershipId,
                directorMembershipId,
                replacesInvitationId,
                role,
                sha256(rawToken),
                expiresAt);
        InvitationRecord invitation = repository.findById(restaurantId, invitationId)
                .orElseThrow(InvitationService::notFound);
        return response(invitation, rawToken);
    }

    private InvitationRecord activeInvitation(String rawToken) {
        String token = extractToken(rawToken);
        InvitationRecord invitation = repository.findByTokenForUpdate(sha256(token))
                .orElseThrow(InvitationService::notFound);
        if ("ACCEPTED".equals(invitation.status())) {
            throw conflict("INVITATION_ALREADY_USED", "Приглашение уже использовано.");
        }
        if ("REVOKED".equals(invitation.status())) {
            throw new ApiException(HttpStatus.GONE, "INVITATION_REVOKED", "Приглашение отозвано.");
        }
        if ("EXPIRED".equals(invitation.status()) || !invitation.expiresAt().isAfter(clock.instant())) {
            repository.markExpired(invitation.id());
            throw new ApiException(HttpStatus.GONE, "INVITATION_EXPIRED", "Срок приглашения истёк.");
        }
        return invitation;
    }

    private InviterRecord requiredInviterMembership(UUID restaurantId, UUID userId) {
        return repository.findInviterMembership(restaurantId, userId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.FORBIDDEN,
                        "INVITATION_ACCESS_REQUIRED",
                        "Недостаточно прав для управления приглашениями ресторана."));
    }

    private static void ensureCanManage(String inviterRole, String invitedRole) {
        if ("MANAGER".equals(inviterRole) && !"EMPLOYEE".equals(invitedRole)) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "MANAGER_CAN_INVITE_EMPLOYEES_ONLY",
                    "Менеджер может приглашать только сотрудников.");
        }
    }

    private static InvitationResponse response(InvitationRecord invitation, String rawToken) {
        String content = rawToken == null ? null : INVITATION_PREFIX + rawToken;
        return new InvitationResponse(
                invitation.id(),
                invitation.membershipId(),
                invitation.restaurantId(),
                invitation.firstName(),
                invitation.lastName(),
                invitation.role(),
                invitation.position(),
                invitation.status(),
                content,
                content,
                invitation.expiresAt());
    }

    private static String normalizeRole(String value) {
        String role = value.strip().toUpperCase(Locale.ROOT);
        if (!"MANAGER".equals(role) && !"EMPLOYEE".equals(role)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_INVITATION_ROLE",
                    "Роль должна быть MANAGER или EMPLOYEE.");
        }
        return role;
    }

    private static String normalizePosition(String role, String value) {
        if ("MANAGER".equals(role)) {
            if (value != null && !value.isBlank()) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "POSITION_NOT_ALLOWED",
                        "Для менеджера должность сотрудника не указывается.");
            }
            return null;
        }
        if (value == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "POSITION_REQUIRED", "Укажите должность сотрудника.");
        }
        String position = value.strip().toUpperCase(Locale.ROOT);
        if (!"WAITER".equals(position) && !"RUNNER".equals(position)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_POSITION",
                    "Должность должна быть WAITER или RUNNER.");
        }
        return position;
    }

    private static String cleanName(String value) {
        String name = value.strip();
        if (name.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Имя не может быть пустым.");
        }
        return name;
    }

    private static String extractToken(String value) {
        String token = value == null ? "" : value.strip();
        if (token.startsWith(INVITATION_PREFIX)) {
            token = token.substring(INVITATION_PREFIX.length());
        }
        if (token.length() < 20) {
            throw notFound();
        }
        return token;
    }

    private static String newToken() {
        byte[] value = new byte[32];
        SECURE_RANDOM.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "INVITATION_NOT_FOUND", "Приглашение не найдено.");
    }

    private static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }
}
