package com.jetski.tenant.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request do import (restauração) de export de arquivamento.
 *
 * @param key                          chave do zip em {@code _platform/exports/<tenantId>/}
 * @param confirmacaoSlug              slug ATUAL da empresa, digitado (confirmação forte)
 * @param ignorarTabelasDesconhecidas  prossegue mesmo se o zip tiver tabelas que não
 *                                     existem mais no sistema (esses dados são pulados)
 */
public record ImportTenantRequest(
    @NotBlank String key,
    @NotBlank String confirmacaoSlug,
    boolean ignorarTabelasDesconhecidas
) {}
