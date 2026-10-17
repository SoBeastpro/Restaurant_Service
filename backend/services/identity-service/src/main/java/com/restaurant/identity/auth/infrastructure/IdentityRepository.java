package com.restaurant.identity.auth.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class IdentityRepository {

    private final JdbcClient jdbc;

    public IdentityRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<UserRecord> findUserByLogin(String normalizedLogin) {
        return jdbc.sql("""
                        SELECT id, login, email, phone, password_hash, first_name, last_name,
                               role_code, position_code, restaurant_id,
                               status, email_verified_at, failed_login_attempts, locked_until,
                               created_at, updated_at
                        FROM users
                        WHERE LOWER(login) = :login
                        """)
                .param("login", normalizedLogin)
                .query(IdentityRepository::mapUser)
                .optional();
    }

    public Optional<UserRecord> findUserById(UUID userId) {
        return jdbc.sql("""
                        SELECT id, login, email, phone, password_hash, first_name, last_name,
                               role_code, position_code, restaurant_id,
                               status, email_verified_at, failed_login_attempts, locked_until,
                               created_at, updated_at
                        FROM users
                        WHERE id = :userId
                        """)
                .param("userId", userId)
                .query(IdentityRepository::mapUser)
                .optional();
    }

    public boolean accountExists(String normalizedLogin) {
        return jdbc.sql("""
                        SELECT EXISTS(
                            SELECT 1 FROM users
                            WHERE LOWER(login) = :login
                        )
                        """)
                .param("login", normalizedLogin)
                .query(Boolean.class)
                .single();
    }

    public UUID insertUser(
            String login,
            String passwordHash,
            String firstName,
            String lastName,
            String role,
            String position,
            UUID restaurantId) {
        return jdbc.sql("""
                        INSERT INTO users (
                            login, password_hash, first_name, last_name,
                            role_code, position_code, restaurant_id
                        ) VALUES (
                            :login, :passwordHash, :firstName, :lastName,
                            :role, :position, :restaurantId
                        )
                        RETURNING id
                        """)
                .param("login", login)
                .param("passwordHash", passwordHash)
                .param("firstName", firstName)
                .param("lastName", lastName)
                .param("role", role)
                .param("position", position)
                .param("restaurantId", restaurantId)
                .query(UUID.class)
                .single();
    }

    public List<String> findPlatformRoles(UUID userId) {
        return jdbc.sql("""
                        SELECT role_code
                        FROM user_platform_roles
                        WHERE user_id = :userId
                        ORDER BY role_code
                        """)
                .param("userId", userId)
                .query(String.class)
                .list();
    }

    public void insertSession(
            UUID sessionId,
            UUID userId,
            String refreshTokenHash,
            String ipAddress,
            String userAgent,
            Instant expiresAt) {
        jdbc.sql("""
                        INSERT INTO sessions (
                            id, user_id, refresh_token_hash, ip_address, user_agent, expires_at
                        ) VALUES (
                            :id, :userId, :tokenHash, CAST(:ipAddress AS inet), :userAgent, :expiresAt
                        )
                        """)
                .param("id", sessionId)
                .param("userId", userId)
                .param("tokenHash", refreshTokenHash)
                .param("ipAddress", ipAddress)
                .param("userAgent", userAgent)
                .param("expiresAt", OffsetDateTime.ofInstant(expiresAt, java.time.ZoneOffset.UTC))
                .update();
    }

    public Optional<RefreshSessionRecord> findRefreshSession(UUID sessionId) {
        return jdbc.sql("""
                        SELECT s.id, s.user_id, s.refresh_token_hash, s.expires_at, s.revoked_at,
                               u.login, u.status
                        FROM sessions s
                        JOIN users u ON u.id = s.user_id
                        WHERE s.id = :sessionId
                        """)
                .param("sessionId", sessionId)
                .query((result, rowNumber) -> new RefreshSessionRecord(
                        result.getObject("id", UUID.class),
                        result.getObject("user_id", UUID.class),
                        result.getString("refresh_token_hash"),
                        instant(result, "expires_at"),
                        nullableInstant(result, "revoked_at"),
                        result.getString("login"),
                        result.getString("status")))
                .optional();
    }

    public boolean rotateRefreshToken(
            UUID sessionId,
            String expectedHash,
            String newHash,
            Instant newExpiresAt) {
        return jdbc.sql("""
                        UPDATE sessions
                        SET refresh_token_hash = :newHash,
                            expires_at = :expiresAt,
                            last_active_at = CURRENT_TIMESTAMP
                        WHERE id = :sessionId
                          AND refresh_token_hash = :expectedHash
                          AND revoked_at IS NULL
                          AND expires_at > CURRENT_TIMESTAMP
                        """)
                .param("newHash", newHash)
                .param("expiresAt", OffsetDateTime.ofInstant(newExpiresAt, java.time.ZoneOffset.UTC))
                .param("sessionId", sessionId)
                .param("expectedHash", expectedHash)
                .update() == 1;
    }

    public void updateLastLogin(UUID userId) {
        jdbc.sql("""
                        UPDATE users
                        SET last_login_at = CURRENT_TIMESTAMP,
                            failed_login_attempts = 0,
                            locked_until = NULL
                        WHERE id = :userId
                        """)
                .param("userId", userId)
                .update();
    }

    public void recordFailedLogin(UUID userId) {
        jdbc.sql("""
                        UPDATE users
                        SET failed_login_attempts = failed_login_attempts + 1,
                            locked_until = CASE
                                WHEN failed_login_attempts + 1 >= 5
                                    THEN CURRENT_TIMESTAMP + INTERVAL '15 minutes'
                                ELSE locked_until
                            END
                        WHERE id = :userId
                        """)
                .param("userId", userId)
                .update();
    }

    public void revokeSession(UUID sessionId, String reason) {
        jdbc.sql("""
                        UPDATE sessions
                        SET revoked_at = COALESCE(revoked_at, CURRENT_TIMESTAMP),
                            revoke_reason = COALESCE(revoke_reason, :reason)
                        WHERE id = :sessionId
                        """)
                .param("sessionId", sessionId)
                .param("reason", reason)
                .update();
    }

    public boolean revokeOwnedSession(UUID sessionId, UUID userId, String reason) {
        return jdbc.sql("""
                        UPDATE sessions
                        SET revoked_at = COALESCE(revoked_at, CURRENT_TIMESTAMP),
                            revoke_reason = COALESCE(revoke_reason, :reason)
                        WHERE id = :sessionId AND user_id = :userId
                        """)
                .param("sessionId", sessionId)
                .param("userId", userId)
                .param("reason", reason)
                .update() == 1;
    }

    public int revokeAllSessions(UUID userId, String reason) {
        return jdbc.sql("""
                        UPDATE sessions
                        SET revoked_at = CURRENT_TIMESTAMP, revoke_reason = :reason
                        WHERE user_id = :userId AND revoked_at IS NULL
                        """)
                .param("userId", userId)
                .param("reason", reason)
                .update();
    }

    public List<SessionRecord> findActiveSessions(UUID userId) {
        return jdbc.sql("""
                        SELECT s.id, s.created_at, s.last_active_at, s.expires_at,
                               s.ip_address::text AS ip_address, s.user_agent,
                               d.device_name, d.platform, d.app_version
                        FROM sessions s
                        LEFT JOIN devices d ON d.id = s.device_id
                        WHERE s.user_id = :userId
                          AND s.revoked_at IS NULL
                          AND s.expires_at > CURRENT_TIMESTAMP
                        ORDER BY s.last_active_at DESC
                        """)
                .param("userId", userId)
                .query((result, rowNumber) -> new SessionRecord(
                        result.getObject("id", UUID.class),
                        instant(result, "created_at"),
                        instant(result, "last_active_at"),
                        instant(result, "expires_at"),
                        result.getString("ip_address"),
                        result.getString("user_agent"),
                        result.getString("device_name"),
                        result.getString("platform"),
                        result.getString("app_version")))
                .list();
    }

    public void updatePassword(UUID userId, String passwordHash) {
        jdbc.sql("UPDATE users SET password_hash = :passwordHash WHERE id = :userId")
                .param("passwordHash", passwordHash)
                .param("userId", userId)
                .update();
    }

    public void updateProfile(
            UUID userId,
            String email,
            String firstName,
            String lastName) {
        jdbc.sql("""
                        UPDATE users
                        SET email = COALESCE(:email, email),
                            first_name = COALESCE(:firstName, first_name),
                            last_name = COALESCE(:lastName, last_name)
                        WHERE id = :userId
                        """)
                .param("email", email)
                .param("firstName", firstName)
                .param("lastName", lastName)
                .param("userId", userId)
                .update();
    }

    public Optional<SubscriptionRecord> findSubscription(UUID userId) {
        return jdbc.sql("""
                        SELECT CASE
                                   WHEN subscription_status = 'ACTIVE'
                                        AND subscription_expires_at <= CURRENT_TIMESTAMP
                                       THEN 'EXPIRED'
                                   ELSE subscription_status
                               END AS subscription_status,
                               subscription_expires_at
                        FROM users
                        WHERE id = :userId
                        """)
                .param("userId", userId)
                .query((result, rowNumber) -> new SubscriptionRecord(
                        result.getString("subscription_status"),
                        nullableInstant(result, "subscription_expires_at")))
                .optional();
    }

    public Optional<SubscriptionRecord> activateSubscriptionStub(UUID userId, Instant expiresAt) {
        return jdbc.sql("""
                        UPDATE users
                        SET subscription_status = 'ACTIVE',
                            subscription_expires_at = :expiresAt
                        WHERE id = :userId
                        RETURNING subscription_status, subscription_expires_at
                        """)
                .param("userId", userId)
                .param("expiresAt", OffsetDateTime.ofInstant(expiresAt, java.time.ZoneOffset.UTC))
                .query((result, rowNumber) -> new SubscriptionRecord(
                        result.getString("subscription_status"),
                        instant(result, "subscription_expires_at")))
                .optional();
    }

    public boolean assignRestaurant(UUID userId, UUID restaurantId) {
        return jdbc.sql("""
                        UPDATE users
                        SET restaurant_id = :restaurantId
                        WHERE id = :userId
                          AND role_code = 'DIRECTOR'
                          AND (restaurant_id IS NULL OR restaurant_id = :restaurantId)
                        """)
                .param("userId", userId)
                .param("restaurantId", restaurantId)
                .update() == 1;
    }

    private static UserRecord mapUser(ResultSet result, int rowNumber) throws SQLException {
        return new UserRecord(
                result.getObject("id", UUID.class),
                result.getString("login"),
                result.getString("email"),
                result.getString("phone"),
                result.getString("password_hash"),
                result.getString("first_name"),
                result.getString("last_name"),
                result.getString("role_code"),
                result.getString("position_code"),
                result.getObject("restaurant_id", UUID.class),
                result.getString("status"),
                nullableInstant(result, "email_verified_at"),
                result.getInt("failed_login_attempts"),
                nullableInstant(result, "locked_until"),
                instant(result, "created_at"),
                instant(result, "updated_at"));
    }

    private static Instant instant(ResultSet result, String column) throws SQLException {
        return result.getObject(column, OffsetDateTime.class).toInstant();
    }

    private static Instant nullableInstant(ResultSet result, String column) throws SQLException {
        OffsetDateTime value = result.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    public record UserRecord(
            UUID id,
            String login,
            String email,
            String phone,
            String passwordHash,
            String firstName,
            String lastName,
            String role,
            String position,
            UUID restaurantId,
            String status,
            Instant emailVerifiedAt,
            int failedLoginAttempts,
            Instant lockedUntil,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record RefreshSessionRecord(
            UUID id,
            UUID userId,
            String refreshTokenHash,
            Instant expiresAt,
            Instant revokedAt,
            String login,
            String userStatus) {
    }

    public record SessionRecord(
            UUID id,
            Instant createdAt,
            Instant lastActiveAt,
            Instant expiresAt,
            String ipAddress,
            String userAgent,
            String deviceName,
            String platform,
            String appVersion) {
    }

    public record SubscriptionRecord(String status, Instant expiresAt) {
    }
}
