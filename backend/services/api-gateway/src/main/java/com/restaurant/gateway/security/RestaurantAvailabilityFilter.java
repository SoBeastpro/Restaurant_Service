package com.restaurant.gateway.security;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

@Component
public class RestaurantAvailabilityFilter implements GlobalFilter, Ordered {

    private static final String INTERNAL_API_HEADER = "X-Internal-Api-Key";
    private static final Pattern RESTAURANT_PATH = Pattern.compile(
            "^/api/v1/restaurants/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})(?:/.*)?$");

    private final WebClient restaurantClient;
    private final String internalApiSecret;

    public RestaurantAvailabilityFilter(
            @Value("${services.restaurant.url}") String restaurantServiceUrl,
            @Value("${internal.api-secret}") String internalApiSecret) {
        this.restaurantClient = WebClient.create(restaurantServiceUrl);
        this.internalApiSecret = internalApiSecret;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        UUID pathRestaurantId = restaurantIdFromPath(exchange);
        return exchange.getPrincipal()
                .ofType(JwtAuthenticationToken.class)
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .flatMap(optionalAuthentication -> {
                    if (optionalAuthentication.isEmpty()) {
                        return pathRestaurantId == null
                                ? chain.filter(exchange)
                                : checkRestaurant(exchange, chain, pathRestaurantId);
                    }
                    JwtAuthenticationToken authentication = optionalAuthentication.get();
                    var platformRoles = authentication.getToken().getClaimAsStringList("platform_roles");
                    if (platformRoles != null && platformRoles.contains("SYSTEM_ADMIN")) {
                        return chain.filter(exchange);
                    }
                    UUID restaurantId = pathRestaurantId != null
                            ? pathRestaurantId
                            : restaurantIdFromToken(authentication);
                    return restaurantId == null
                            ? chain.filter(exchange)
                            : checkRestaurant(exchange, chain, restaurantId);
                });
    }

    private Mono<Void> checkRestaurant(
            ServerWebExchange exchange,
            GatewayFilterChain chain,
            UUID restaurantId) {
        Mono<RestaurantStatus> statusLookup = restaurantClient.get()
                .uri("/internal/v1/restaurants/{restaurantId}/status", restaurantId)
                .header(INTERNAL_API_HEADER, internalApiSecret)
                .retrieve()
                .bodyToMono(RestaurantStatus.class)
                .switchIfEmpty(Mono.error(new IllegalStateException("Empty restaurant status response")))
                .onErrorResume(WebClientResponseException.NotFound.class, exception -> problem(
                        exchange, HttpStatus.NOT_FOUND, "RESTAURANT_NOT_FOUND", "Ресторан не найден.")
                        .then(Mono.empty()))
                .onErrorResume(exception -> problem(exchange, HttpStatus.SERVICE_UNAVAILABLE,
                        "RESTAURANT_STATUS_UNAVAILABLE", "Не удалось проверить доступность ресторана.")
                        .then(Mono.empty()));
        return statusLookup.flatMap(status -> switch (status.status()) {
                    case "ACTIVE" -> chain.filter(exchange);
                    case "BLOCKED" -> problem(exchange, HttpStatus.FORBIDDEN,
                            "RESTAURANT_BLOCKED", "Работа ресторана заблокирована системным администратором.");
                    case "ARCHIVED" -> problem(exchange, HttpStatus.FORBIDDEN,
                            "RESTAURANT_ARCHIVED", "Ресторан находится в архиве.");
                    default -> problem(exchange, HttpStatus.SERVICE_UNAVAILABLE,
                            "RESTAURANT_STATUS_INVALID", "Получен неизвестный статус ресторана.");
                });
    }

    private static UUID restaurantIdFromPath(ServerWebExchange exchange) {
        Matcher matcher = RESTAURANT_PATH.matcher(exchange.getRequest().getPath().value());
        return matcher.matches() ? UUID.fromString(matcher.group(1)) : null;
    }

    private static UUID restaurantIdFromToken(JwtAuthenticationToken authentication) {
        String value = authentication.getToken().getClaimAsString("restaurant_id");
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static Mono<Void> problem(
            ServerWebExchange exchange,
            HttpStatus status,
            String code,
            String detail) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(new IllegalStateException(detail));
        }
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        byte[] body = ("{\"status\":" + status.value()
                + ",\"code\":\"" + code
                + "\",\"detail\":\"" + detail + "\"}").getBytes(StandardCharsets.UTF_8);
        exchange.getResponse().getHeaders().setContentLength(body.length);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -1;
    }

    private record RestaurantStatus(UUID id, String status) {
    }
}
