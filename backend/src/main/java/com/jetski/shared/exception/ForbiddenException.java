package com.jetski.shared.exception;

/**
 * Exception for forbidden operations (HTTP 403).
 *
 * Use when the client is authenticated but not authorized to perform
 * the requested operation due to business rules or resource limits.
 *
 * Examples:
 * - Plan limit reached
 * - Insufficient permissions
 * - Resource quota exceeded
 *
 * @author Jetski Team
 * @since 0.4.0
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }

    public ForbiddenException(String message, Throwable cause) {
        super(message, cause);
    }
}
