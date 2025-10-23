package com.jetski.shared.exception;

/**
 * Exception for resources that are no longer available (HTTP 410).
 *
 * Use when a resource existed but is now permanently unavailable.
 *
 * Examples:
 * - Expired invitation tokens
 * - Cancelled invitations
 * - Archived or deleted resources
 *
 * @author Jetski Team
 * @since 0.4.0
 */
public class GoneException extends RuntimeException {

    public GoneException(String message) {
        super(message);
    }

    public GoneException(String message, Throwable cause) {
        super(message, cause);
    }
}
