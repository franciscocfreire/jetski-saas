package com.jetski.locacoes.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain Event: Conta do cliente ativada (claim validado).
 *
 * <p>Publicado quando o cliente valida o claim-token: usuário criado no
 * Keycloak (role CLIENTE, sem Membro) e vinculado via cliente_identity_provider.
 * Consumido por {@code AuditEventListener}.
 *
 * @param providerUserId sub do Keycloak vinculado ao cliente
 */
public record ContaAtivadaEvent(
    UUID tenantId,
    UUID clienteId,
    String providerUserId,
    Instant occurredAt
) {
    public static ContaAtivadaEvent of(
            UUID tenantId, UUID clienteId, String providerUserId) {
        return new ContaAtivadaEvent(tenantId, clienteId, providerUserId, Instant.now());
    }
}
