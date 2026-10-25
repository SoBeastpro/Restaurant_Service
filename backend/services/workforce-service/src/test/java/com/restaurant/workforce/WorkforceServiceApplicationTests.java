package com.restaurant.workforce;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.restaurant.workforce.invitation.api.InvitationDtos.TokenPair;
import com.restaurant.workforce.invitation.infrastructure.IdentityClient;
import com.restaurant.workforce.invitation.infrastructure.IdentityClient.InvitedRegistrationResult;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
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
class WorkforceServiceApplicationTests {
    @Container @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired MockMvc mockMvc;
    @Autowired JdbcClient jdbc;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean IdentityClient identityClient;

    @Test void contextLoads() {
    }

    @Test
    void restaurantOnboardingCreatesDirectorMembershipIdempotently() throws Exception {
        UUID restaurantId = UUID.randomUUID();
        UUID directorUserId = UUID.randomUUID();
        String body = """
                {
                  "userId":"%s",
                  "firstName":"Дина",
                  "lastName":"Директорова"
                }
                """.formatted(directorUserId);

        mockMvc.perform(post("/internal/v1/restaurants/{restaurantId}/director", restaurantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/internal/v1/restaurants/{restaurantId}/director", restaurantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        long count = jdbc.sql("""
                        SELECT count(*) FROM restaurant_members
                        WHERE restaurant_id = :restaurantId
                          AND user_id = :userId
                          AND role_code = 'DIRECTOR'
                          AND status = 'ACTIVE'
                        """)
                .param("restaurantId", restaurantId)
                .param("userId", directorUserId)
                .query(Long.class)
                .single();
        org.assertj.core.api.Assertions.assertThat(count).isEqualTo(1);
    }

    @Test
    void directorCreatesAndEmployeeAcceptsOneHourInvitation() throws Exception {
        UUID restaurantId = UUID.randomUUID();
        UUID directorUserId = UUID.randomUUID();
        insertDirector(restaurantId, directorUserId);
        UUID employeeUserId = UUID.randomUUID();
        when(identityClient.register(any())).thenReturn(new InvitedRegistrationResult(
                employeeUserId,
                new TokenPair("access-token", "refresh-token", "Bearer", 600)));

        JsonNode created = objectMapper.readTree(mockMvc.perform(post(
                                "/api/v1/restaurants/{restaurantId}/invitations", restaurantId)
                        .with(jwt().jwt(token -> token
                                .subject(directorUserId.toString())
                                .claim("role", "DIRECTOR")
                                .claim("restaurant_id", restaurantId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName":"Анна",
                                  "lastName":"Соколова",
                                  "role":"EMPLOYEE",
                                  "position":"WAITER"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("EMPLOYEE"))
                .andExpect(jsonPath("$.position").value("WAITER"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString());

        String originalToken = created.get("qrContent").asString().replace("rest://invite/", "");
        Duration lifetime = Duration.between(Instant.now(), Instant.parse(created.get("expiresAt").asString()));
        org.assertj.core.api.Assertions.assertThat(lifetime)
                .isGreaterThan(Duration.ofMinutes(59))
                .isLessThanOrEqualTo(Duration.ofHours(1));
        UUID invitationId = UUID.fromString(created.get("id").asString());
        JsonNode reissued = objectMapper.readTree(mockMvc.perform(post(
                                "/api/v1/restaurants/{restaurantId}/invitations/{invitationId}/resend",
                                restaurantId,
                                invitationId)
                        .with(jwt().jwt(token -> token
                                .subject(directorUserId.toString())
                                .claim("role", "DIRECTOR")
                                .claim("restaurant_id", restaurantId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString());
        String token = reissued.get("qrContent").asString().replace("rest://invite/", "");
        org.assertj.core.api.Assertions.assertThat(token).isNotEqualTo(originalToken);

        mockMvc.perform(get("/api/v1/invitations/{token}", originalToken))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("INVITATION_REVOKED"));

        mockMvc.perform(get("/api/v1/invitations/{token}", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Анна"))
                .andExpect(jsonPath("$.role").value("EMPLOYEE"));

        mockMvc.perform(post("/api/v1/invitations/{token}/accept", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login":"anna.waiter","password":"correct-password"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"));

        mockMvc.perform(post("/api/v1/invitations/{token}/accept", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login":"anna.waiter","password":"correct-password"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVITATION_ALREADY_USED"));

        long activeMemberships = jdbc.sql("""
                        SELECT count(*) FROM restaurant_members
                        WHERE restaurant_id = :restaurantId
                          AND user_id = :userId
                          AND status = 'ACTIVE'
                        """)
                .param("restaurantId", restaurantId)
                .param("userId", employeeUserId)
                .query(Long.class)
                .single();
        org.assertj.core.api.Assertions.assertThat(activeMemberships).isEqualTo(1);

        JsonNode revoked = objectMapper.readTree(mockMvc.perform(post(
                                "/api/v1/restaurants/{restaurantId}/invitations", restaurantId)
                        .with(jwt().jwt(tokenBuilder -> tokenBuilder
                                .subject(directorUserId.toString())
                                .claim("role", "DIRECTOR")
                                .claim("restaurant_id", restaurantId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName":"Михаил",
                                  "lastName":"Менеджеров",
                                  "role":"MANAGER"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        UUID revokedId = UUID.fromString(revoked.get("id").asString());
        String revokedToken = revoked.get("qrContent").asString().replace("rest://invite/", "");

        mockMvc.perform(delete(
                                "/api/v1/restaurants/{restaurantId}/invitations/{invitationId}",
                                restaurantId,
                                revokedId)
                        .with(jwt().jwt(tokenBuilder -> tokenBuilder
                                .subject(directorUserId.toString())
                                .claim("role", "DIRECTOR")
                                .claim("restaurant_id", restaurantId.toString()))))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/invitations/{token}", revokedToken))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("INVITATION_REVOKED"));
    }

    @Test
    void managerCanManageAnyEmployeeInvitationButCannotManageManagerInvitations() throws Exception {
        UUID restaurantId = UUID.randomUUID();
        UUID directorUserId = UUID.randomUUID();
        UUID managerUserId = UUID.randomUUID();
        insertDirector(restaurantId, directorUserId);
        insertManager(restaurantId, managerUserId);

        JsonNode employeeInvitation = objectMapper.readTree(mockMvc.perform(post(
                                "/api/v1/restaurants/{restaurantId}/invitations", restaurantId)
                        .with(jwt().jwt(token -> token
                                .subject(directorUserId.toString())
                                .claim("role", "DIRECTOR")
                                .claim("restaurant_id", restaurantId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName":"Роман",
                                  "lastName":"Раннеров",
                                  "role":"EMPLOYEE",
                                  "position":"RUNNER"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());

        UUID employeeInvitationId = UUID.fromString(employeeInvitation.get("id").asString());
        mockMvc.perform(post(
                                "/api/v1/restaurants/{restaurantId}/invitations/{invitationId}/resend",
                                restaurantId,
                                employeeInvitationId)
                        .with(jwt().jwt(token -> token
                                .subject(managerUserId.toString())
                                .claim("role", "MANAGER")
                                .claim("restaurant_id", restaurantId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("EMPLOYEE"));

        JsonNode managerInvitation = objectMapper.readTree(mockMvc.perform(post(
                                "/api/v1/restaurants/{restaurantId}/invitations", restaurantId)
                        .with(jwt().jwt(token -> token
                                .subject(directorUserId.toString())
                                .claim("role", "DIRECTOR")
                                .claim("restaurant_id", restaurantId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName":"Мария",
                                  "lastName":"Менеджерова",
                                  "role":"MANAGER"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());

        mockMvc.perform(get("/api/v1/restaurants/{restaurantId}/invitations", restaurantId)
                        .with(jwt().jwt(token -> token
                                .subject(managerUserId.toString())
                                .claim("role", "MANAGER")
                                .claim("restaurant_id", restaurantId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].role").value(org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.is("EMPLOYEE"))));

        mockMvc.perform(delete(
                                "/api/v1/restaurants/{restaurantId}/invitations/{invitationId}",
                                restaurantId,
                                UUID.fromString(managerInvitation.get("id").asString()))
                        .with(jwt().jwt(token -> token
                                .subject(managerUserId.toString())
                                .claim("role", "MANAGER")
                                .claim("restaurant_id", restaurantId.toString()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MANAGER_CAN_INVITE_EMPLOYEES_ONLY"));

        mockMvc.perform(post("/api/v1/restaurants/{restaurantId}/invitations", restaurantId)
                        .with(jwt().jwt(token -> token
                                .subject(managerUserId.toString())
                                .claim("role", "MANAGER")
                                .claim("restaurant_id", restaurantId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName":"Мария",
                                  "lastName":"Менеджерова",
                                  "role":"MANAGER"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MANAGER_CAN_INVITE_EMPLOYEES_ONLY"));
    }

    private void insertDirector(UUID restaurantId, UUID directorUserId) {
        jdbc.sql("""
                        INSERT INTO restaurant_members (
                            restaurant_id, user_id, role_code, first_name, last_name, status
                        ) VALUES (
                            :restaurantId, :userId, 'DIRECTOR', 'Дина', 'Директорова', 'ACTIVE'
                        )
                        """)
                .param("restaurantId", restaurantId)
                .param("userId", directorUserId)
                .update();
    }

    private void insertManager(UUID restaurantId, UUID managerUserId) {
        jdbc.sql("""
                        INSERT INTO restaurant_members (
                            restaurant_id, user_id, role_code, first_name, last_name, status
                        ) VALUES (
                            :restaurantId, :userId, 'MANAGER', 'Максим', 'Менеджеров', 'ACTIVE'
                        )
                        """)
                .param("restaurantId", restaurantId)
                .param("userId", managerUserId)
                .update();
    }
}
