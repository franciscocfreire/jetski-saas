package com.jetski.reservas.domain.event;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Domain Event: Reservation Created
 *
 * <p>Published when a new reservation is created.
 * This event is consumed by:
 * <ul>
 *   <li>AuditEventListener - to record the audit trail</li>
 *   <li>Future: Notifications, analytics, etc.</li>
 * </ul>
 *
 * @param tenantId         The tenant that owns this reservation
 * @param reservaId        The unique identifier of the created reservation
 * @param modeloId         The modelo being reserved
 * @param clienteId        The customer making the reservation
 * @param vendedorId       The seller who created the reservation (optional)
 * @param operadorId       The user who created the reservation
 * @param dataInicio       The start date/time of the reservation
 * @param dataFimPrevista  The expected end date/time
 * @param sinalPago        Whether deposit was paid (ALTA priority)
 * @param occurredAt       When this event occurred
 *
 * @author Jetski Team
 * @since 0.10.0
 */
public record ReservationCreatedEvent(
    UUID tenantId,
    UUID reservaId,
    UUID modeloId,
    UUID clienteId,
    UUID vendedorId,
    UUID operadorId,
    LocalDateTime dataInicio,
    LocalDateTime dataFimPrevista,
    Boolean sinalPago,
    Instant occurredAt
) {

    /**
     * Factory method for creating a reservation created event.
     */
    public static ReservationCreatedEvent of(
            UUID tenantId,
            UUID reservaId,
            UUID modeloId,
            UUID clienteId,
            UUID vendedorId,
            UUID operadorId,
            LocalDateTime dataInicio,
            LocalDateTime dataFimPrevista,
            Boolean sinalPago) {
        return new ReservationCreatedEvent(
            tenantId, reservaId, modeloId, clienteId, vendedorId,
            operadorId, dataInicio, dataFimPrevista, sinalPago, Instant.now()
        );
    }
}
