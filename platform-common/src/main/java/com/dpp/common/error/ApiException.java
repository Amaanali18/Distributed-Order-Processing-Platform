package com.dpp.common.error;

import java.util.List;

public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;
    private final transient List<FieldViolation> violations;

    public ApiException(ErrorCode errorCode, String message) {
        this(errorCode, message, List.of());
    }

    public ApiException(ErrorCode errorCode, String message, List<FieldViolation> violations) {
        super(message);
        this.errorCode = errorCode;
        this.violations = violations == null ? List.of() : List.copyOf(violations);
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public List<FieldViolation> violations() {
        return violations;
    }

    public static ApiException notFound(String message) {
        return new ApiException(ErrorCode.NOT_FOUND, message);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(ErrorCode.FORBIDDEN, message);
    }

    public static ApiException conflict(ErrorCode code, String message) {
        return new ApiException(code, message);
    }
}
