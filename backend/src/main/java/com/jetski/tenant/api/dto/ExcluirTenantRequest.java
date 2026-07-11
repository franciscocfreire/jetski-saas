package com.jetski.tenant.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * DTO: exclusão de empresa (super admin). CARENCIA = suspende agora e expurga
 * em D+30 (cancelável); IMEDIATO = expurga na hora (empresas de teste).
 * Confirmação pelo SLUG digitado, como no reset.
 */
public record ExcluirTenantRequest(
    @NotNull(message = "Modo é obrigatório (CARENCIA ou IMEDIATO)")
    Modo modo,

    @NotBlank(message = "Digite o slug da empresa para confirmar")
    String confirmacaoSlug
) {
    public enum Modo { CARENCIA, IMEDIATO }
}
