package com.restaurant.reporting;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
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
class ReportingServiceApplicationTests {
    @Container @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired MockMvc mockMvc;
    @Autowired JdbcClient jdbc;
    @MockitoBean JwtDecoder jwtDecoder;

    @Test void contextLoads() {
    }

    @Test
    void systemAdminReadsPlatformMetricsForPeriod() throws Exception {
        LocalDate date = LocalDate.of(2026, 10, 30);
        jdbc.sql("""
                        INSERT INTO restaurant_daily_metrics (
                            restaurant_id, metric_date, shifts_opened, shifts_closed,
                            tasks_created, tasks_completed, tasks_cancelled, tasks_overdue,
                            table_status_changes, active_members
                        ) VALUES (:restaurantId, :date, 2, 1, 10, 8, 1, 2, 7, 5)
                        """)
                .param("restaurantId", UUID.randomUUID())
                .param("date", date)
                .update();

        mockMvc.perform(get("/api/v1/platform/metrics")
                        .param("from", date.toString())
                        .param("to", date.toString())
                        .with(jwt().jwt(token -> token
                                .subject(UUID.randomUUID().toString())
                                .claim("platform_roles", List.of("SYSTEM_ADMIN")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restaurantsWithData").value(1))
                .andExpect(jsonPath("$.activeMembers").value(5))
                .andExpect(jsonPath("$.tasksCompleted").value(8))
                .andExpect(jsonPath("$.taskCompletionRate").value(80.00))
                .andExpect(jsonPath("$.daily[0].tableStatusChanges").value(7));
    }
}
