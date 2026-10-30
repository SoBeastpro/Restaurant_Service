package com.restaurant.billing;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class BillingServiceApplicationTests {
    @Container @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean JwtDecoder jwtDecoder;

    @Test void contextLoads() {
    }

    @Test
    void systemAdminManagesTariffAndSubscriptionLifecycle() throws Exception {
        UUID adminId = UUID.randomUUID();
        UUID restaurantId = UUID.randomUUID();

        JsonNode tariff = objectMapper.readTree(mockMvc.perform(post("/api/v1/platform/tariffs")
                        .with(systemAdmin(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code":"pro-test",
                                  "name":"Pro",
                                  "price":1990,
                                  "currency":"rub",
                                  "durationDays":30,
                                  "features":{"employees":50}
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("PRO-TEST"))
                .andExpect(jsonPath("$.currency").value("RUB"))
                .andReturn().getResponse().getContentAsString());

        JsonNode subscription = objectMapper.readTree(mockMvc.perform(post("/api/v1/platform/subscriptions")
                        .with(systemAdmin(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"restaurantId":"%s","planId":"%s"}
                                """.formatted(restaurantId, tariff.get("id").asString())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.restaurantId").value(restaurantId.toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.activatedByUserId").value(adminId.toString()))
                .andReturn().getResponse().getContentAsString());

        String subscriptionId = subscription.get("id").asString();
        String oldEndsAt = subscription.get("endsAt").asString();
        mockMvc.perform(post("/api/v1/platform/subscriptions/{id}/extend", subscriptionId)
                        .with(systemAdmin(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"days\":7,\"reason\":\"тест\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.endsAt").value(org.hamcrest.Matchers.not(oldEndsAt)));

        mockMvc.perform(post("/api/v1/platform/subscriptions/{id}/cancel", subscriptionId)
                        .with(systemAdmin(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"по запросу ресторана\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelledAt").isNotEmpty());
    }

    @Test
    void platformBillingRejectsAuthenticatedNonAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/platform/tariffs")
                        .with(jwt().jwt(token -> token
                                .subject(UUID.randomUUID().toString())
                                .claim("platform_roles", List.of()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SYSTEM_ADMIN_REQUIRED"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor systemAdmin(UUID userId) {
        return jwt().jwt(token -> token
                .subject(userId.toString())
                .claim("platform_roles", List.of("SYSTEM_ADMIN")));
    }
}
