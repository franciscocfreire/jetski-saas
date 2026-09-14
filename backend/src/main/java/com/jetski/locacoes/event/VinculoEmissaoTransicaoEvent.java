package com.jetski.locacoes.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain Event: transição no ciclo de vida de uma parceria de emissão
 * delegada (V048). Consumido pelo {@code AuditEventListener}, que grava a
 * trilha NOS DOIS tenants (operadora e emissora) — é a prova de "quando a
 * EAMA bloqueou/aceitou" exigida pela spec (§4.3).
 *
 * @param transicao CONVIDADO | ATIVADO | BLOQUEADO | LIBERADO | REVOGADO |
 *                  INSTRUTOR_SOLICITADO | INSTRUTOR_APROVADO | INSTRUTOR_REJEITADO |
 *                  INSTRUTOR_REMOVIDO (V070)
 * @param instrutorId instrutor da operadora envolvido (V070); null nas transições do vínculo
 */
public record VinculoEmissaoTransicaoEvent(
    UUID vinculoId,
    UUID tenantOperadorId,
    UUID tenantEmissorId,
    String transicao,
    UUID actor,
    Instant occurredAt,
    UUID instrutorId
) {
    public static VinculoEmissaoTransicaoEvent of(
            UUID vinculoId, UUID tenantOperadorId, UUID tenantEmissorId,
            String transicao, UUID actor) {
        return of(vinculoId, tenantOperadorId, tenantEmissorId, transicao, actor, null);
    }

    public static VinculoEmissaoTransicaoEvent of(
            UUID vinculoId, UUID tenantOperadorId, UUID tenantEmissorId,
            String transicao, UUID actor, UUID instrutorId) {
        return new VinculoEmissaoTransicaoEvent(
            vinculoId, tenantOperadorId, tenantEmissorId, transicao, actor, Instant.now(), instrutorId);
    }
}
