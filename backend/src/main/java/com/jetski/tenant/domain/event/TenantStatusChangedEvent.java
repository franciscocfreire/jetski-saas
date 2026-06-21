package com.jetski.tenant.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain Event: mudança de status de tenant pelo super admin (aprovar/suspender/reativar).
 *
 * <p>Consumido pelo AuditEventListener para registrar a trilha de auditoria.
 *
 * @param tenantId    tenant afetado
 * @param acao        rótulo da ação (TENANT_APPROVED / TENANT_SUSPENDED / TENANT_REACTIVATED)
 * @param fromStatus  status anterior
 * @param toStatus    status novo
 * @param actor       usuário (super admin) que executou a ação
 * @param motivo      motivo opcional (ex.: suspensão)
 * @param occurredAt  quando ocorreu
 *
 * @author Jetski Team
 */
public record TenantStatusChangedEvent(
    UUID tenantId,
    String acao,
    String fromStatus,
    String toStatus,
    UUID actor,
    String motivo,
    Instant occurredAt
) {
    public static TenantStatusChangedEvent of(
            UUID tenantId, String acao, String fromStatus, String toStatus, UUID actor, String motivo) {
        return new TenantStatusChangedEvent(tenantId, acao, fromStatus, toStatus, actor, motivo, Instant.now());
    }
}
