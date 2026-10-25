package com.restaurant.restaurant.status.infrastructure;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class OperationsTableClient {

    private final RestClient restClient;

    public OperationsTableClient(
            RestClient.Builder builder,
            @Value("${services.operations.url}") String operationsServiceUrl) {
        this.restClient = builder.baseUrl(operationsServiceUrl).build();
    }

    public TableAssignments activeAssignments(UUID restaurantId, UUID tableId) {
        return restClient.get()
                .uri(
                        "/internal/v1/restaurants/{restaurantId}/active-shift/tables/{tableId}/assignments",
                        restaurantId,
                        tableId)
                .retrieve()
                .body(TableAssignments.class);
    }

    public record TableAssignments(
            UUID shiftId,
            UUID managerMembershipId,
            UUID waiterMembershipId,
            UUID runnerMembershipId) {
    }
}
