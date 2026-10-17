package com.restaurant.restaurant.setup.api;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

import com.restaurant.foundation.web.ApiException;
import com.restaurant.restaurant.setup.infrastructure.RestaurantSetupRepository;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/internal/v1/restaurants/{restaurantId}/tables")
class InternalTableController {

    private final RestaurantSetupRepository repository;

    InternalTableController(RestaurantSetupRepository repository) {
        this.repository = repository;
    }

    @PostMapping("/validate")
    ValidateTablesResponse validate(
            @PathVariable UUID restaurantId,
            @Valid @RequestBody ValidateTablesRequest request) {
        List<UUID> requested = List.copyOf(new LinkedHashSet<>(request.tableIds()));
        List<UUID> found = repository.findActiveTableIds(restaurantId, requested);
        if (found.size() != requested.size()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_SHIFT_TABLE",
                    "Можно закрепить только активные столы этого ресторана.");
        }
        return new ValidateTablesResponse(found);
    }

    @GetMapping("/active-ids")
    ValidateTablesResponse activeIds(@PathVariable UUID restaurantId) {
        return new ValidateTablesResponse(repository.findAllActiveTableIds(restaurantId));
    }

    record ValidateTablesRequest(@NotNull List<@NotNull UUID> tableIds) {
    }

    record ValidateTablesResponse(List<UUID> tableIds) {
    }
}
