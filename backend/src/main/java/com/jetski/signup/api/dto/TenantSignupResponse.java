package com.jetski.signup.api.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for tenant signup operations.
 *
 * @param tenantId UUID of the created tenant
 * @param slug URL-friendly identifier
 * @param razaoSocial Legal business name
 * @param adminEmail Email of the admin user (null for authenticated user creating tenant)
 * @param message User-friendly message about next steps
 * @param trialExpiresAt When the trial period ends
 * @param requiresActivation Whether the user needs to activate via email
 */
public record TenantSignupResponse(
    UUID tenantId,
    String slug,
    String razaoSocial,
    String adminEmail,
    String message,
    Instant trialExpiresAt,
    boolean requiresActivation
) {
    /**
     * Factory method for new user signup (requires email activation).
     */
    public static TenantSignupResponse forNewUser(
        UUID tenantId,
        String slug,
        String razaoSocial,
        String adminEmail,
        Instant trialExpiresAt
    ) {
        return new TenantSignupResponse(
            tenantId,
            slug,
            razaoSocial,
            adminEmail,
            "Verifique seu email para ativar a conta",
            trialExpiresAt,
            true
        );
    }

    /**
     * Factory method for existing user creating new tenant (no activation needed).
     */
    public static TenantSignupResponse forExistingUser(
        UUID tenantId,
        String slug,
        String razaoSocial,
        Instant trialExpiresAt
    ) {
        return new TenantSignupResponse(
            tenantId,
            slug,
            razaoSocial,
            null,
            "Empresa criada com sucesso! Você já pode acessar.",
            trialExpiresAt,
            false
        );
    }
}
