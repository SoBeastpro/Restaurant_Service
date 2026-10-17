package com.restaurant.operations.shift.infrastructure;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class WorkforceClient {

    private final RestClient restClient;

    public WorkforceClient(
            RestClient.Builder builder,
            @Value("${services.workforce.url}") String workforceServiceUrl) {
        this.restClient = builder.baseUrl(workforceServiceUrl).build();
    }

    public MemberReference findMember(UUID restaurantId, UUID userId) {
        return restClient.get()
                .uri("/internal/v1/restaurants/{restaurantId}/members/by-user/{userId}", restaurantId, userId)
                .retrieve()
                .body(MemberReference.class);
    }

    public MemberReference findMemberByMembership(UUID restaurantId, UUID membershipId) {
        return restClient.get()
                .uri("/internal/v1/restaurants/{restaurantId}/members/{membershipId}", restaurantId, membershipId)
                .retrieve()
                .body(MemberReference.class);
    }

    public List<EmployeeAssignmentReference> validateEmployees(UUID restaurantId, List<UUID> membershipIds) {
        ValidateEmployeesResponse response = restClient.post()
                .uri("/internal/v1/restaurants/{restaurantId}/members/validate-employees", restaurantId)
                .body(new ValidateEmployeesRequest(membershipIds))
                .retrieve()
                .body(ValidateEmployeesResponse.class);
        return response == null ? List.of() : response.employees();
    }

    public record MemberReference(
            UUID membershipId,
            UUID restaurantId,
            UUID userId,
            String role,
            String position,
            String status) {
    }

    public record EmployeeAssignmentReference(UUID membershipId, String position) {
    }

    private record ValidateEmployeesRequest(List<UUID> membershipIds) {
    }

    private record ValidateEmployeesResponse(List<EmployeeAssignmentReference> employees) {
    }
}
