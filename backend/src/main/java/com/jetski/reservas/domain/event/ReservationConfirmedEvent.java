package com.jetski.reservas.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain Event: Reservation Confirmed
 *
 * <p>Published when a reservation transitions from PENDENTE to CONFIRMADA.
 * This event is consumed by:
 * <ul>
 *   <li>AuditEventListener - to record the audit trail</li>
 *   <li>Future: Notifications to customer, analytics, etc.</li>
 * </ul>
 *
 * @param tenantId      The tenant that owns this reservation
 * @param reservaId     The unique identifier of the confirmed reservation
 * @param clienteId     The customer of the reservation
 * @param confirmedBy   The user who confirmed the reservation
 * @param occurredAt    When this event occurred
 *
 * @author Jetski Team
 * @since 0.10.0
 */
public record ReservationConfirmedEvent(
    UUID tenantId,
    UUID reservaId,
    UUID clienteId,
    UUID confirmedBy,
    Instant occurredAt
) {

    /**
     * Factory method for creating a reservation confirmed event.
     */
    public static ReservationConfirmedEvent of(
            UUID tenantId,
            UUID reservaId,
            UUID clienteId,
            UUID confirmedBy) {
        return new ReservationConfirmedEvent(
            tenantId, reservaId, clienteId, confirmedBy, Instant.now()
        );
    }
}
