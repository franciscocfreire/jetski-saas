package com.jetski.locacoes.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain Event: CHA-MTA-E temporária vigente REUSADA numa nova reserva
 * (triagem do portal) — a habilitação nasce resolvida via CHA derivada,
 * sem nova GRU/emissão. Consumido por {@code AuditEventListener}.
 *
 * @param tenantId      loja da NOVA reserva (onde o reuso aconteceu)
 * @param reservaId     nova reserva
 * @param tenantOrigem  loja que emitiu a temporária
 * @param reservaOrigem reserva da emissão original
 * @param gruNumero     nº da GRU de origem (referência na Marinha)
 * @param occurredAt    quando ocorreu
 */
public record HabilitacaoTemporariaReusadaEvent(
    UUID tenantId,
    UUID reservaId,
    UUID tenantOrigem,
    UUID reservaOrigem,
    String gruNumero,
    Instant occurredAt
) {
    public static HabilitacaoTemporariaReusadaEvent of(
            UUID tenantId, UUID reservaId, UUID tenantOrigem, UUID reservaOrigem, String gruNumero) {
        return new HabilitacaoTemporariaReusadaEvent(
            tenantId, reservaId, tenantOrigem, reservaOrigem, gruNumero, Instant.now());
    }
}
