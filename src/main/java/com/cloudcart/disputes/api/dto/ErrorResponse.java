package com.cloudcart.disputes.api.dto;

import java.time.Instant;
import java.util.List;

public record ErrorResponse(
    int status,
    String error,
    String code,
    String message,
    Instant timestamp,
    List<Violation> violations
) {
    public record Violation(String field, String message) {}

    public static ErrorResponse of(int status, String error, String code, String message) {
        return new ErrorResponse(status, error, code, message, Instant.now(), null);
    }

    public static ErrorResponse withViolations(int status, String error, String code,
                                               String message, List<Violation> violations) {
        return new ErrorResponse(status, error, code, message, Instant.now(), violations);
    }
}
