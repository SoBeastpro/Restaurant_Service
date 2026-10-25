package com.restaurant.foundation.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class RequestIdFilterTests {

    @Test
    void preservesSafeRequestId() {
        assertThat(RequestIdFilter.normalize("mobile.ios:request-42"))
                .isEqualTo("mobile.ios:request-42");
    }

    @Test
    void replacesUnsafeRequestId() {
        String requestId = RequestIdFilter.normalize("unsafe\nvalue");
        assertThatCode(() -> UUID.fromString(requestId)).doesNotThrowAnyException();
    }

}
