package com.cloudcart.disputes.infra.config;

import com.cloudcart.disputes.api.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        List<ErrorResponse.Violation> violations = ex.getBindingResult()
            .getFieldErrors()
            .stream()
            .map(fe -> new ErrorResponse.Violation(fe.getField(), fe.getDefaultMessage()))
            .toList();

        return ResponseEntity.badRequest().body(
            ErrorResponse.withViolations(400, "BAD_REQUEST", "VALIDATION_ERROR",
                "Request validation failed. See violations for details.", violations)
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex) {
        String message = "Malformed JSON or invalid field value: " + sanitize(ex.getMessage());
        return ResponseEntity.badRequest().body(
            ErrorResponse.of(400, "BAD_REQUEST", "INVALID_REQUEST_BODY", message)
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String message = String.format("Invalid value '%s' for parameter '%s'", ex.getValue(), ex.getName());
        return ResponseEntity.badRequest().body(
            ErrorResponse.of(400, "BAD_REQUEST", "INVALID_PARAMETER", message)
        );
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException ex) {
        return ResponseEntity.badRequest().body(
            ErrorResponse.of(400, "BAD_REQUEST", "MISSING_PARAMETER",
                "Required parameter '" + ex.getParameterName() + "' is missing")
        );
    }

    @ExceptionHandler(DisputeNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(DisputeNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            ErrorResponse.of(404, "NOT_FOUND", ex.getCode(), ex.getMessage())
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            ErrorResponse.of(500, "INTERNAL_SERVER_ERROR", "INTERNAL_ERROR",
                "An unexpected error occurred. Please try again later.")
        );
    }

    /** Truncates and cleans exception messages before including them in responses. */
    private String sanitize(String message) {
        if (message == null) return "unknown";
        int max = 200;
        String clean = message.replaceAll("[\\r\\n]", " ");
        return clean.length() > max ? clean.substring(0, max) + "..." : clean;
    }
}
