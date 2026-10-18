package com.dpp.common.error;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ApiErrorTest {

    @Test
    void usesDefaultMessageWhenMessageMissing() {
        ApiError error = ApiError.of(ErrorCode.NOT_FOUND, null, "/orders/1", "corr-1", List.of());

        assertThat(error.status()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(error.code()).isEqualTo("NOT_FOUND");
        assertThat(error.message()).isEqualTo(ErrorCode.NOT_FOUND.defaultMessage());
        assertThat(error.path()).isEqualTo("/orders/1");
        assertThat(error.correlationId()).isEqualTo("corr-1");
        assertThat(error.violations()).isEmpty();
        assertThat(error.timestamp()).isNotNull();
    }

    @Test
    void keepsCustomMessageAndViolations() {
        ApiError error = ApiError.of(ErrorCode.VALIDATION_ERROR, "bad input", "/orders", "corr-2",
                List.of(new FieldViolation("quantity", "must be positive")));

        assertThat(error.message()).isEqualTo("bad input");
        assertThat(error.violations()).containsExactly(new FieldViolation("quantity", "must be positive"));
        assertThat(error.status()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    }

    @Test
    void apiExceptionCarriesErrorCode() {
        ApiException ex = ApiException.conflict(ErrorCode.INSUFFICIENT_STOCK, "only 2 left");

        assertThat(ex.errorCode()).isEqualTo(ErrorCode.INSUFFICIENT_STOCK);
        assertThat(ex.getMessage()).isEqualTo("only 2 left");
        assertThat(ex.violations()).isEmpty();
    }
}
