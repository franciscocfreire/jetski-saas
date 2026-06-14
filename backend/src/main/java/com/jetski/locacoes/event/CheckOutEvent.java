package com.jetski.locacoes.domain.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Domain Event: Check-Out Completed
 *
 * <p>Published when a rental (locacao) is finalized through check-out.
 * This event is consumed by:
 * <ul>
 *   <li>AuditEventListener - to record the audit trail</li>
 *   <li>DashboardMetricsService - to invalidate cache</li>
 *   <li>Future: Notifications, analytics, commission calculation, etc.</li>
 * </ul>
 *
 * <p><strong>Event-Driven Architecture:</strong><br>
 * Enables loose coupling between the locacoes module and consumers
 * that need to react to rental completion (e.g., audit logging,
 * cache invalidation, commission processing).
 *
 * @param tenantId        The tenant that owns this rental
 * @param locacaoId       The unique identifier of the completed rental
 * @param jetskiId        The jetski that was rented
 * @param clienteId       The customer ID (null for walk-in without customer)
 * @param operadorId      The user who performed the check-out
 * @param horimetroFim    The final odometer reading
 * @param minutosUsados   Total minutes used
 * @param valorTotal      Total amount charged
 * @param dataCheckOut    The timestamp when check-out occurred
 *
 * @author Jetski Team
 * @since 0.10.0
 */
public record CheckOutEvent(
    UUID tenantId,
    UUID locacaoId,
    UUID jetskiId,
    UUID clienteId,
    UUID operadorId,
    Integer horimetroFim,
    Integer minutosUsados,
    BigDecimal valorTotal,
    LocalDateTime dataCheckOut
) {

    /**
     * Factory method for standard check-out.
     */
    public static CheckOutEvent of(
            UUID tenantId, UUID locacaoId, UUID jetskiId,
            UUID clienteId, UUID operadorId, Integer horimetroFim,
            Integer minutosUsados, BigDecimal valorTotal, LocalDateTime dataCheckOut) {
        return new CheckOutEvent(
            tenantId, locacaoId, jetskiId, clienteId,
            operadorId, horimetroFim, minutosUsados, valorTotal, dataCheckOut
        );
    }
}
