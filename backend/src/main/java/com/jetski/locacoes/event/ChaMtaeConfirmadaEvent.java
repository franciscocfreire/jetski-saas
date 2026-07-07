package com.jetski.locacoes.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain Event: devolutiva da Marinha anexada pelo staff — a CHA-MTA-E
 * temporária desta reserva está oficialmente CONFIRMADA (elegível para
 * reuso em novas reservas dentro dos 30 dias). Consumido por
 * {@code AuditEventListener}.
 *
 * @param tenantId    loja da emissão
 * @param reservaId   reserva de origem da temporária
 * @param usuarioId   staff que anexou a devolutiva
 * @param substituicao true quando re-upload (PDF substituído; confirmação original preservada)
 * @param occurredAt  quando ocorreu
 */
public record ChaMtaeConfirmadaEvent(
    UUID tenantId,
    UUID reservaId,
    UUID usuarioId,
    boolean substituicao,
    Instant occurredAt
) {
    public static ChaMtaeConfirmadaEvent of(
            UUID tenantId, UUID reservaId, UUID usuarioId, boolean substituicao) {
        return new ChaMtaeConfirmadaEvent(tenantId, reservaId, usuarioId, substituicao, Instant.now());
    }
}
