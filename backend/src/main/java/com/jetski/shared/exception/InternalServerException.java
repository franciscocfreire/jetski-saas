package com.jetski.shared.exception;

/**
 * Exception for internal server errors (infrastructure failures).
 *
 * Examples:
 * - External service failures (Keycloak, payment gateway, etc.)
 * - Database connection issues
 * - File system errors
 *
 * HTTP Status: 500 Internal Server Error
 *
 * @author Jetski Team
 * @since 0.4.0
 */
public class InternalServerException extends RuntimeException {
    public InternalServerException(String message) {
        super(message);
    }

    public InternalServerException(String message, Throwable cause) {
        super(message, cause);
    }
}
