package com.jetski.tenant.domain;

/**
 * Enum: TenantStatus
 *
 * Possible status values for a tenant organization.
 *
 * @author Jetski Team
 * @since 0.1.0
 */
public enum TenantStatus {
    /**
     * Trial tenant - testing period
     */
    TRIAL,

    /**
     * Active tenant - fully operational
     */
    ATIVO,

    /**
     * Suspended tenant - temporarily disabled
     * Reasons: payment issues, policy violations, maintenance
     */
    SUSPENSO,

    /**
     * Canceled tenant - subscription ended
     */
    CANCELADO,

    /**
     * Inactive tenant - permanently disabled
     * Account closed or migrated
     */
    INATIVO
}
