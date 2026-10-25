package com.restaurant.identity.auth.application;

import static com.restaurant.identity.auth.api.AuthDtos.ChangePasswordRequest;
import static com.restaurant.identity.auth.api.AuthDtos.DirectorRestaurantLink;
import static com.restaurant.identity.auth.api.AuthDtos.InvitedRegistrationRequest;
import static com.restaurant.identity.auth.api.AuthDtos.InvitedRegistrationResult;
import static com.restaurant.identity.auth.api.AuthDtos.LoginRequest;
import static com.restaurant.identity.auth.api.AuthDtos.RegisterRequest;
import static com.restaurant.identity.auth.api.AuthDtos.SessionResponse;
import static com.restaurant.identity.auth.api.AuthDtos.TokenPair;
import static com.restaurant.identity.auth.api.AuthDtos.UserProfile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.restaurant.foundation.web.ApiException;
import com.restaurant.identity.auth.infrastructure.IdentityRepository;
import com.restaurant.identity.auth.infrastructure.IdentityRepository.RefreshSessionRecord;
import com.restaurant.identity.auth.infrastructure.IdentityRepository.UserRecord;
import com.restaurant.identity.auth.security.AuthProperties;
import com.restaurant.identity.auth.security.JwtService;
import com.restaurant.identity.profile.api.ProfileDtos.UpdateProfileRequest;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final IdentityRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthProperties properties;
    private final Clock clock;
    private final String dummyPasswordHash;

    @Autowired
    public AuthService(
            IdentityRepository repository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AuthProperties properties) {
        this(repository, passwordEncoder, jwtService, properties, Clock.systemUTC());
    }

    AuthService(
            IdentityRepository repository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AuthProperties properties,
            Clock clock) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.properties = properties;
        this.clock = clock;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public UserProfile register(RegisterRequest request) {
        String login = normalizedLogin(request.login());
        ensureBcryptLength(request.password());
        if (repository.accountExists(login)) {
            throw conflict("ACCOUNT_ALREADY_EXISTS", "Аккаунт с таким логином уже существует.");
        }
        try {
            UUID userId = repository.insertUser(
                    login,
                    passwordEncoder.encode(request.password()),
                    cleanName(request.firstName()),
                    cleanName(request.lastName()),
                    "DIRECTOR",
                    null,
                    null);
            return profile(requiredUser(userId));
        } catch (DuplicateKeyException exception) {
            throw conflict("ACCOUNT_ALREADY_EXISTS", "Аккаунт с таким логином уже существует.");
        }
    }

    @Transactional
    public InvitedRegistrationResult registerInvited(
            InvitedRegistrationRequest request,
            RequestContext context) {
        String login = normalizedLogin(request.login());
        String role = normalizeInvitedRole(request.role());
        String position = normalizePosition(role, request.position());
        if (request.restaurantId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "RESTAURANT_REQUIRED", "Ресторан обязателен.");
        }
        ensureBcryptLength(request.password());
        if (repository.accountExists(login)) {
            throw conflict("ACCOUNT_ALREADY_EXISTS", "Аккаунт с таким логином уже существует.");
        }
        try {
            UUID userId = repository.insertUser(
                    login,
                    passwordEncoder.encode(request.password()),
                    cleanName(request.firstName()),
                    cleanName(request.lastName()),
                    role,
                    position,
                    request.restaurantId());
            UserRecord user = requiredUser(userId);
            return new InvitedRegistrationResult(userId, createSession(user, context));
        } catch (DuplicateKeyException exception) {
            throw conflict("ACCOUNT_ALREADY_EXISTS", "Аккаунт с таким логином уже существует.");
        }
    }

    @Transactional
    public DirectorRestaurantLink assignRestaurant(UUID userId, UUID restaurantId) {
        UserRecord user = requiredUser(userId);
        if (!"DIRECTOR".equals(user.role())) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "DIRECTOR_REQUIRED",
                    "Создать ресторан может только директор.");
        }
        var subscription = repository.findSubscription(userId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Пользователь не найден."));
        if (!"ACTIVE".equals(subscription.status())) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "ACTIVE_SUBSCRIPTION_REQUIRED",
                    "Сначала активируйте подписку.");
        }
        if (user.restaurantId() != null && !user.restaurantId().equals(restaurantId)) {
            throw conflict("RESTAURANT_ALREADY_ASSIGNED", "Аккаунт директора уже связан с рестораном.");
        }
        if (!repository.assignRestaurant(userId, restaurantId)) {
            throw conflict("RESTAURANT_ASSIGNMENT_FAILED", "Не удалось связать аккаунт с рестораном.");
        }
        return new DirectorRestaurantLink(user.id(), user.firstName(), user.lastName());
    }

    @Transactional(noRollbackFor = ApiException.class)
    public TokenPair login(LoginRequest request, RequestContext context) {
        ensureBcryptLength(request.password());
        UserRecord user = repository.findUserByLogin(normalizedLogin(request.login())).orElse(null);
        if (user == null) {
            passwordEncoder.matches(request.password(), dummyPasswordHash);
            throw invalidCredentials();
        }
        ensureActive(user.status());
        if (user.lockedUntil() != null && user.lockedUntil().isAfter(clock.instant())) {
            passwordEncoder.matches(request.password(), user.passwordHash());
            throw invalidCredentials();
        }
        if (!passwordEncoder.matches(request.password(), user.passwordHash())) {
            repository.recordFailedLogin(user.id());
            throw invalidCredentials();
        }

        return createSession(user, context);
    }

    private TokenPair createSession(UserRecord user, RequestContext context) {
        UUID sessionId = UUID.randomUUID();
        String refreshToken = newRefreshToken(sessionId);
        Instant refreshExpiresAt = clock.instant().plus(properties.getRefreshTokenTtl());
        repository.insertSession(
                sessionId,
                user.id(),
                sha256(refreshToken),
                context.ipAddress(),
                truncate(context.userAgent(), 1000),
                refreshExpiresAt);
        repository.updateLastLogin(user.id());
        return tokenPair(user, sessionId, refreshToken);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public TokenPair refresh(String refreshToken) {
        UUID sessionId = parseSessionId(refreshToken);
        RefreshSessionRecord session = repository.findRefreshSession(sessionId)
                .orElseThrow(AuthService::invalidRefreshToken);
        if (session.revokedAt() != null || !session.expiresAt().isAfter(clock.instant())) {
            throw invalidRefreshToken();
        }
        ensureActive(session.userStatus());

        String presentedHash = sha256(refreshToken);
        if (!constantTimeEquals(presentedHash, session.refreshTokenHash())) {
            repository.revokeSession(sessionId, "REFRESH_TOKEN_REUSE");
            throw invalidRefreshToken();
        }

        String rotatedToken = newRefreshToken(sessionId);
        Instant expiresAt = clock.instant().plus(properties.getRefreshTokenTtl());
        if (!repository.rotateRefreshToken(sessionId, presentedHash, sha256(rotatedToken), expiresAt)) {
            repository.revokeSession(sessionId, "REFRESH_TOKEN_RACE");
            throw invalidRefreshToken();
        }
        UserRecord user = requiredUser(session.userId());
        return tokenPair(user, sessionId, rotatedToken);
    }

    @Transactional
    public void logout(UUID userId, UUID sessionId) {
        if (sessionId != null) {
            repository.revokeOwnedSession(sessionId, userId, "LOGOUT");
        }
    }

    @Transactional
    public void logoutAll(UUID userId) {
        repository.revokeAllSessions(userId, "LOGOUT_ALL");
    }

    @Transactional(readOnly = true)
    public List<SessionResponse> sessions(UUID userId, UUID currentSessionId) {
        return repository.findActiveSessions(userId).stream()
                .map(session -> new SessionResponse(
                        session.id(),
                        session.id().equals(currentSessionId),
                        session.createdAt(),
                        session.lastActiveAt(),
                        session.expiresAt(),
                        session.ipAddress(),
                        session.userAgent(),
                        session.deviceName(),
                        session.platform(),
                        session.appVersion()))
                .toList();
    }

    @Transactional
    public void deleteSession(UUID userId, UUID sessionId) {
        if (!repository.revokeOwnedSession(sessionId, userId, "USER_REVOKED")) {
            throw new ApiException(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", "Сессия не найдена.");
        }
    }

    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        ensureBcryptLength(request.currentPassword());
        ensureBcryptLength(request.newPassword());
        UserRecord user = requiredUser(userId);
        if (!passwordEncoder.matches(request.currentPassword(), user.passwordHash())) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    "INVALID_CURRENT_PASSWORD",
                    "Текущий пароль указан неверно.");
        }
        if (passwordEncoder.matches(request.newPassword(), user.passwordHash())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "PASSWORD_NOT_CHANGED",
                    "Новый пароль должен отличаться от текущего.");
        }
        repository.updatePassword(userId, passwordEncoder.encode(request.newPassword()));
        repository.revokeAllSessions(userId, "PASSWORD_CHANGED");
    }

    @Transactional(readOnly = true)
    public UserProfile getProfile(UUID userId) {
        return profile(requiredUser(userId));
    }

    @Transactional
    public UserProfile updateProfile(UUID userId, UpdateProfileRequest request) {
        if (request.email() == null && request.firstName() == null && request.lastName() == null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "EMPTY_UPDATE",
                    "Нужно передать хотя бы одно изменяемое поле.");
        }
        try {
            repository.updateProfile(
                    userId,
                    request.email() == null ? null : normalizedEmail(request.email()),
                    request.firstName() == null ? null : cleanName(request.firstName()),
                    request.lastName() == null ? null : cleanName(request.lastName()));
            return profile(requiredUser(userId));
        } catch (DuplicateKeyException exception) {
            throw conflict("EMAIL_ALREADY_EXISTS", "Этот email уже используется.");
        }
    }

    private TokenPair tokenPair(UserRecord user, UUID sessionId, String refreshToken) {
        List<String> platformRoles = repository.findPlatformRoles(user.id());
        JwtService.AccessToken accessToken = jwtService.issue(
                user.id(),
                sessionId,
                user.login(),
                user.role(),
                user.position(),
                user.restaurantId(),
                platformRoles);
        return new TokenPair(accessToken.value(), refreshToken, "Bearer", accessToken.expiresInSeconds());
    }

    private UserProfile profile(UserRecord user) {
        return new UserProfile(
                user.id(),
                user.login(),
                user.email(),
                user.phone(),
                user.firstName(),
                user.lastName(),
                user.status(),
                user.role(),
                user.position(),
                user.restaurantId(),
                repository.findPlatformRoles(user.id()),
                user.createdAt(),
                user.updatedAt());
    }

    private UserRecord requiredUser(UUID userId) {
        return repository.findUserById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Пользователь не найден."));
    }

    private static void ensureActive(String status) {
        if ("BLOCKED".equals(status)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_BLOCKED", "Аккаунт заблокирован.");
        }
        if (!"ACTIVE".equals(status)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_UNAVAILABLE", "Аккаунт недоступен.");
        }
    }

    private static String normalizedLogin(String value) {
        return value.strip().toLowerCase(Locale.ROOT);
    }

    private static String normalizedEmail(String value) {
        return value.strip().toLowerCase(Locale.ROOT);
    }

    private static String normalizeInvitedRole(String value) {
        String role = value.strip().toUpperCase(Locale.ROOT);
        if (!"MANAGER".equals(role) && !"EMPLOYEE".equals(role)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_INVITATION_ROLE",
                    "Приглашение может назначить только роль MANAGER или EMPLOYEE.");
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
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "POSITION_REQUIRED",
                    "Для сотрудника нужно указать должность.");
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
        String cleaned = value.strip();
        if (cleaned.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Имя не может быть пустым.");
        }
        return cleaned;
    }

    private static void ensureBcryptLength(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "PASSWORD_TOO_LONG",
                    "Пароль не должен превышать 72 байта в UTF-8.");
        }
    }

    private static String newRefreshToken(UUID sessionId) {
        byte[] secret = new byte[32];
        SECURE_RANDOM.nextBytes(secret);
        return sessionId + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
    }

    private static UUID parseSessionId(String refreshToken) {
        if (refreshToken == null) {
            throw invalidRefreshToken();
        }
        int separator = refreshToken.indexOf('.');
        if (separator <= 0 || separator == refreshToken.length() - 1) {
            throw invalidRefreshToken();
        }
        try {
            return UUID.fromString(refreshToken.substring(0, separator));
        } catch (IllegalArgumentException exception) {
            throw invalidRefreshToken();
        }
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

    private static boolean constantTimeEquals(String left, String right) {
        return MessageDigest.isEqual(
                left.getBytes(StandardCharsets.US_ASCII),
                right.getBytes(StandardCharsets.US_ASCII));
    }

    private static String truncate(String value, int maximumLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maximumLength ? value : value.substring(0, maximumLength);
    }

    private static ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Неверный логин или пароль.");
    }

    private static ApiException invalidRefreshToken() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "Refresh-токен недействителен.");
    }

    private static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    public record RequestContext(String ipAddress, String userAgent) {
    }
}
