package com.restaurant.notification;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class NotificationServiceApplicationTests {
    @Container @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired MockMvc mockMvc;
    @MockitoBean JwtDecoder jwtDecoder;

    @Test
    void notificationIsStoredReadAndMarkedRead() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID restaurantId = UUID.randomUUID();
        String response = mockMvc.perform(post("/internal/v1/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId":"%s",
                                  "restaurantId":"%s",
                                  "type":"TABLE_NEEDS_CLEANING",
                                  "title":"Стол №7",
                                  "message":"Столу №7 нужна уборка",
                                  "payload":{"tableNumber":"7","status":"NEEDS_CLEANING"}
                                }
                                """.formatted(userId, restaurantId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("TABLE_NEEDS_CLEANING"))
                .andExpect(jsonPath("$.readAt").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        String notificationId = tools.jackson.databind.json.JsonMapper.builder()
                .build()
                .readTree(response)
                .get("id")
                .asString();
        mockMvc.perform(get("/api/v1/me/notifications")
                        .with(jwt().jwt(token -> token.subject(userId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].payload.tableNumber").value("7"));

        mockMvc.perform(patch(
                                "/api/v1/me/notifications/{notificationId}/read",
                                notificationId)
                        .with(jwt().jwt(token -> token.subject(userId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readAt").exists());
    }
}
