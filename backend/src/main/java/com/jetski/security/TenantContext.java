package com.jetski.security;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.UUID;

/**
 * Thread-local storage for current tenant ID
 *
 * This class provides thread-safe access to the current tenant's ID
 * for the duration of an HTTP request.
 *
 * IMPORTANT: Always call clear() in a finally block to prevent
 * memory leaks in thread pools.
 *
 * Usage:
 * <pre>
 * try {
 *     TenantContext.setTenantId(tenantId);
 *     // ... process request
 * } finally {
 *     TenantContext.clear();
 * }
 * </pre>
 *
 * @author Jetski Team
 * @since 0.1.0
 * @see TenantFilter
 */
@Slf4j
public class TenantContext {

    private static final ThreadLocal<UUID> TENANT_ID = new ThreadLocal<>();
    private static final ThreadLocal<List<String>> USER_ROLES = new ThreadLocal<>();

    /**
     * Private constructor to prevent instantiation
     */
    private TenantContext() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Set the tenant ID for the current thread
     *
     * @param tenantId the tenant UUID
     * @throws IllegalArgumentException if tenantId is null
     */
    public static void setTenantId(UUID tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("Tenant ID cannot be null");
        }
        log.debug("Setting tenant ID: {}", tenantId);
        TENANT_ID.set(tenantId);
    }

    /**
     * Get the tenant ID for the current thread
     *
     * @return the tenant UUID, or null if not set
     */
    public static UUID getTenantId() {
        UUID tenantId = TENANT_ID.get();
        if (tenantId == null) {
            log.warn("Tenant ID requested but not set in context");
        }
        return tenantId;
    }

    /**
     * Get the tenant ID as String
     *
     * @return the tenant UUID as string, or null if not set
     */
    public static String getTenantIdAsString() {
        UUID tenantId = getTenantId();
        return tenantId != null ? tenantId.toString() : null;
    }

    /**
     * Set the user roles for the current thread
     *
     * @param roles the list of roles for this user in this tenant
     */
    public static void setUserRoles(List<String> roles) {
        log.debug("Setting user roles: {}", roles);
        USER_ROLES.set(roles);
    }

    /**
     * Get the user roles for the current thread
     *
     * @return the list of roles, or empty list if not set
     */
    public static List<String> getUserRoles() {
        List<String> roles = USER_ROLES.get();
        return roles != null ? roles : List.of();
    }

    /**
     * Clear the tenant ID and roles from the current thread
     *
     * MUST be called in a finally block to prevent memory leaks
     */
    public static void clear() {
        UUID tenantId = TENANT_ID.get();
        if (tenantId != null) {
            log.debug("Clearing tenant ID: {}", tenantId);
        }
        TENANT_ID.remove();
        USER_ROLES.remove();  // Clear roles as well
    }

    /**
     * Check if tenant ID is set
     *
     * @return true if tenant ID is set, false otherwise
     */
    public static boolean isSet() {
        return TENANT_ID.get() != null;
    }
}
