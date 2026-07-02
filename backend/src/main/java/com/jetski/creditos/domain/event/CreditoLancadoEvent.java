package com.jetski.creditos.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain Event: crédito lançado no ledger (ADESAO/AJUSTE/ESTORNO — consumos não
 * publicam; a linha do ledger + auditoria da emissão já cobrem).
 *
 * <p>Consumido pelo módulo audit para a trilha "quem lançou, quanto, por quê".
 * A fonte primária anti-fraude é a própria linha do ledger (append-only).
 */
public record CreditoLancadoEvent(
    UUID tenantId,
    String tipo,
    int quantidade,
    int saldoApos,
    String motivo,
    UUID actor,
    Instant occurredAt
) {
    public static CreditoLancadoEvent of(UUID tenantId, String tipo, int quantidade,
                                         int saldoApos, String motivo, UUID actor) {
        return new CreditoLancadoEvent(tenantId, tipo, quantidade, saldoApos, motivo, actor, Instant.now());
    }
}
