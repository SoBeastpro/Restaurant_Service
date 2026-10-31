package com.restaurant.foundation.web;

import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Scope;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestClient;

import com.restaurant.foundation.security.InternalApiAuthorizer;

@AutoConfiguration
public class RestClientAutoConfiguration {

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    @ConditionalOnMissingBean
    RestClient.Builder restClientBuilder(@Value("${internal.api-secret:}") String internalApiSecret) {
        RestClient.Builder builder = RestClient.builder();
        if (internalApiSecret != null && !internalApiSecret.isBlank()) {
            builder.requestInterceptor((request, body, execution) -> {
                if (request.getURI().getPath().startsWith("/internal/v1/")) {
                    request.getHeaders().set(InternalApiAuthorizer.HEADER, internalApiSecret);
                }
                return execution.execute(request, body);
            });
        }
        return builder;
    }
}
