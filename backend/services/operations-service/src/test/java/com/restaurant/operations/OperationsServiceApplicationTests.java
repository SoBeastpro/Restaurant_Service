package com.restaurant.operations;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import com.restaurant.operations.shift.infrastructure.NotificationClient;
import com.restaurant.operations.shift.infrastructure.RestaurantClient;
import com.restaurant.operations.shift.infrastructure.WorkforceClient;
import com.restaurant.operations.shift.infrastructure.WorkforceClient.EmployeeAssignmentReference;
import com.restaurant.operations.shift.infrastructure.WorkforceClient.MemberReference;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class OperationsServiceApplicationTests {

    @Container @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcClient jdbc;

    @MockitoBean WorkforceClient workforceClient;
    @MockitoBean RestaurantClient restaurantClient;
    @MockitoBean NotificationClient notificationClient;
    @MockitoBean JwtDecoder jwtDecoder;

    @Test
    void draftIsConfiguredOpenedAndEmployeeSeesOnlyPersonalPlan() throws Exception {
        UUID restaurantId = UUID.randomUUID();
        UUID managerUserId = UUID.randomUUID();
        UUID managerMembershipId = UUID.randomUUID();
        UUID employeeUserId = UUID.randomUUID();
        UUID employeeMembershipId = UUID.randomUUID();
        UUID otherEmployeeUserId = UUID.randomUUID();
        UUID otherEmployeeMembershipId = UUID.randomUUID();
        UUID tableOne = UUID.randomUUID();

        when(workforceClient.findMember(restaurantId, managerUserId)).thenReturn(new MemberReference(
                managerMembershipId, restaurantId, managerUserId, "MANAGER", null, "ACTIVE"));
        when(workforceClient.findMember(restaurantId, employeeUserId)).thenReturn(new MemberReference(
                employeeMembershipId, restaurantId, employeeUserId, "EMPLOYEE", "WAITER", "ACTIVE"));
        when(workforceClient.findMemberByMembership(restaurantId, employeeMembershipId)).thenReturn(new MemberReference(
                employeeMembershipId, restaurantId, employeeUserId, "EMPLOYEE", "WAITER", "ACTIVE"));
        when(workforceClient.findMemberByMembership(restaurantId, otherEmployeeMembershipId)).thenReturn(new MemberReference(
                otherEmployeeMembershipId, restaurantId, otherEmployeeUserId, "EMPLOYEE", "RUNNER", "ACTIVE"));
        when(workforceClient.validateEmployees(
                restaurantId,
                List.of(employeeMembershipId, otherEmployeeMembershipId)))
                .thenReturn(List.of(
                        new EmployeeAssignmentReference(employeeMembershipId, "WAITER"),
                        new EmployeeAssignmentReference(otherEmployeeMembershipId, "RUNNER")));
        when(restaurantClient.validateTables(restaurantId, List.of(tableOne)))
                .thenReturn(List.of(tableOne));
        when(restaurantClient.activeTables(restaurantId)).thenReturn(List.of(tableOne));

        JsonNode shift = objectMapper.readTree(mockMvc.perform(post(
                                "/api/v1/restaurants/{restaurantId}/shifts", restaurantId)
                        .with(managerJwt(managerUserId, restaurantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "scheduledStartAt":"2026-10-17T07:00:00Z",
                                  "scheduledEndAt":"2026-10-17T19:00:00Z"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PLANNED"))
                .andExpect(jsonPath("$.participants.length()").value(0))
                .andReturn().getResponse().getContentAsString());
        UUID shiftId = UUID.fromString(shift.get("id").asString());

        mockMvc.perform(put(
                                "/api/v1/restaurants/{restaurantId}/shifts/{shiftId}/participants",
                                restaurantId,
                                shiftId)
                        .with(managerJwt(managerUserId, restaurantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"participants":[
                                  {"membershipId":"%s","tableIds":["%s"]},
                                  {"membershipId":"%s","tableIds":["%s"]}
                                ]}
                                """.formatted(
                                employeeMembershipId,
                                tableOne,
                                otherEmployeeMembershipId,
                                tableOne)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participants.length()").value(2))
                .andExpect(jsonPath("$.participants[*].position")
                        .value(containsInAnyOrder("WAITER", "RUNNER")));

        JsonNode nearTask = createTask(
                restaurantId,
                shiftId,
                managerUserId,
                employeeMembershipId,
                "Подготовить стол",
                "TIME",
                "2026-10-17T09:00:00Z");
        JsonNode endTask = createTask(
                restaurantId,
                shiftId,
                managerUserId,
                employeeMembershipId,
                "Проверить приборы",
                "SHIFT_END",
                null);
        JsonNode noDeadlineTask = createTask(
                restaurantId,
                shiftId,
                managerUserId,
                employeeMembershipId,
                "Без срока",
                "NONE",
                null);
        JsonNode otherTask = createTask(
                restaurantId,
                shiftId,
                managerUserId,
                otherEmployeeMembershipId,
                "Чужая задача",
                "NONE",
                null);

        mockMvc.perform(post(
                                "/api/v1/restaurants/{restaurantId}/shifts/{shiftId}/start",
                                restaurantId,
                                shiftId)
                        .with(managerJwt(managerUserId, restaurantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(get(
                                "/internal/v1/restaurants/{restaurantId}/active-shift/tables/{tableId}/assignments",
                                restaurantId,
                                tableOne))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.managerMembershipId").value(managerMembershipId.toString()))
                .andExpect(jsonPath("$.waiterMembershipId").value(employeeMembershipId.toString()))
                .andExpect(jsonPath("$.runnerMembershipId").value(otherEmployeeMembershipId.toString()));

        mockMvc.perform(get("/api/v1/me/current-shift")
                        .with(jwt().jwt(token -> token
                                .subject(employeeUserId.toString())
                                .claim("role", "EMPLOYEE")
                                .claim("restaurant_id", restaurantId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedTableIds.length()").value(1))
                .andExpect(jsonPath("$.assignedTableIds[0]").value(tableOne.toString()))
                .andExpect(jsonPath("$.tasks.length()").value(3))
                .andExpect(jsonPath("$.tasks[0].id").value(nearTask.get("id").asString()))
                .andExpect(jsonPath("$.tasks[1].deadlineType").value("SHIFT_END"))
                .andExpect(jsonPath("$.tasks[1].deadlineAt").value("2026-10-17T19:00:00Z"))
                .andExpect(jsonPath("$.tasks[2].deadlineType").value("NONE"));

        mockMvc.perform(post(
                                "/api/v1/restaurants/{restaurantId}/tasks/{taskId}/status-changes",
                                restaurantId,
                                UUID.fromString(nearTask.get("id").asString()))
                        .with(jwt().jwt(token -> token
                                .subject(employeeUserId.toString())
                                .claim("role", "EMPLOYEE")
                                .claim("restaurant_id", restaurantId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        mockMvc.perform(post(
                                "/api/v1/restaurants/{restaurantId}/tasks/{taskId}/assign",
                                restaurantId,
                                UUID.fromString(endTask.get("id").asString()))
                        .with(managerJwt(managerUserId, restaurantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assignedToMembershipId\":\"%s\"}".formatted(otherEmployeeMembershipId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedToMembershipId").value(otherEmployeeMembershipId.toString()));

        mockMvc.perform(post(
                                "/api/v1/restaurants/{restaurantId}/shifts/{shiftId}/close",
                                restaurantId,
                                shiftId)
                        .with(managerJwt(managerUserId, restaurantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"unfinishedTasks":[
                                  {"taskId":"%s","action":"CANCEL","comment":"Больше не требуется"},
                                  {"taskId":"%s","action":"CARRY_OVER"},
                                  {"taskId":"%s","action":"CANCEL"}
                                ]}
                                """.formatted(
                                endTask.get("id").asString(),
                                noDeadlineTask.get("id").asString(),
                                otherTask.get("id").asString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTasks").value(4))
                .andExpect(jsonPath("$.completedTasks").value(1))
                .andExpect(jsonPath("$.cancelledTasks").value(2))
                .andExpect(jsonPath("$.carriedOverTasks").value(1));

        mockMvc.perform(get(
                                "/api/v1/restaurants/{restaurantId}/tasks/pending-carry-over",
                                restaurantId)
                        .with(managerJwt(managerUserId, restaurantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(noDeadlineTask.get("id").asString()))
                .andExpect(jsonPath("$[0].assignedToMembershipId").doesNotExist());

        mockMvc.perform(get(
                                "/api/v1/restaurants/{restaurantId}/tasks/{taskId}/history",
                                restaurantId,
                                UUID.fromString(endTask.get("id").asString()))
                        .with(managerJwt(managerUserId, restaurantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventType").value("CREATED"))
                .andExpect(jsonPath("$[1].eventType").value("ASSIGNED"))
                .andExpect(jsonPath("$[2].eventType").value("CANCELLED"))
                .andExpect(jsonPath("$[2].comment").value("Больше не требуется"));

        long cancellationHistory = jdbc.sql("""
                        SELECT count(*) FROM task_history
                        WHERE task_id = :taskId AND event_type = 'CANCELLED'
                        """)
                .param("taskId", UUID.fromString(endTask.get("id").asString()))
                .query(Long.class)
                .single();
        org.assertj.core.api.Assertions.assertThat(cancellationHistory).isEqualTo(1);
        String persistedStatus = jdbc.sql("SELECT status FROM tasks WHERE id = :taskId")
                .param("taskId", UUID.fromString(endTask.get("id").asString()))
                .query(String.class)
                .single();
        org.assertj.core.api.Assertions.assertThat(persistedStatus).isEqualTo("CANCELLED");

        when(workforceClient.validateEmployees(
                restaurantId,
                List.of(employeeMembershipId, otherEmployeeMembershipId)))
                .thenReturn(List.of(
                        new EmployeeAssignmentReference(employeeMembershipId, "WAITER"),
                        new EmployeeAssignmentReference(otherEmployeeMembershipId, "RUNNER")));
        when(restaurantClient.validateTables(restaurantId, List.of(tableOne)))
                .thenReturn(List.of(tableOne));
        JsonNode nextShift = objectMapper.readTree(mockMvc.perform(post(
                                "/api/v1/restaurants/{restaurantId}/shifts", restaurantId)
                        .with(managerJwt(managerUserId, restaurantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "scheduledStartAt":"2026-10-18T07:00:00Z",
                                  "scheduledEndAt":"2026-10-18T19:00:00Z"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        UUID nextShiftId = UUID.fromString(nextShift.get("id").asString());
        mockMvc.perform(put(
                                "/api/v1/restaurants/{restaurantId}/shifts/{shiftId}/participants",
                                restaurantId,
                                nextShiftId)
                        .with(managerJwt(managerUserId, restaurantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"participants":[
                                  {"membershipId":"%s","tableIds":["%s"]},
                                  {"membershipId":"%s","tableIds":["%s"]}
                                ]}
                                """.formatted(
                                employeeMembershipId,
                                tableOne,
                                otherEmployeeMembershipId,
                                tableOne)))
                .andExpect(status().isOk());
        mockMvc.perform(post(
                                "/api/v1/restaurants/{restaurantId}/shifts/{shiftId}/tasks/{taskId}/assign-carried",
                                restaurantId,
                                nextShiftId,
                                UUID.fromString(noDeadlineTask.get("id").asString()))
                        .with(managerJwt(managerUserId, restaurantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"assignedToMembershipId":"%s","deadlineType":"SHIFT_END"}
                                """.formatted(employeeMembershipId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shiftId").value(nextShiftId.toString()))
                .andExpect(jsonPath("$.assignedToMembershipId").value(employeeMembershipId.toString()))
                .andExpect(jsonPath("$.deadlineAt").value("2026-10-18T19:00:00Z"));

        mockMvc.perform(get(
                                "/api/v1/restaurants/{restaurantId}/tasks/pending-carry-over",
                                restaurantId)
                        .with(managerJwt(managerUserId, restaurantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        verify(notificationClient).create(
                eq(employeeUserId),
                eq(restaurantId),
                eq("TASK_REASSIGNED"),
                anyString(),
                anyString(),
                anyMap());
        verify(notificationClient, atLeastOnce()).create(
                eq(otherEmployeeUserId),
                eq(restaurantId),
                eq("TASK_ASSIGNED"),
                anyString(),
                anyString(),
                anyMap());
    }

    @Test
    void oneTableCannotBeAssignedToTwoEmployees() throws Exception {
        UUID restaurantId = UUID.randomUUID();
        UUID directorUserId = UUID.randomUUID();
        UUID firstEmployee = UUID.randomUUID();
        UUID secondEmployee = UUID.randomUUID();
        UUID tableId = UUID.randomUUID();
        when(workforceClient.findMember(restaurantId, directorUserId)).thenReturn(new MemberReference(
                UUID.randomUUID(), restaurantId, directorUserId, "DIRECTOR", null, "ACTIVE"));
        when(workforceClient.validateEmployees(restaurantId, List.of(firstEmployee, secondEmployee)))
                .thenReturn(List.of(
                        new EmployeeAssignmentReference(firstEmployee, "WAITER"),
                        new EmployeeAssignmentReference(secondEmployee, "WAITER")));

        JsonNode shift = objectMapper.readTree(mockMvc.perform(post(
                                "/api/v1/restaurants/{restaurantId}/shifts", restaurantId)
                        .with(directorJwt(directorUserId, restaurantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "scheduledStartAt":"2026-10-17T07:00:00Z",
                                  "scheduledEndAt":"2026-10-17T19:00:00Z"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());

        mockMvc.perform(put(
                                "/api/v1/restaurants/{restaurantId}/shifts/{shiftId}/participants",
                                restaurantId,
                                UUID.fromString(shift.get("id").asString()))
                        .with(directorJwt(directorUserId, restaurantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"participants":[
                                  {"membershipId":"%s","tableIds":["%s"]},
                                  {"membershipId":"%s","tableIds":["%s"]}
                                ]}
                                """.formatted(firstEmployee, tableId, secondEmployee, tableId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TABLE_ASSIGNED_MORE_THAN_ONCE_FOR_POSITION"));
    }

    private JsonNode createTask(
            UUID restaurantId,
            UUID shiftId,
            UUID managerUserId,
            UUID assignee,
            String description,
            String deadlineType,
            String deadlineAt) throws Exception {
        String deadline = deadlineAt == null ? "" : ",\"deadlineAt\":\"" + deadlineAt + "\"";
        String body = """
                {"description":"%s","assignedToMembershipId":"%s","deadlineType":"%s"%s}
                """.formatted(description, assignee, deadlineType, deadline);
        return objectMapper.readTree(mockMvc.perform(post(
                                "/api/v1/restaurants/{restaurantId}/shifts/{shiftId}/tasks",
                                restaurantId,
                                shiftId)
                        .with(managerJwt(managerUserId, restaurantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    private static RequestPostProcessor managerJwt(UUID userId, UUID restaurantId) {
        return jwt().jwt(token -> token
                .subject(userId.toString())
                .claim("role", "MANAGER")
                .claim("restaurant_id", restaurantId.toString()));
    }

    private static RequestPostProcessor directorJwt(UUID userId, UUID restaurantId) {
        return jwt().jwt(token -> token
                .subject(userId.toString())
                .claim("role", "DIRECTOR")
                .claim("restaurant_id", restaurantId.toString()));
    }
}
