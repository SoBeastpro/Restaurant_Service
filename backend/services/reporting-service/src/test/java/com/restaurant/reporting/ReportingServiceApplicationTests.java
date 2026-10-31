package com.restaurant.reporting;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.restaurant.reporting.platform.infrastructure.PlatformSummaryClient;
import com.restaurant.reporting.platform.infrastructure.PlatformSummaryClient.BillingSummary;
import com.restaurant.reporting.platform.infrastructure.PlatformSummaryClient.PlatformSummary;
import com.restaurant.reporting.platform.infrastructure.PlatformSummaryClient.RestaurantSummary;
import com.restaurant.reporting.platform.infrastructure.PlatformSummaryClient.UserSummary;
import com.restaurant.reporting.messaging.ActivityMetricsListener;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessageBuilder;
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

import static org.mockito.Mockito.when;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class ReportingServiceApplicationTests {
    @Container @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired MockMvc mockMvc;
    @Autowired JdbcClient jdbc;
    @Autowired ActivityMetricsListener activityMetricsListener;
    @MockitoBean JwtDecoder jwtDecoder;
    @MockitoBean PlatformSummaryClient platformSummaryClient;

    @Test void contextLoads() {
    }

    @Test
    void systemAdminReadsPlatformMetricsForPeriod() throws Exception {
        LocalDate date = LocalDate.of(2026, 10, 29);
        when(platformSummaryClient.load()).thenReturn(new PlatformSummary(
                new UserSummary(20, 18, 1, 1),
                new RestaurantSummary(6, 5, 1, 0),
                new BillingSummary(7, 5, 1, 1, 2, 1)));
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
                .andExpect(jsonPath("$.usersTotal").value(20))
                .andExpect(jsonPath("$.activeRestaurants").value(5))
                .andExpect(jsonPath("$.activeSubscriptions").value(5))
                .andExpect(jsonPath("$.restaurantsWithData").value(1))
                .andExpect(jsonPath("$.activeMembers").value(5))
                .andExpect(jsonPath("$.tasksCompleted").value(8))
                .andExpect(jsonPath("$.taskCompletionRate").value(80.00))
                .andExpect(jsonPath("$.daily[0].tableStatusChanges").value(7));
    }

    @Test
    void domainEventsBuildIdempotentDailyMetrics() throws Exception {
        UUID restaurantId = UUID.randomUUID();
        UUID createdEventId = UUID.randomUUID();
        UUID completedEventId = UUID.randomUUID();
        activityMetricsListener.consume(message(createdEventId, restaurantId,
                "operations.task.created.v1", "CREATED"));
        var completed = message(completedEventId, restaurantId,
                "operations.task.status-changed.v1", "COMPLETED");
        activityMetricsListener.consume(completed);
        activityMetricsListener.consume(completed);

        var counters = jdbc.sql("""
                        SELECT tasks_created, tasks_completed
                        FROM restaurant_daily_metrics
                        WHERE restaurant_id = :restaurantId AND metric_date = DATE '2026-10-30'
                        """)
                .param("restaurantId", restaurantId)
                .query((result, rowNumber) -> List.of(
                        result.getInt("tasks_created"), result.getInt("tasks_completed")))
                .single();
        org.assertj.core.api.Assertions.assertThat(counters).containsExactly(1, 1);
    }

    private static org.springframework.amqp.core.Message message(
            UUID eventId,
            UUID restaurantId,
            String eventType,
            String status) {
        String body = """
                {
                  "eventId":"%s",
                  "eventType":"%s",
                  "producer":"operations-service",
                  "restaurantId":"%s",
                  "occurredAt":"2026-10-30T12:00:00Z",
                  "payload":{"restaurantId":"%s","status":"%s"}
                }
                """.formatted(eventId, eventType, restaurantId, restaurantId, status);
        return MessageBuilder.withBody(body.getBytes(java.nio.charset.StandardCharsets.UTF_8)).build();
    }
}
