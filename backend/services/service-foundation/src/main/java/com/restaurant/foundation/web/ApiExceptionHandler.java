package com.restaurant.foundation.web;

import java.net.URI;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.convert.ConversionFailedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

@RestControllerAdvice
final class ApiExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> handleApiException(
            ApiException exception,
            HttpServletRequest request) {
        return ResponseEntity.status(exception.status()).body(problem(
                exception.status(),
                exception.code(),
                exception.getMessage(),
                request));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {
        List<Violation> violations = exception.getBindingResult().getFieldErrors().stream()
                .map(ApiExceptionHandler::toViolation)
                .toList();
        ProblemDetail problem = problem(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_FAILED",
                "Запрос содержит некорректные поля.",
                request);
        problem.setProperty("violations", violations);
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ProblemDetail> handleConstraintViolation(
            ConstraintViolationException exception,
            HttpServletRequest request) {
        List<Violation> violations = exception.getConstraintViolations().stream()
                .map(violation -> new Violation(
                        violation.getPropertyPath().toString(),
                        violation.getMessage()))
                .toList();
        ProblemDetail problem = problem(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_FAILED",
                "Параметры запроса не прошли проверку.",
                request);
        problem.setProperty("violations", violations);
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, ConversionFailedException.class})
    ResponseEntity<ProblemDetail> handleMalformedRequest(Exception exception, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(problem(
                HttpStatus.BAD_REQUEST,
                "MALFORMED_REQUEST",
                "Не удалось прочитать запрос.",
                request));
    }

    @ExceptionHandler(ErrorResponseException.class)
    ResponseEntity<ProblemDetail> handleFrameworkError(
            ErrorResponseException exception,
            HttpServletRequest request) {
        HttpStatus status = HttpStatus.resolve(exception.getStatusCode().value());
        HttpStatus resolvedStatus = status == null ? HttpStatus.INTERNAL_SERVER_ERROR : status;
        String detail = exception.getBody().getDetail();
        ProblemDetail problem = problem(
                resolvedStatus,
                "HTTP_" + exception.getStatusCode().value(),
                detail == null ? resolvedStatus.getReasonPhrase() : detail,
                request);
        return ResponseEntity.status(exception.getStatusCode()).body(problem);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception exception, HttpServletRequest request) {
        LOGGER.error("Unhandled request error", exception);
        return ResponseEntity.internalServerError().body(problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "Внутренняя ошибка сервера.",
                request));
    }

    private static ProblemDetail problem(
            HttpStatus status,
            String code,
            String detail,
            HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("urn:restaurant:error:" + code.toLowerCase().replace('_', '-')));
        problem.setTitle(status.getReasonPhrase());
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code);
        problem.setProperty("requestId", requestId(request));
        return problem;
    }

    private static String requestId(HttpServletRequest request) {
        Object requestId = request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME);
        return requestId == null ? "unknown" : requestId.toString();
    }

    private static Violation toViolation(FieldError error) {
        return new Violation(error.getField(), error.getDefaultMessage());
    }

    record Violation(String field, String message) {
    }
}
