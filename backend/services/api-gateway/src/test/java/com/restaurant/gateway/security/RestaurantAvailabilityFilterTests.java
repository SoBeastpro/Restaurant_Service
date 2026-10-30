package com.restaurant.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

class RestaurantAvailabilityFilterTests {

    @Test
    void blockedRestaurantRequestDoesNotReachDownstreamRoute() {
        UUID restaurantId = UUID.randomUUID();
        DisposableServer statusServer = HttpServer.create()
                .port(0)
                .handle((request, response) -> response
                        .header("Content-Type", "application/json")
                        .sendString(Mono.just("{\"id\":\"%s\",\"status\":\"BLOCKED\"}"
                                .formatted(restaurantId))))
                .bindNow();
        try {
            var filter = new RestaurantAvailabilityFilter(
                    "http://127.0.0.1:" + statusServer.port(),
                    "local-internal-api-key-change-me");
            var exchange = MockServerWebExchange.from(MockServerHttpRequest.get(
                    "/api/v1/restaurants/%s/tasks".formatted(restaurantId)).build());
            AtomicBoolean downstreamCalled = new AtomicBoolean();

            filter.filter(exchange, ignored -> {
                downstreamCalled.set(true);
                return Mono.empty();
            }).block();

            assertThat(downstreamCalled).isFalse();
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(exchange.getResponse().getBodyAsString().block()).contains("RESTAURANT_BLOCKED");
        } finally {
            statusServer.disposeNow();
        }
    }
}
