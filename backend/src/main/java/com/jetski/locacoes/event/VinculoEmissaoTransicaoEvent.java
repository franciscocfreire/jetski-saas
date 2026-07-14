package com.jetski.locacoes.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain Event: transição no ciclo de vida de uma parceria de emissão
 * delegada (V048). Consumido pelo {@code AuditEventListener}, que grava a
 * trilha NOS DOIS tenants (operadora e emissora) — é a prova de "quando a
 * EAMA bloqueou/aceitou" exigida pela spec (§4.3).
 *
 * @param transicao CONVIDADO | ATIVADO | BLOQUEADO | LIBERADO | REVOGADO
 */
public record VinculoEmissaoTransicaoEvent(
    UUID vinculoId,
    UUID tenantOperadorId,
    UUID tenantEmissorId,
    String transicao,
    UUID actor,
    Instant occurredAt
) {
    public static VinculoEmissaoTransicaoEvent of(
            UUID vinculoId, UUID tenantOperadorId, UUID tenantEmissorId,
            String transicao, UUID actor) {
        return new VinculoEmissaoTransicaoEvent(
            vinculoId, tenantOperadorId, tenantEmissorId, transicao, actor, Instant.now());
    }
}
