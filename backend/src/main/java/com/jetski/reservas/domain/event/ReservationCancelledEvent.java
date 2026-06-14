package com.jetski.reservas.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain Event: Reservation Cancelled
 *
 * <p>Published when a reservation is cancelled.
 * This event is consumed by:
 * <ul>
 *   <li>AuditEventListener - to record the audit trail</li>
 *   <li>Future: Notifications, refund processing, analytics, etc.</li>
 * </ul>
 *
 * @param tenantId      The tenant that owns this reservation
 * @param reservaId     The unique identifier of the cancelled reservation
 * @param clienteId     The customer of the reservation
 * @param cancelledBy   The user who cancelled the reservation
 * @param reason        The reason for cancellation (optional)
 * @param occurredAt    When this event occurred
 *
 * @author Jetski Team
 * @since 0.10.0
 */
public record ReservationCancelledEvent(
    UUID tenantId,
    UUID reservaId,
    UUID clienteId,
    UUID cancelledBy,
    String reason,
    Instant occurredAt
) {

    /**
     * Factory method for creating a reservation cancelled event.
     */
    public static ReservationCancelledEvent of(
            UUID tenantId,
            UUID reservaId,
            UUID clienteId,
            UUID cancelledBy,
            String reason) {
        return new ReservationCancelledEvent(
            tenantId, reservaId, clienteId, cancelledBy, reason, Instant.now()
        );
    }

    /**
     * Factory method for cancellation without reason.
     */
    public static ReservationCancelledEvent of(
            UUID tenantId,
            UUID reservaId,
            UUID clienteId,
            UUID cancelledBy) {
        return of(tenantId, reservaId, clienteId, cancelledBy, null);
    }
}
