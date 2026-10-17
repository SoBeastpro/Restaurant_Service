package com.restaurant.restaurant.status.realtime;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration(proxyBeanMethods = false)
@EnableWebSocket
class WebSocketConfiguration implements WebSocketConfigurer {

    private final HallRealtimePublisher publisher;

    WebSocketConfiguration(HallRealtimePublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(publisher, "/api/v1/ws")
                .setAllowedOriginPatterns("*");
    }
}
