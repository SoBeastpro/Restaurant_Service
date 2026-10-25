package com.restaurant.restaurant.status.realtime;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.socket.WebSocketSession;

import tools.jackson.databind.ObjectMapper;

class HallRealtimePublisherTests {

    @Test
    void broadcastsOnlyToSessionsFromTheSameRestaurant() throws Exception {
        ObjectMapper objectMapper = org.mockito.Mockito.mock(ObjectMapper.class);
        when(objectMapper.writeValueAsString(org.mockito.ArgumentMatchers.any()))
                .thenReturn("{\"type\":\"TABLE_STATUS_CHANGED\"}");
        HallRealtimePublisher publisher = new HallRealtimePublisher(objectMapper);

        UUID restaurantId = UUID.randomUUID();
        WebSocketSession ownSession = session("own", restaurantId);
        WebSocketSession otherSession = session("other", UUID.randomUUID());
        publisher.afterConnectionEstablished(ownSession);
        publisher.afterConnectionEstablished(otherSession);

        publisher.publish(new TableStatusChangedRealtimeEvent(
                restaurantId,
                UUID.randomUUID(),
                "7",
                UUID.randomUUID(),
                "READY",
                "NEEDS_SERVING",
                null,
                UUID.randomUUID(),
                Instant.parse("2026-10-17T10:00:00Z")));

        verify(ownSession).sendMessage(argThat(message ->
                "{\"type\":\"TABLE_STATUS_CHANGED\"}".equals(message.getPayload())));
        verify(otherSession, never()).sendMessage(org.mockito.ArgumentMatchers.any());
    }

    private static WebSocketSession session(String id, UUID restaurantId) {
        WebSocketSession session = org.mockito.Mockito.mock(WebSocketSession.class);
        Jwt jwt = new Jwt(
                "token-" + id,
                Instant.parse("2026-10-17T09:00:00Z"),
                Instant.parse("2026-10-17T11:00:00Z"),
                Map.of("alg", "none"),
                Map.of("sub", UUID.randomUUID().toString(), "restaurant_id", restaurantId.toString()));
        when(session.getId()).thenReturn(id);
        when(session.getPrincipal()).thenReturn(new JwtAuthenticationToken(jwt));
        when(session.isOpen()).thenReturn(true);
        return session;
    }
}
