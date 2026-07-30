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
 * @param providerUserId   sub do Keycloak vinculado ao cliente
 * @param contaReutilizada true = conta EXISTENTE (consumidor de outra loja,
 *                         Google ou staff) acumulou o papel de cliente —
 *                         identidade única (F2); false = conta criada no claim
 */
public record ContaAtivadaEvent(
    UUID tenantId,
    UUID clienteId,
    String providerUserId,
    boolean contaReutilizada,
    Instant occurredAt
) {
    public static ContaAtivadaEvent of(
            UUID tenantId, UUID clienteId, String providerUserId, boolean contaReutilizada) {
        return new ContaAtivadaEvent(tenantId, clienteId, providerUserId, contaReutilizada, Instant.now());
    }
}
