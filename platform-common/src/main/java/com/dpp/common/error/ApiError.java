package com.dpp.common.error;

import java.time.Instant;
import java.util.List;

public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        String correlationId,
        List<FieldViolation> violations) {

    public static ApiError of(ErrorCode code,
                              String message,
                              String path,
                              String correlationId,
                              List<FieldViolation> violations) {
        return new ApiError(
                Instant.now(),
                code.status().value(),
                code.status().getReasonPhrase(),
                code.name(),
                message != null && !message.isBlank() ? message : code.defaultMessage(),
                path,
                correlationId,
                violations == null ? List.of() : List.copyOf(violations));
    }
}
