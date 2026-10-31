package com.restaurant.foundation.security;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@AutoConfiguration
@ConditionalOnProperty(name = "internal.api-secret")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class InternalApiSecurityAutoConfiguration {

    @Bean
    FilterRegistrationBean<OncePerRequestFilter> internalApiKeyFilter(
            @Value("${internal.api-secret}") String expectedSecret) {
        OncePerRequestFilter filter = new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(
                    HttpServletRequest request,
                    HttpServletResponse response,
                    FilterChain chain) throws ServletException, IOException {
                if (!request.getRequestURI().startsWith("/internal/v1/")) {
                    chain.doFilter(request, response);
                    return;
                }
                try {
                    InternalApiAuthorizer.require(
                            request.getHeader(InternalApiAuthorizer.HEADER),
                            expectedSecret);
                } catch (RuntimeException exception) {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                    response.setCharacterEncoding("UTF-8");
                    response.getWriter().write("""
                            {"status":401,"code":"INTERNAL_API_KEY_INVALID","message":"Некорректный ключ внутреннего API."}
                            """);
                    return;
                }
                chain.doFilter(request, response);
            }
        };
        FilterRegistrationBean<OncePerRequestFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        registration.addUrlPatterns("/internal/v1/*");
        return registration;
    }
}
