package com.restaurant.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
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
class IdentityServiceApplicationTests {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    JwtDecoder jwtDecoder;

    @Test
    void contextLoads() {
    }

    @Test
    void registrationLoginProfileRefreshAndLogoutFormACompleteFlow() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String login = "flow." + suffix;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "login": "%s",
                                  "firstName": " Анна ",
                                  "lastName": " Тестова ",
                                  "password": "correct-password",
                                  "platformRoles": ["SYSTEM_ADMIN"]
                                }
                                """.formatted(login)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.login").value(login))
                .andExpect(jsonPath("$.firstName").value("Анна"))
                .andExpect(jsonPath("$.role").value("DIRECTOR"))
                .andExpect(jsonPath("$.restaurantId").doesNotExist())
                .andExpect(jsonPath("$.platformRoles").isEmpty());

        String storedHash = jdbc.sql("SELECT password_hash FROM users WHERE login = :login")
                .param("login", login)
                .query(String.class)
                .single();
        assertThat(storedHash).startsWith("{bcrypt}").doesNotContain("correct-password");

        JsonNode loginResponse = body(mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("User-Agent", "identity-integration-test")
                        .content("""
                                {"login":"%s","password":"correct-password"}
                                """.formatted(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(600))
                .andReturn().getResponse().getContentAsString());

        String accessToken = loginResponse.get("accessToken").asString();
        String refreshToken = loginResponse.get("refreshToken").asString();
        var jwt = jwtDecoder.decode(accessToken);
        assertThat(jwt.getClaimAsString("role")).isEqualTo("DIRECTOR");
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("DIRECTOR");
        assertThat(jwt.hasClaim("restaurant_id")).isFalse();

        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.login").value(login))
                .andExpect(jsonPath("$.role").value("DIRECTOR"));

        mockMvc.perform(get("/api/v1/auth/sessions").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].current").value(true))
                .andExpect(jsonPath("$[0].userAgent").value("identity-integration-test"));

        JsonNode refreshResponse = body(mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"%s\"}".formatted(refreshToken)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        String rotatedRefreshToken = refreshResponse.get("refreshToken").asString();
        assertThat(rotatedRefreshToken).isNotEqualTo(refreshToken);

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + refreshResponse.get("accessToken").asString()))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"%s\"}".formatted(rotatedRefreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void duplicateRegistrationAndBadCredentialsUseSafeErrors() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String login = "duplicate." + suffix;
        String registration = """
                {
                  "login":"%s",
                  "firstName":"Иван",
                  "lastName":"Тестов",
                  "password":"correct-password"
                }
                """.formatted(login);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_ALREADY_EXISTS"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"%s\",\"password\":\"wrong-password\"}".formatted(login)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"missing.%s\",\"password\":\"wrong-password\"}".formatted(suffix)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void invitedEmployeeGetsRestaurantRolePositionAndSession() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String login = "waiter." + suffix;
        UUID restaurantId = UUID.randomUUID();

        JsonNode response = body(mockMvc.perform(post("/internal/v1/users/invitations")
                        .header("X-Internal-Api-Key", "local-internal-api-key-change-me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "login":"%s",
                                  "password":"correct-password",
                                  "firstName":"Анна",
                                  "lastName":"Соколова",
                                  "role":"EMPLOYEE",
                                  "position":"WAITER",
                                  "restaurantId":"%s"
                                }
                                """.formatted(login, restaurantId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").isNotEmpty())
                .andExpect(jsonPath("$.tokens.accessToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString());

        var jwt = jwtDecoder.decode(response.get("tokens").get("accessToken").asString());
        assertThat(jwt.getClaimAsString("role")).isEqualTo("EMPLOYEE");
        assertThat(jwt.getClaimAsString("position")).isEqualTo("WAITER");
        assertThat(jwt.getClaimAsString("restaurant_id")).isEqualTo(restaurantId.toString());

        mockMvc.perform(post("/internal/v1/users/invitations")
                        .header("X-Internal-Api-Key", "local-internal-api-key-change-me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "login":"%s",
                                  "password":"correct-password",
                                  "firstName":"Анна",
                                  "lastName":"Соколова",
                                  "role":"EMPLOYEE",
                                  "position":"WAITER",
                                  "restaurantId":"%s"
                                }
                                """.formatted(login, restaurantId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(response.get("userId").asString()))
                .andExpect(jsonPath("$.tokens.accessToken").isNotEmpty());

        mockMvc.perform(post("/internal/v1/users/invitations")
                        .header("X-Internal-Api-Key", "local-internal-api-key-change-me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "login":"%s",
                                  "password":"wrong-password",
                                  "firstName":"Анна",
                                  "lastName":"Соколова",
                                  "role":"EMPLOYEE",
                                  "position":"WAITER",
                                  "restaurantId":"%s"
                                }
                                """.formatted(login, restaurantId)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void protectedEndpointsRejectMissingToken() throws Exception {
        mockMvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void systemAdminListsAndBlocksPlatformUser() throws Exception {
        UUID adminId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO users (
                            id, login, password_hash, first_name, last_name, role_code, status
                        ) VALUES (
                            :id, :login, '{bcrypt}unused', 'Иван', 'Сотрудник', 'DIRECTOR', 'ACTIVE'
                        )
                        """)
                .param("id", targetId)
                .param("login", "platform-user-" + targetId)
                .update();

        mockMvc.perform(get("/api/v1/platform/users")
                        .param("query", "Иван Сотрудник")
                        .with(systemAdmin(adminId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(targetId.toString()));

        mockMvc.perform(patch("/api/v1/platform/users/{userId}/status", targetId)
                        .with(systemAdmin(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"blocked\",\"reason\":\"проверка\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BLOCKED"));

        assertThat(jdbc.sql("SELECT COUNT(*) FROM outbox_events WHERE aggregate_id = :id")
                .param("id", targetId)
                .query(Long.class)
                .single()).isEqualTo(1);

        mockMvc.perform(get("/internal/v1/platform/metrics-summary"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INTERNAL_API_KEY_INVALID"));
        mockMvc.perform(get("/internal/v1/platform/metrics-summary")
                        .header("X-Internal-Api-Key", "local-internal-api-key-change-me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blocked").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    @Test
    void systemAdminCannotBlockOwnAccount() throws Exception {
        UUID adminId = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO users (
                            id, login, password_hash, first_name, last_name, role_code, status
                        ) VALUES (
                            :id, :login, '{bcrypt}unused', 'Системный', 'Администратор', 'SYSTEM_ADMIN', 'ACTIVE'
                        )
                        """)
                .param("id", adminId)
                .param("login", "platform-admin-" + adminId)
                .update();

        mockMvc.perform(patch("/api/v1/platform/users/{userId}/status", adminId)
                        .with(systemAdmin(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"BLOCKED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SYSTEM_ADMIN_SELF_BLOCK_FORBIDDEN"));
    }

    @Test
    void directorActivatesThirtyDayLocalSubscriptionStubIdempotently() throws Exception {
        String login = "subscription." + UUID.randomUUID().toString().substring(0, 8);
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "login":"%s",
                                  "firstName":"Дина",
                                  "lastName":"Директорова",
                                  "password":"correct-password"
                                }
                                """.formatted(login)))
                .andExpect(status().isCreated());
        JsonNode loginResponse = body(mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"%s\",\"password\":\"correct-password\"}".formatted(login)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        String authorization = "Bearer " + loginResponse.get("accessToken").asString();
        UUID userId = UUID.fromString(jwtDecoder.decode(loginResponse.get("accessToken").asString()).getSubject());
        UUID restaurantId = UUID.randomUUID();

        mockMvc.perform(post("/internal/v1/users/{userId}/restaurant", userId)
                        .header("X-Internal-Api-Key", "local-internal-api-key-change-me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"restaurantId\":\"%s\"}".formatted(restaurantId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACTIVE_SUBSCRIPTION_REQUIRED"));

        mockMvc.perform(get("/api/v1/onboarding/subscription")
                        .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NOT_STARTED"))
                .andExpect(jsonPath("$.expiresAt").doesNotExist())
                .andExpect(jsonPath("$.stub").value(true));

        JsonNode firstActivation = body(mockMvc.perform(post("/api/v1/onboarding/subscription/activate")
                        .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.stub").value(true))
                .andReturn().getResponse().getContentAsString());
        Instant expiresAt = Instant.parse(firstActivation.get("expiresAt").asString());
        assertThat(Duration.between(Instant.now(), expiresAt))
                .isGreaterThan(Duration.ofDays(29).plus(Duration.ofHours(23)))
                .isLessThanOrEqualTo(Duration.ofDays(30));

        mockMvc.perform(post("/api/v1/onboarding/subscription/activate")
                        .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresAt").value(firstActivation.get("expiresAt").asString()));

        mockMvc.perform(post("/internal/v1/users/{userId}/restaurant", userId)
                        .header("X-Internal-Api-Key", "local-internal-api-key-change-me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"restaurantId\":\"%s\"}".formatted(restaurantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.firstName").value("Дина"));

        JsonNode refreshed = body(mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"%s\"}".formatted(loginResponse.get("refreshToken").asString())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(jwtDecoder.decode(refreshed.get("accessToken").asString())
                .getClaimAsString("restaurant_id")).isEqualTo(restaurantId.toString());
    }

    private JsonNode body(String json) {
        return objectMapper.readTree(json);
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor systemAdmin(UUID userId) {
        return jwt().jwt(token -> token
                .subject(userId.toString())
                .claim("platform_roles", List.of("SYSTEM_ADMIN")));
    }
}
