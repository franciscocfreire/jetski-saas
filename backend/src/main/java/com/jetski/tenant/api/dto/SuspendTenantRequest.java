package com.jetski.tenant.api.dto;

/**
 * Corpo (opcional) para suspensão de tenant — motivo registrado na auditoria.
 */
public record SuspendTenantRequest(String motivo) {
}
