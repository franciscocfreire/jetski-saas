package com.jetski.tenant.api.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Empresa aguardando aprovação de um super admin (status PENDENTE_APROVACAO).
 */
public record PendingTenantDTO(
    UUID tenantId,
    String slug,
    String razaoSocial,
    String cnpj,
    Instant createdAt
) {
}
