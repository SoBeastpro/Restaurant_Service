package com.restaurant.foundation.web;

import org.springframework.http.HttpStatus;

/**
 * A safe, client-facing API error. The message may be returned to the caller;
 * internal exception details must never be placed here.
 */
public final class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}
