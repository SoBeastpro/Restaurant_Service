package com.restaurant.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.core.io.ResourceLoader;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest
class ApiGatewayApplicationTests {

    @Autowired
    private RouteDefinitionLocator routeDefinitionLocator;

    @Autowired
    private ResourceLoader resourceLoader;

    @Test
    void loadsAllPublicRoutes() {
        List<RouteDefinition> routes = routeDefinitionLocator.getRouteDefinitions().collectList().block();
        assertThat(routes)
                .isNotNull()
                .extracting(RouteDefinition::getId)
                .containsExactlyInAnyOrder(
                        "restaurant-realtime",
                        "identity-auth",
                        "identity-onboarding",
                        "notification-self",
                        "workforce-self-restaurants",
                        "operations-self-shift",
                        "identity-profile",
                        "workforce-invitations-public",
                        "reporting",
                        "audit",
                        "billing",
                        "operations",
                        "workforce",
                        "restaurant-resources",
                        "platform-restaurants",
                        "platform-users");
    }

    @Test
    void packagesPublicOpenApiContractForSwaggerUi() {
        assertThat(resourceLoader.getResource("classpath:static/openapi/openapi.yaml").exists())
                .isTrue();
    }
}
