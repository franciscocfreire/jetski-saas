package com.jetski.tenant.api.dto;

import java.util.List;
import java.util.UUID;

/**
 * Resumo de uma empresa para o painel de plataforma (super admin).
 *
 * <p>Mesma forma de TenantSummary (id, slug, razaoSocial, status, roles) para que o
 * frontend reutilize o tipo no switcher. {@code roles} vem como ADMIN_TENANT (god mode):
 * o super admin atua como admin em qualquer empresa que selecionar.
 */
public record PlatformTenantSummary(
    String id,
    String slug,
    String razaoSocial,
    String status,
    List<String> roles
) {
    public static PlatformTenantSummary of(UUID id, String slug, String razaoSocial, String status) {
        return new PlatformTenantSummary(
            id.toString(), slug, razaoSocial, status, List.of("ADMIN_TENANT"));
    }
}
