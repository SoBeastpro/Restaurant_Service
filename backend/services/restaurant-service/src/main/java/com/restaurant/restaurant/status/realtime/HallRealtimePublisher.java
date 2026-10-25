package com.restaurant.restaurant.status.realtime;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class HallRealtimePublisher extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(HallRealtimePublisher.class);

    private final ObjectMapper objectMapper;
    private final Map<UUID, Set<WebSocketSession>> sessionsByRestaurant = new ConcurrentHashMap<>();
    private final Map<String, UUID> restaurantBySession = new ConcurrentHashMap<>();

    public HallRealtimePublisher(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        if (!(session.getPrincipal() instanceof JwtAuthenticationToken authentication)) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("JWT authentication required"));
            return;
        }
        String restaurantClaim = authentication.getToken().getClaimAsString("restaurant_id");
        if (restaurantClaim == null) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("restaurant_id claim required"));
            return;
        }
        try {
            UUID restaurantId = UUID.fromString(restaurantClaim);
            restaurantBySession.put(session.getId(), restaurantId);
            sessionsByRestaurant.computeIfAbsent(restaurantId, ignored -> ConcurrentHashMap.newKeySet())
                    .add(session);
        } catch (IllegalArgumentException exception) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("invalid restaurant_id claim"));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        remove(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        remove(session);
        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    public void publish(TableStatusChangedRealtimeEvent event) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    broadcast(event);
                }
            });
            return;
        }
        broadcast(event);
    }

    private void broadcast(TableStatusChangedRealtimeEvent event) {
        String body;
        try {
            body = objectMapper.writeValueAsString(new RealtimeMessage(
                    "TABLE_STATUS_CHANGED",
                    event.restaurantId(),
                    event.changedAt(),
                    event));
        } catch (JacksonException exception) {
            log.error("Cannot serialize table status WebSocket event", exception);
            return;
        }

        TextMessage message = new TextMessage(body);
        for (WebSocketSession session : sessionsByRestaurant.getOrDefault(event.restaurantId(), Set.of())) {
            if (!session.isOpen()) {
                remove(session);
                continue;
            }
            try {
                synchronized (session) {
                    session.sendMessage(message);
                }
            } catch (IOException exception) {
                log.warn("Cannot send table status WebSocket event to session {}", session.getId(), exception);
                remove(session);
            }
        }
    }

    private void remove(WebSocketSession session) {
        UUID restaurantId = restaurantBySession.remove(session.getId());
        if (restaurantId == null) {
            return;
        }
        Set<WebSocketSession> sessions = sessionsByRestaurant.get(restaurantId);
        if (sessions == null) {
            return;
        }
        sessions.remove(session);
        if (sessions.isEmpty()) {
            sessionsByRestaurant.remove(restaurantId, sessions);
        }
    }

    private record RealtimeMessage(
            String type,
            UUID restaurantId,
            Instant occurredAt,
            TableStatusChangedRealtimeEvent payload) {
    }
}
