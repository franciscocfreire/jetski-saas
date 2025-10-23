package com.jetski.shared.exception;

/**
 * Business Exception
 *
 * Thrown when business rules are violated.
 * Results in HTTP 400 Bad Request or 422 Unprocessable Entity.
 *
 * @author Jetski Team
 * @since 0.3.0
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}
