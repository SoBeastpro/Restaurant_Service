package com.restaurant.workforce.member.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.restaurant.workforce.member.api.MemberDtos.EmployeeResponse;
import com.restaurant.workforce.member.api.MemberDtos.EmployeeAssignmentResponse;
import com.restaurant.workforce.member.api.MemberDtos.MemberReferenceResponse;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class MemberRepository {

    private final JdbcClient jdbc;

    public MemberRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<MemberReferenceResponse> findActiveByUser(UUID restaurantId, UUID userId) {
        return jdbc.sql("""
                        SELECT id, restaurant_id, user_id, role_code, job_title, status
                        FROM restaurant_members
                        WHERE restaurant_id = :restaurantId
                          AND user_id = :userId
                          AND status = 'ACTIVE'
                        """)
                .param("restaurantId", restaurantId)
                .param("userId", userId)
                .query((result, rowNumber) -> new MemberReferenceResponse(
                        result.getObject("id", UUID.class),
                        result.getObject("restaurant_id", UUID.class),
                        result.getObject("user_id", UUID.class),
                        result.getString("role_code"),
                        result.getString("job_title"),
                        result.getString("status")))
                .optional();
    }

    public Optional<MemberReferenceResponse> findActiveById(UUID restaurantId, UUID membershipId) {
        return jdbc.sql("""
                        SELECT id, restaurant_id, user_id, role_code, job_title, status
                        FROM restaurant_members
                        WHERE restaurant_id = :restaurantId
                          AND id = :membershipId
                          AND status = 'ACTIVE'
                        """)
                .param("restaurantId", restaurantId)
                .param("membershipId", membershipId)
                .query((result, rowNumber) -> new MemberReferenceResponse(
                        result.getObject("id", UUID.class),
                        result.getObject("restaurant_id", UUID.class),
                        result.getObject("user_id", UUID.class),
                        result.getString("role_code"),
                        result.getString("job_title"),
                        result.getString("status")))
                .optional();
    }

    public List<MemberReferenceResponse> findActiveManagers(UUID restaurantId) {
        return jdbc.sql("""
                        SELECT id, restaurant_id, user_id, role_code, job_title, status
                        FROM restaurant_members
                        WHERE restaurant_id = :restaurantId
                          AND role_code = 'MANAGER'
                          AND status = 'ACTIVE'
                          AND user_id IS NOT NULL
                        ORDER BY created_at, id
                        """)
                .param("restaurantId", restaurantId)
                .query((result, rowNumber) -> new MemberReferenceResponse(
                        result.getObject("id", UUID.class),
                        result.getObject("restaurant_id", UUID.class),
                        result.getObject("user_id", UUID.class),
                        result.getString("role_code"),
                        result.getString("job_title"),
                        result.getString("status")))
                .list();
    }

    public List<EmployeeResponse> findActiveEmployees(UUID restaurantId) {
        return jdbc.sql("""
                        SELECT id, user_id, first_name, last_name, job_title, status
                        FROM restaurant_members
                        WHERE restaurant_id = :restaurantId
                          AND role_code = 'EMPLOYEE'
                          AND status = 'ACTIVE'
                        ORDER BY last_name, first_name, id
                        """)
                .param("restaurantId", restaurantId)
                .query((result, rowNumber) -> new EmployeeResponse(
                        result.getObject("id", UUID.class),
                        result.getObject("user_id", UUID.class),
                        result.getString("first_name"),
                        result.getString("last_name"),
                        result.getString("job_title"),
                        result.getString("status")))
                .list();
    }

    public List<EmployeeAssignmentResponse> findActiveEmployeesForAssignment(
            UUID restaurantId,
            List<UUID> membershipIds) {
        if (membershipIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("""
                        SELECT id, job_title
                        FROM restaurant_members
                        WHERE restaurant_id = :restaurantId
                          AND id IN (:membershipIds)
                          AND role_code = 'EMPLOYEE'
                          AND status = 'ACTIVE'
                        ORDER BY id
                        """)
                .param("restaurantId", restaurantId)
                .param("membershipIds", membershipIds)
                .query((result, rowNumber) -> new EmployeeAssignmentResponse(
                        result.getObject("id", UUID.class),
                        result.getString("job_title")))
                .list();
    }
}
