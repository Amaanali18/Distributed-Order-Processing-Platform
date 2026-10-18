package com.dpp.common.error;

import com.dpp.common.web.CorrelationId;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApiException(ApiException ex, HttpServletRequest request) {
        ApiError body = ApiError.of(ex.errorCode(), ex.getMessage(), request.getRequestURI(),
                CorrelationId.current(), ex.violations());
        return ResponseEntity.status(ex.errorCode().status()).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                 HttpServletRequest request) {
        List<FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> new FieldViolation(fieldError.getField(), fieldError.getDefaultMessage()))
                .toList();
        ApiError body = ApiError.of(ErrorCode.VALIDATION_ERROR, null, request.getRequestURI(),
                CorrelationId.current(), violations);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex,
                                                              HttpServletRequest request) {
        List<FieldViolation> violations = ex.getConstraintViolations().stream()
                .map(violation -> new FieldViolation(violation.getPropertyPath().toString(), violation.getMessage()))
                .toList();
        ApiError body = ApiError.of(ErrorCode.VALIDATION_ERROR, null, request.getRequestURI(),
                CorrelationId.current(), violations);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableMessage(HttpMessageNotReadableException ex,
                                                            HttpServletRequest request) {
        ApiError body = ApiError.of(ErrorCode.MALFORMED_REQUEST, null, request.getRequestURI(),
                CorrelationId.current(), List.of());
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception for {} {}", request.getMethod(), request.getRequestURI(), ex);
        ApiError body = ApiError.of(ErrorCode.INTERNAL_ERROR, null, request.getRequestURI(),
                CorrelationId.current(), List.of());
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.status()).body(body);
    }
}
