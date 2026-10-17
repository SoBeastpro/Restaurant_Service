package com.restaurant.foundation.web;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Order(Ordered.HIGHEST_PRECEDENCE)
final class RequestIdFilter extends OncePerRequestFilter {

    static final String HEADER_NAME = "X-Request-Id";
    static final String ATTRIBUTE_NAME = RequestIdFilter.class.getName() + ".requestId";
    private static final int MAX_REQUEST_ID_LENGTH = 128;
    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9._:-]+");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String requestId = normalize(request.getHeader(HEADER_NAME));
        request.setAttribute(ATTRIBUTE_NAME, requestId);
        response.setHeader(HEADER_NAME, requestId);

        try (MDC.MDCCloseable ignored = MDC.putCloseable("requestId", requestId)) {
            filterChain.doFilter(request, response);
        }
    }

    static String normalize(String candidate) {
        if (candidate == null
                || candidate.isBlank()
                || candidate.length() > MAX_REQUEST_ID_LENGTH
                || !SAFE_REQUEST_ID.matcher(candidate).matches()) {
            return UUID.randomUUID().toString();
        }
        return candidate;
    }
}
