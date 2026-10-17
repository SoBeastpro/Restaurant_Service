package com.restaurant.operations.shift.infrastructure;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class RestaurantClient {

    private final RestClient restClient;

    public RestaurantClient(
            RestClient.Builder builder,
            @Value("${services.restaurant.url}") String restaurantServiceUrl) {
        this.restClient = builder.baseUrl(restaurantServiceUrl).build();
    }

    public List<UUID> validateTables(UUID restaurantId, List<UUID> tableIds) {
        ValidateTablesResponse response = restClient.post()
                .uri("/internal/v1/restaurants/{restaurantId}/tables/validate", restaurantId)
                .body(new ValidateTablesRequest(tableIds))
                .retrieve()
                .body(ValidateTablesResponse.class);
        return response == null ? List.of() : response.tableIds();
    }

    public List<UUID> activeTables(UUID restaurantId) {
        ValidateTablesResponse response = restClient.get()
                .uri("/internal/v1/restaurants/{restaurantId}/tables/active-ids", restaurantId)
                .retrieve()
                .body(ValidateTablesResponse.class);
        return response == null ? List.of() : response.tableIds();
    }

    private record ValidateTablesRequest(List<UUID> tableIds) {
    }

    private record ValidateTablesResponse(List<UUID> tableIds) {
    }
}
