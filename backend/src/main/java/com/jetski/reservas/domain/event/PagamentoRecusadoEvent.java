package com.jetski.reservas.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain Event: Pagamento da reserva recusado pelo staff (com motivo).
 *
 * <p>Mantém a reserva PENDENTE/BAIXA; o cliente é notificado para reenviar.
 * Consumido por {@code AuditEventListener}.
 */
public record PagamentoRecusadoEvent(
    UUID tenantId,
    UUID reservaId,
    String motivo,
    UUID recusadoPor,
    Instant occurredAt
) {
    public static PagamentoRecusadoEvent of(
            UUID tenantId, UUID reservaId, String motivo, UUID recusadoPor) {
        return new PagamentoRecusadoEvent(tenantId, reservaId, motivo, recusadoPor, Instant.now());
    }
}
