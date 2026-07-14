package com.jetski.reservas.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain Event: Documentos consolidados (PDF) emitidos para uma reserva.
 *
 * <p>Publicado pelo fluxo de emissão (atendimento de balcão) após gerar o PDF,
 * arquivar e disparar os envios. Consumido por {@code AuditEventListener}.
 *
 * @param destinos resumo dos destinos do envio (ex.: "marinha,cliente")
 */
public record DocumentosEmitidosEvent(
    UUID tenantId,
    UUID reservaId,
    UUID documentoId,
    String destinos,
    UUID emitidoPor,
    UUID emissorTenantId,
    Instant occurredAt
) {
    public static DocumentosEmitidosEvent of(
            UUID tenantId, UUID reservaId, UUID documentoId, String destinos, UUID emitidoPor) {
        return of(tenantId, reservaId, documentoId, destinos, emitidoPor, null);
    }

    /** Emissão delegada (V048): informa a EAMA em nome de quem o documento saiu. */
    public static DocumentosEmitidosEvent of(
            UUID tenantId, UUID reservaId, UUID documentoId, String destinos, UUID emitidoPor,
            UUID emissorTenantId) {
        return new DocumentosEmitidosEvent(
            tenantId, reservaId, documentoId, destinos, emitidoPor, emissorTenantId, Instant.now());
    }
}
