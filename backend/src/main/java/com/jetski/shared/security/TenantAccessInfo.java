package com.jetski.shared.security;

import lombok.*;

import java.util.List;

/**
 * DTO: TenantAccessInfo
 *
 * Represents the result of tenant access validation.
 * Used by TenantAccessService and TenantFilter.
 *
 * Three possible outcomes:
 * 1. Access denied (hasAccess=false)
 * 2. Access allowed via Membro (hasAccess=true, unrestricted=false)
 * 3. Unrestricted access via global roles (hasAccess=true, unrestricted=true)
 *
 * @author Jetski Team
 * @since 0.2.0
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TenantAccessInfo {

    /**
     * Whether access is granted
     */
    private boolean hasAccess;

    /**
     * Roles available for this user in this tenant
     * Empty list if access is denied
     */
    private List<String> roles;

    /**
     * If true, user has platform-level unrestricted access
     * (can access ANY tenant without explicit membership)
     */
    private boolean unrestricted;

    /**
     * Human-readable reason for the access decision
     * Used for logging and debugging
     */
    private String reason;

    /**
     * Factory method: Access denied
     */
    public static TenantAccessInfo denied(String reason) {
        return TenantAccessInfo.builder()
            .hasAccess(false)
            .roles(List.of())
            .unrestricted(false)
            .reason(reason)
            .build();
    }

    /**
     * Factory method: Access granted via Membro table
     */
    public static TenantAccessInfo allowed(List<String> roles) {
        return TenantAccessInfo.builder()
            .hasAccess(true)
            .roles(roles)
            .unrestricted(false)
            .reason("Access granted via membro table")
            .build();
    }

    /**
     * Factory method: Unrestricted platform access
     */
    public static TenantAccessInfo unrestricted(List<String> globalRoles) {
        return TenantAccessInfo.builder()
            .hasAccess(true)
            .roles(globalRoles)
            .unrestricted(true)
            .reason("Unrestricted platform access")
            .build();
    }
}
