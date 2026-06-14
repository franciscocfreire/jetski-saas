package com.jetski.locacoes.domain.event;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Domain Event: Check-In Started
 *
 * <p>Published when a rental (locacao) is initiated through check-in.
 * This event is consumed by:
 * <ul>
 *   <li>AuditEventListener - to record the audit trail</li>
 *   <li>Future: Notifications, analytics, etc.</li>
 * </ul>
 *
 * <p><strong>Event-Driven Architecture:</strong><br>
 * Enables loose coupling between the locacoes module and consumers
 * that need to react to rental start (e.g., audit logging,
 * notifications, metrics).
 *
 * @param tenantId        The tenant that owns this rental
 * @param locacaoId       The unique identifier of the started rental
 * @param jetskiId        The jetski being rented
 * @param reservaId       The reservation ID (null for walk-in)
 * @param clienteId       The customer ID (null for walk-in without customer)
 * @param operadorId      The user who performed the check-in
 * @param horimetroInicio The initial odometer reading
 * @param dataCheckIn     The timestamp when check-in occurred
 * @param tipo            The type of check-in (RESERVA or WALK_IN)
 *
 * @author Jetski Team
 * @since 0.10.0
 */
public record CheckInEvent(
    UUID tenantId,
    UUID locacaoId,
    UUID jetskiId,
    UUID reservaId,
    UUID clienteId,
    UUID operadorId,
    Integer horimetroInicio,
    LocalDateTime dataCheckIn,
    CheckInTipo tipo
) {

    public enum CheckInTipo {
        RESERVA,
        WALK_IN
    }

    /**
     * Factory method for check-in from reservation.
     */
    public static CheckInEvent fromReservation(
            UUID tenantId, UUID locacaoId, UUID jetskiId, UUID reservaId,
            UUID clienteId, UUID operadorId, Integer horimetroInicio, LocalDateTime dataCheckIn) {
        return new CheckInEvent(
            tenantId, locacaoId, jetskiId, reservaId, clienteId,
            operadorId, horimetroInicio, dataCheckIn, CheckInTipo.RESERVA
        );
    }

    /**
     * Factory method for walk-in check-in.
     */
    public static CheckInEvent walkIn(
            UUID tenantId, UUID locacaoId, UUID jetskiId,
            UUID clienteId, UUID operadorId, Integer horimetroInicio, LocalDateTime dataCheckIn) {
        return new CheckInEvent(
            tenantId, locacaoId, jetskiId, null, clienteId,
            operadorId, horimetroInicio, dataCheckIn, CheckInTipo.WALK_IN
        );
    }
}
