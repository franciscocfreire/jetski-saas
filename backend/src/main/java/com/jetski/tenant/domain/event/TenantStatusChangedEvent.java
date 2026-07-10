package com.jetski.tenant.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain Event: mudança de status de tenant pelo super admin (aprovar/suspender/reativar).
 *
 * <p>Consumido pelo AuditEventListener (trilha de auditoria) e pelos listeners de
 * notificação por e-mail à empresa (usuarios/signup) — por isso carrega razão social
 * e slug: o consumidor não precisa (nem pode) ler o repositório do módulo tenant.
 *
 * @param tenantId    tenant afetado
 * @param acao        rótulo da ação (TENANT_APPROVED / TENANT_SUSPENDED / TENANT_REACTIVATED)
 * @param fromStatus  status anterior
 * @param toStatus    status novo
 * @param actor       usuário (super admin) que executou a ação
 * @param motivo      motivo opcional (ex.: suspensão)
 * @param razaoSocial razão social da empresa no momento da mudança
 * @param slug        identificador (slug) da empresa
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
    String razaoSocial,
    String slug,
    Instant occurredAt
) {
    public static TenantStatusChangedEvent of(
            UUID tenantId, String acao, String fromStatus, String toStatus, UUID actor, String motivo,
            String razaoSocial, String slug) {
        return new TenantStatusChangedEvent(
            tenantId, acao, fromStatus, toStatus, actor, motivo, razaoSocial, slug, Instant.now());
    }
}
