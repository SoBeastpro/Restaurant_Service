package com.restaurant.restaurant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.restaurant.restaurant.setup.infrastructure.IdentityOnboardingClient;
import com.restaurant.restaurant.setup.infrastructure.IdentityOnboardingClient.DirectorProfile;
import com.restaurant.restaurant.setup.infrastructure.WorkforceOnboardingClient;
import com.restaurant.restaurant.status.infrastructure.NotificationClient;
import com.restaurant.restaurant.status.infrastructure.OperationsTableClient;
import com.restaurant.restaurant.status.infrastructure.OperationsTableClient.TableAssignments;
import com.restaurant.restaurant.status.infrastructure.WorkforceMemberClient;
import com.restaurant.restaurant.status.infrastructure.WorkforceMemberClient.MemberReference;
import com.restaurant.restaurant.status.realtime.HallRealtimePublisher;

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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class RestaurantServiceApplicationTests {

    @Container @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcClient jdbc;

    @MockitoBean IdentityOnboardingClient identityClient;
    @MockitoBean WorkforceOnboardingClient workforceClient;
    @MockitoBean WorkforceMemberClient workforceMemberClient;
    @MockitoBean OperationsTableClient operationsTableClient;
    @MockitoBean NotificationClient notificationClient;
    @MockitoBean HallRealtimePublisher realtimePublisher;
    @MockitoBean JwtDecoder jwtDecoder;

    @Test
    void directorCreatesRestaurantScheduleHallAndPositionedTable() throws Exception {
        UUID directorUserId = UUID.randomUUID();
        when(identityClient.assignRestaurant(any(), any())).thenAnswer(invocation ->
                new DirectorProfile(invocation.getArgument(0), "Дина", "Директорова"));

        JsonNode restaurant = objectMapper.readTree(mockMvc.perform(post("/api/v1/restaurants")
                        .with(jwt().jwt(token -> token
                                .subject(directorUserId.toString())
                                .claim("role", "DIRECTOR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"Учебный ресторан",
                                  "address":"Москва, Учебная улица, 1",
                                  "openingHours":[
                                    {"dayOfWeek":"MONDAY","closed":false,"opensAt":"10:00","closesAt":"23:00"},
                                    {"dayOfWeek":"TUESDAY","closed":false,"opensAt":"10:00","closesAt":"23:00"},
                                    {"dayOfWeek":"WEDNESDAY","closed":false,"opensAt":"10:00","closesAt":"23:00"},
                                    {"dayOfWeek":"THURSDAY","closed":false,"opensAt":"10:00","closesAt":"23:00"},
                                    {"dayOfWeek":"FRIDAY","closed":false,"opensAt":"18:00","closesAt":"02:00"},
                                    {"dayOfWeek":"SATURDAY","closed":false,"opensAt":"12:00","closesAt":"02:00"},
                                    {"dayOfWeek":"SUNDAY","closed":true}
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Учебный ресторан"))
                .andExpect(jsonPath("$.openingHours.length()").value(7))
                .andExpect(jsonPath("$.openingHours[4].dayOfWeek").value("FRIDAY"))
                .andExpect(jsonPath("$.openingHours[4].closesNextDay").value(true))
                .andExpect(jsonPath("$.openingHours[6].closed").value(true))
                .andReturn().getResponse().getContentAsString());
        UUID restaurantId = UUID.fromString(restaurant.get("id").asString());
        verify(workforceClient).createDirector(
                restaurantId,
                directorUserId,
                "Дина",
                "Директорова");

        JsonNode hall = objectMapper.readTree(mockMvc.perform(post(
                                "/api/v1/restaurants/{restaurantId}/halls", restaurantId)
                        .with(jwt().jwt(token -> token
                                .subject(directorUserId.toString())
                                .claim("role", "DIRECTOR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Основной зал\",\"displayOrder\":0}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        UUID hallId = UUID.fromString(hall.get("id").asString());

        JsonNode table = objectMapper.readTree(mockMvc.perform(post(
                                "/api/v1/restaurants/{restaurantId}/halls/{hallId}/tables",
                                restaurantId,
                                hallId)
                        .with(jwt().jwt(token -> token
                                .subject(directorUserId.toString())
                                .claim("role", "DIRECTOR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "number":"1",
                                  "seatsCount":4,
                                  "positionX":120.5,
                                  "positionY":64,
                                  "shape":"ROUND"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shape").value("ROUND"))
                .andExpect(jsonPath("$.positionX").value(120.5))
                .andReturn().getResponse().getContentAsString());
        UUID tableId = UUID.fromString(table.get("id").asString());

        mockMvc.perform(patch("/api/v1/restaurants/{restaurantId}/tables/{tableId}", restaurantId, tableId)
                        .with(jwt().jwt(token -> token
                                .subject(directorUserId.toString())
                                .claim("role", "DIRECTOR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"positionX\":180,\"positionY\":90,\"shape\":\"SQUARE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.positionX").value(180))
                .andExpect(jsonPath("$.positionY").value(90))
                .andExpect(jsonPath("$.shape").value("SQUARE"));

        mockMvc.perform(get("/api/v1/restaurants/{restaurantId}/tables", restaurantId)
                        .with(jwt().jwt(token -> token
                                .subject(directorUserId.toString())
                                .claim("role", "DIRECTOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void waiterRequestsServingRunnerMakesReadyAndManagersReceiveStatusNotifications() throws Exception {
        UUID restaurantId = UUID.randomUUID();
        UUID hallId = UUID.randomUUID();
        UUID tableId = UUID.randomUUID();
        UUID shiftId = UUID.randomUUID();
        UUID waiterMembershipId = UUID.randomUUID();
        UUID waiterUserId = UUID.randomUUID();
        UUID runnerMembershipId = UUID.randomUUID();
        UUID runnerUserId = UUID.randomUUID();
        UUID managerMembershipId = UUID.randomUUID();
        UUID managerUserId = UUID.randomUUID();

        jdbc.sql("""
                        INSERT INTO restaurants (id, director_user_id, name, address)
                        VALUES (:restaurantId, :directorId, 'Тестовый ресторан', 'Москва')
                        """)
                .param("restaurantId", restaurantId)
                .param("directorId", UUID.randomUUID())
                .update();
        jdbc.sql("""
                        INSERT INTO halls (id, restaurant_id, name)
                        VALUES (:hallId, :restaurantId, 'Зал')
                        """)
                .param("hallId", hallId)
                .param("restaurantId", restaurantId)
                .update();
        jdbc.sql("""
                        INSERT INTO dining_tables (
                            id, restaurant_id, hall_id, number, seats_count,
                            position_x, position_y, shape
                        ) VALUES (
                            :tableId, :restaurantId, :hallId, '7', 4, 10, 20, 'ROUND'
                        )
                        """)
                .param("tableId", tableId)
                .param("restaurantId", restaurantId)
                .param("hallId", hallId)
                .update();

        MemberReference waiter = new MemberReference(
                waiterMembershipId, restaurantId, waiterUserId, "EMPLOYEE", "WAITER", "ACTIVE");
        MemberReference runner = new MemberReference(
                runnerMembershipId, restaurantId, runnerUserId, "EMPLOYEE", "RUNNER", "ACTIVE");
        MemberReference manager = new MemberReference(
                managerMembershipId, restaurantId, managerUserId, "MANAGER", null, "ACTIVE");
        when(workforceMemberClient.findByUser(restaurantId, waiterUserId)).thenReturn(waiter);
        when(workforceMemberClient.findByUser(restaurantId, runnerUserId)).thenReturn(runner);
        when(workforceMemberClient.findByUser(restaurantId, managerUserId)).thenReturn(manager);
        when(workforceMemberClient.findByMembership(restaurantId, runnerMembershipId)).thenReturn(runner);
        when(workforceMemberClient.findByMembership(restaurantId, managerMembershipId)).thenReturn(manager);
        when(operationsTableClient.activeAssignments(restaurantId, tableId))
                .thenReturn(new TableAssignments(
                        shiftId,
                        managerMembershipId,
                        waiterMembershipId,
                        runnerMembershipId));

        mockMvc.perform(post(
                                "/api/v1/restaurants/{restaurantId}/tables/{tableId}/status-changes",
                                restaurantId,
                                tableId)
                        .with(employeeJwt(waiterUserId, restaurantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"NEEDS_SERVING\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tableNumber").value("7"))
                .andExpect(jsonPath("$.status").value("NEEDS_SERVING"))
                .andExpect(jsonPath("$.shiftId").value(shiftId.toString()));

        verify(notificationClient).create(
                eq(managerUserId),
                eq(restaurantId),
                eq("TABLE_STATUS_CHANGED"),
                eq("Изменение статуса стола"),
                eq("Стол №7 изменил свой статус"),
                anyMap());
        verify(notificationClient).create(
                eq(runnerUserId),
                eq(restaurantId),
                eq("TABLE_NEEDS_SERVING"),
                eq("Стол №7"),
                eq("Столу №7 нужна сервировка"),
                anyMap());
        verify(realtimePublisher).publish(argThat(event ->
                restaurantId.equals(event.restaurantId())
                        && tableId.equals(event.tableId())
                        && shiftId.equals(event.shiftId())
                        && "READY".equals(event.oldStatus())
                        && "NEEDS_SERVING".equals(event.newStatus())));

        mockMvc.perform(post(
                                "/api/v1/restaurants/{restaurantId}/tables/{tableId}/status-changes",
                                restaurantId,
                                tableId)
                        .with(employeeJwt(runnerUserId, restaurantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"READY\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"));

        mockMvc.perform(post(
                                "/api/v1/restaurants/{restaurantId}/tables/{tableId}/status-changes",
                                restaurantId,
                                tableId)
                        .with(employeeJwt(waiterUserId, restaurantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PROBLEM\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get(
                                "/api/v1/restaurants/{restaurantId}/tables/{tableId}/status-history",
                                restaurantId,
                                tableId)
                        .param("shiftId", shiftId.toString())
                        .with(managerJwt(managerUserId, restaurantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].newStatus").value("READY"))
                .andExpect(jsonPath("$[1].newStatus").value("NEEDS_SERVING"));

        mockMvc.perform(get(
                                "/api/v1/restaurants/{restaurantId}/tables/status-history",
                                restaurantId)
                        .param("changedByMembershipId", waiterMembershipId.toString())
                        .with(managerJwt(managerUserId, restaurantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].changedByMembershipId").value(waiterMembershipId.toString()));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor employeeJwt(
            UUID userId,
            UUID restaurantId) {
        return jwt().jwt(token -> token
                .subject(userId.toString())
                .claim("role", "EMPLOYEE")
                .claim("restaurant_id", restaurantId.toString()));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor managerJwt(
            UUID userId,
            UUID restaurantId) {
        return jwt().jwt(token -> token
                .subject(userId.toString())
                .claim("role", "MANAGER")
                .claim("restaurant_id", restaurantId.toString()));
    }
}
