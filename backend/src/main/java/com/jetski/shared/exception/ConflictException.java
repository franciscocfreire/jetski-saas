package com.jetski.shared.exception;

/**
 * Exception for resource conflict scenarios (HTTP 409).
 *
 * Use when an operation cannot be completed due to a conflict
 * with the current state of a resource.
 *
 * Examples:
 * - Duplicate email invitation
 * - Resource already exists
 * - Concurrent modification conflict
 *
 * @author Jetski Team
 * @since 0.4.0
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }

    public ConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
