package com.jetski.shared.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;

/**
 * Standardized error response for REST API.
 *
 * Provides consistent error format across the application:
 * - timestamp: When the error occurred
 * - status: HTTP status code
 * - error: HTTP status reason phrase
 * - message: Human-readable error message
 * - path: Request path that caused the error
 * - details: Additional error details (optional)
 *
 * @author Jetski Team
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    /**
     * Timestamp when the error occurred (ISO-8601 format)
     */
    @Builder.Default
    private Instant timestamp = Instant.now();

    /**
     * HTTP status code (e.g., 400, 403, 404, 500)
     */
    private int status;

    /**
     * HTTP status reason phrase (e.g., "Bad Request", "Forbidden")
     */
    private String error;

    /**
     * Human-readable error message explaining what went wrong
     */
    private String message;

    /**
     * Request path that caused the error
     */
    private String path;

    /**
     * Additional error details (optional)
     * Can include validation errors, field-specific messages, etc.
     */
    private Map<String, Object> details;

    /**
     * Validation errors (optional)
     * Maps field names to error messages for validation failures
     */
    private Map<String, String> errors;
}
