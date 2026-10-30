package com.restaurant.foundation.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import com.restaurant.foundation.web.ApiException;

import org.springframework.http.HttpStatus;

public final class InternalApiAuthorizer {

    public static final String HEADER = "X-Internal-Api-Key";

    private InternalApiAuthorizer() {
    }

    public static void require(String provided, String expected) {
        byte[] actualBytes = provided == null ? new byte[0] : provided.getBytes(StandardCharsets.UTF_8);
        byte[] expectedBytes = expected == null ? new byte[0] : expected.getBytes(StandardCharsets.UTF_8);
        if (expectedBytes.length < 16 || !MessageDigest.isEqual(actualBytes, expectedBytes)) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    "INTERNAL_API_KEY_INVALID",
                    "Некорректный ключ внутреннего API.");
        }
    }
}
