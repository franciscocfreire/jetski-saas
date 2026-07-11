package com.jetski.tenant.api.dto;

import com.jetski.tenant.internal.TenantResetService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * DTO: reset de empresa (super admin). A confirmação exige o SLUG digitado —
 * proteção contra clique acidental numa operação destrutiva.
 */
public record ResetTenantRequest(
    @NotNull(message = "Nível é obrigatório (OPERACIONAL, FROTA ou TOTAL)")
    TenantResetService.Nivel nivel,

    @NotBlank(message = "Digite o slug da empresa para confirmar")
    String confirmacaoSlug
) {}
