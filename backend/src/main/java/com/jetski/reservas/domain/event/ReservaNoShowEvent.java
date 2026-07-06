package com.jetski.reservas.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain Event: reserva marcada como NO_SHOW (cliente não compareceu).
 *
 * <p>Ação manual do staff — difere da expiração automática, que só alcança
 * reservas sem sinal. Consumido por {@code AuditEventListener}.
 *
 * @param tenantId   tenant da reserva
 * @param reservaId  reserva marcada
 * @param marcadoPor usuário (staff) que marcou
 * @param occurredAt quando ocorreu
 */
public record ReservaNoShowEvent(
    UUID tenantId,
    UUID reservaId,
    UUID marcadoPor,
    Instant occurredAt
) {
    public static ReservaNoShowEvent of(UUID tenantId, UUID reservaId, UUID marcadoPor) {
        return new ReservaNoShowEvent(tenantId, reservaId, marcadoPor, Instant.now());
    }
}
