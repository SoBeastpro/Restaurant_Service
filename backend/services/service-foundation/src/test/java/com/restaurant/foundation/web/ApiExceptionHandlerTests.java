package com.restaurant.foundation.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import jakarta.validation.ConstraintViolationException;

class ApiExceptionHandlerTests {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void returnsUnifiedValidationProblemWithRequestId() {
        MockHttpServletRequest request = request("request-42");

        ResponseEntity<ProblemDetail> response = handler.handleConstraintViolation(
                new ConstraintViolationException(Set.of()),
                request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getProperties())
                .containsEntry("code", "VALIDATION_FAILED")
                .containsEntry("requestId", "request-42")
                .containsKey("violations");
    }

    @Test
    void hidesUnexpectedExceptionDetails() {
        ResponseEntity<ProblemDetail> response = handler.handleUnexpected(
                new IllegalStateException("database password must not leak"),
                request("request-43"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getDetail()).isEqualTo("Внутренняя ошибка сервера.");
        assertThat(response.getBody().getDetail()).doesNotContain("password");
    }

    @Test
    void returnsSafeApplicationErrorCode() {
        ResponseEntity<ProblemDetail> response = handler.handleApiException(
                new ApiException(HttpStatus.CONFLICT, "ACCOUNT_ALREADY_EXISTS", "Аккаунт уже существует."),
                request("request-44"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getDetail()).isEqualTo("Аккаунт уже существует.");
        assertThat(response.getBody().getProperties())
                .containsEntry("code", "ACCOUNT_ALREADY_EXISTS")
                .containsEntry("requestId", "request-44");
    }

    private static MockHttpServletRequest request(String requestId) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/test");
        request.setAttribute(RequestIdFilter.ATTRIBUTE_NAME, requestId);
        return request;
    }
}
