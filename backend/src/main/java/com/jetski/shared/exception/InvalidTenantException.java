package com.jetski.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when tenant ID is invalid, missing or mismatched
 *
 * This exception is thrown by TenantFilter when:
 * - Tenant ID is not provided in header or subdomain
 * - Tenant ID format is invalid (not a valid UUID)
 * - Tenant ID in header doesn't match JWT claim
 * - User tries to access resources from a different tenant
 *
 * @author Jetski Team
 * @since 0.1.0
 * @see com.jetski.security.TenantFilter
 */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class InvalidTenantException extends RuntimeException {

    /**
     * Construct with message
     *
     * @param message the error message
     */
    public InvalidTenantException(String message) {
        super(message);
    }

    /**
     * Construct with message and cause
     *
     * @param message the error message
     * @param cause the underlying cause
     */
    public InvalidTenantException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Create exception for missing tenant ID
     *
     * @return InvalidTenantException with appropriate message
     */
    public static InvalidTenantException missingTenantId() {
        return new InvalidTenantException(
            "Tenant ID not found in request. Please provide X-Tenant-Id header or use subdomain routing."
        );
    }

    /**
     * Create exception for invalid tenant ID format
     *
     * @param tenantId the invalid tenant ID
     * @return InvalidTenantException with appropriate message
     */
    public static InvalidTenantException invalidFormat(String tenantId) {
        return new InvalidTenantException(
            String.format("Invalid tenant ID format: '%s'. Must be a valid UUID.", tenantId)
        );
    }

    /**
     * Create exception for mismatched tenant IDs
     *
     * @param headerTenantId tenant ID from header/subdomain
     * @param jwtTenantId tenant ID from JWT claim
     * @return InvalidTenantException with appropriate message
     */
    public static InvalidTenantException mismatch(String headerTenantId, String jwtTenantId) {
        return new InvalidTenantException(
            String.format(
                "Tenant ID mismatch: header='%s', JWT='%s'. You cannot access resources from a different tenant.",
                headerTenantId,
                jwtTenantId
            )
        );
    }
}
