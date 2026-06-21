package com.jetski.tenant.api.dto;

import java.util.UUID;

/**
 * Resultado de uma mudança de status de tenant (aprovar/suspender/reativar).
 */
public record TenantStatusResult(
    UUID tenantId,
    String status,
    String message
) {
}
