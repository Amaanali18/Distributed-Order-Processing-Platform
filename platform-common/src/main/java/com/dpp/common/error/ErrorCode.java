package com.dpp.common.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Request validation failed"),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "Request could not be parsed"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Authentication is required"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Access to this resource is denied"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found"),
    CONFLICT(HttpStatus.CONFLICT, "Request conflicts with the current state"),
    IDEMPOTENCY_KEY_REUSE(HttpStatus.CONFLICT, "Idempotency key was reused with a different payload"),
    INSUFFICIENT_STOCK(HttpStatus.CONFLICT, "Requested quantity is not available"),
    INVALID_STATE_TRANSITION(HttpStatus.CONFLICT, "State transition is not allowed"),
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "An upstream dependency is unavailable"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
