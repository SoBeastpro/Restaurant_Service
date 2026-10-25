package com.restaurant.workforce.invitation.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class InvitationRepository {

    private final JdbcClient jdbc;

    public InvitationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<InviterRecord> findInviterMembership(UUID restaurantId, UUID userId) {
        return jdbc.sql("""
                        SELECT id, role_code
                        FROM restaurant_members
                        WHERE restaurant_id = :restaurantId
                          AND user_id = :userId
                          AND role_code IN ('DIRECTOR', 'MANAGER')
                          AND status = 'ACTIVE'
                        """)
                .param("restaurantId", restaurantId)
                .param("userId", userId)
                .query((result, rowNumber) -> new InviterRecord(
                        result.getObject("id", UUID.class),
                        result.getString("role_code")))
                .optional();
    }

    public void insertMember(
            UUID membershipId,
            UUID restaurantId,
            String role,
            String firstName,
            String lastName,
            String position) {
        jdbc.sql("""
                        INSERT INTO restaurant_members (
                            id, restaurant_id, role_code, first_name, last_name, job_title, status
                        ) VALUES (
                            :id, :restaurantId, :role, :firstName, :lastName, :position, 'INVITED'
                        )
                        """)
                .param("id", membershipId)
                .param("restaurantId", restaurantId)
                .param("role", role)
                .param("firstName", firstName)
                .param("lastName", lastName)
                .param("position", position)
                .update();
    }

    public void insertInvitation(
            UUID invitationId,
            UUID restaurantId,
            UUID membershipId,
            UUID creatorMembershipId,
            UUID replacesInvitationId,
            String role,
            String tokenHash,
            Instant expiresAt) {
        jdbc.sql("""
                        INSERT INTO invitations (
                            id, restaurant_id, membership_id, created_by_membership_id,
                            replaces_invitation_id, role_code, token_hash, expires_at
                        ) VALUES (
                            :id, :restaurantId, :membershipId, :creatorMembershipId,
                            :replacesInvitationId, :role, :tokenHash, :expiresAt
                        )
                        """)
                .param("id", invitationId)
                .param("restaurantId", restaurantId)
                .param("membershipId", membershipId)
                .param("creatorMembershipId", creatorMembershipId)
                .param("replacesInvitationId", replacesInvitationId)
                .param("role", role)
                .param("tokenHash", tokenHash)
                .param("expiresAt", OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC))
                .update();
    }

    public void expirePending(UUID restaurantId) {
        jdbc.sql("""
                        UPDATE invitations
                        SET status = 'EXPIRED'
                        WHERE restaurant_id = :restaurantId
                          AND status = 'PENDING'
                          AND expires_at <= CURRENT_TIMESTAMP
                        """)
                .param("restaurantId", restaurantId)
                .update();
    }

    public List<InvitationRecord> findAll(UUID restaurantId, String invitedRole) {
        String roleFilter = invitedRole == null ? "" : " AND i.role_code = :invitedRole";
        var query = jdbc.sql(selectSql()
                        + " WHERE i.restaurant_id = :restaurantId"
                        + roleFilter
                        + " ORDER BY i.created_at DESC")
                .param("restaurantId", restaurantId);
        if (invitedRole != null) {
            query = query.param("invitedRole", invitedRole);
        }
        return query.query(InvitationRepository::mapInvitation).list();
    }

    public Optional<InvitationRecord> findById(UUID restaurantId, UUID invitationId) {
        return jdbc.sql(selectSql() + " WHERE i.restaurant_id = :restaurantId AND i.id = :invitationId")
                .param("restaurantId", restaurantId)
                .param("invitationId", invitationId)
                .query(InvitationRepository::mapInvitation)
                .optional();
    }

    public Optional<InvitationRecord> findByTokenForUpdate(String tokenHash) {
        return jdbc.sql(selectSql() + " WHERE i.token_hash = :tokenHash FOR UPDATE OF i")
                .param("tokenHash", tokenHash)
                .query(InvitationRepository::mapInvitation)
                .optional();
    }

    public boolean revoke(UUID restaurantId, UUID invitationId) {
        return jdbc.sql("""
                        UPDATE invitations
                        SET status = 'REVOKED', revoked_at = CURRENT_TIMESTAMP
                        WHERE restaurant_id = :restaurantId
                          AND id = :invitationId
                          AND status = 'PENDING'
                        """)
                .param("restaurantId", restaurantId)
                .param("invitationId", invitationId)
                .update() == 1;
    }

    public void markExpired(UUID invitationId) {
        jdbc.sql("""
                        UPDATE invitations
                        SET status = 'EXPIRED'
                        WHERE id = :invitationId AND status = 'PENDING'
                        """)
                .param("invitationId", invitationId)
                .update();
    }

    public boolean accept(UUID invitationId, UUID membershipId, UUID userId) {
        int invitationUpdated = jdbc.sql("""
                        UPDATE invitations
                        SET status = 'ACCEPTED', accepted_by_user_id = :userId,
                            accepted_at = CURRENT_TIMESTAMP
                        WHERE id = :invitationId AND status = 'PENDING'
                        """)
                .param("userId", userId)
                .param("invitationId", invitationId)
                .update();
        if (invitationUpdated != 1) {
            return false;
        }
        jdbc.sql("""
                        UPDATE restaurant_members
                        SET user_id = :userId, status = 'ACTIVE', last_active_at = CURRENT_TIMESTAMP
                        WHERE id = :membershipId AND status = 'INVITED'
                        """)
                .param("userId", userId)
                .param("membershipId", membershipId)
                .update();
        return true;
    }

    private static String selectSql() {
        return """
                SELECT i.id, i.restaurant_id, i.membership_id, i.role_code, i.status,
                       i.expires_at, m.first_name, m.last_name, m.job_title
                FROM invitations i
                JOIN restaurant_members m ON m.id = i.membership_id
                """;
    }

    private static InvitationRecord mapInvitation(ResultSet result, int rowNumber) throws SQLException {
        return new InvitationRecord(
                result.getObject("id", UUID.class),
                result.getObject("restaurant_id", UUID.class),
                result.getObject("membership_id", UUID.class),
                result.getString("first_name"),
                result.getString("last_name"),
                result.getString("role_code"),
                result.getString("job_title"),
                result.getString("status"),
                result.getObject("expires_at", OffsetDateTime.class).toInstant());
    }

    public record InvitationRecord(
            UUID id,
            UUID restaurantId,
            UUID membershipId,
            String firstName,
            String lastName,
            String role,
            String position,
            String status,
            Instant expiresAt) {
    }

    public record InviterRecord(UUID membershipId, String role) {
    }
}
