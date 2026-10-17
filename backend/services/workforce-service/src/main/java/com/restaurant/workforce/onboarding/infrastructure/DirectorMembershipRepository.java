package com.restaurant.workforce.onboarding.infrastructure;

import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class DirectorMembershipRepository {

    private final JdbcClient jdbc;

    public DirectorMembershipRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<DirectorRecord> findByRestaurant(UUID restaurantId) {
        return jdbc.sql("""
                        SELECT id, user_id
                        FROM restaurant_members
                        WHERE restaurant_id = :restaurantId
                          AND role_code = 'DIRECTOR'
                          AND status <> 'ARCHIVED'
                        """)
                .param("restaurantId", restaurantId)
                .query((result, rowNumber) -> new DirectorRecord(
                        result.getObject("id", UUID.class),
                        result.getObject("user_id", UUID.class)))
                .optional();
    }

    public void insert(UUID restaurantId, UUID userId, String firstName, String lastName) {
        jdbc.sql("""
                        INSERT INTO restaurant_members (
                            restaurant_id, user_id, role_code, first_name, last_name, status,
                            last_active_at
                        ) VALUES (
                            :restaurantId, :userId, 'DIRECTOR', :firstName, :lastName, 'ACTIVE',
                            CURRENT_TIMESTAMP
                        )
                        """)
                .param("restaurantId", restaurantId)
                .param("userId", userId)
                .param("firstName", firstName)
                .param("lastName", lastName)
                .update();
    }

    public record DirectorRecord(UUID id, UUID userId) {
    }
}
