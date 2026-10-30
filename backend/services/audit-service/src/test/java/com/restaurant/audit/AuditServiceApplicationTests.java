package com.restaurant.audit;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
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
class AuditServiceApplicationTests {
    @Container @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired MockMvc mockMvc;
    @Autowired JdbcClient jdbc;
    @MockitoBean JwtDecoder jwtDecoder;

    @Test void contextLoads() {
    }

    @Test
    void systemAdminReadsFilteredPlatformAuditAndSystemEvents() throws Exception {
        UUID actorId = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO audit_logs (actor_user_id, scope, action, outcome, details)
                        VALUES (:actorId, 'PLATFORM', 'USER_STATUS_CHANGED', 'SUCCESS', '{"status":"BLOCKED"}')
                        """)
                .param("actorId", actorId)
                .update();
        jdbc.sql("""
                        INSERT INTO system_events (level, service, event_type, message, details)
                        VALUES ('ERROR', 'billing-service', 'PAYMENT_PROVIDER_UNAVAILABLE', 'Provider timeout', '{}')
                        """).update();

        mockMvc.perform(get("/api/v1/platform/audit-log")
                        .param("actorUserId", actorId.toString())
                        .with(systemAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].action").value("USER_STATUS_CHANGED"));

        mockMvc.perform(get("/api/v1/platform/system-events")
                        .param("level", "error")
                        .param("service", "billing-service")
                        .with(systemAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].eventType").value("PAYMENT_PROVIDER_UNAVAILABLE"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor systemAdmin() {
        return jwt().jwt(token -> token
                .subject(UUID.randomUUID().toString())
                .claim("platform_roles", List.of("SYSTEM_ADMIN")));
    }
}
