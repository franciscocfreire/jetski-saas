package com.jetski.locacoes.domain.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Domain Event: Rental Completed (Check-out)
 *
 * <p>Published when a rental (locacao) is finalized through check-out.
 * This event is consumed by:
 * <ul>
 *   <li>DashboardMetricsService - to invalidate cached revenue metrics</li>
 *   <li>Future: Commission calculation, notifications, etc.</li>
 * </ul>
 *
 * <p><strong>Event-Driven Architecture:</strong><br>
 * Enables loose coupling between the locacoes module and consumers
 * that need to react to rental completion (e.g., cache invalidation,
 * metrics updates, notifications).
 *
 * @param tenantId      The tenant that owns this rental
 * @param locacaoId     The unique identifier of the completed rental
 * @param valorTotal    The total value charged for the rental
 * @param dataCheckOut  The timestamp when check-out occurred
 *
 * @author Jetski Team
 * @since 0.9.0
 */
public record RentalCompletedEvent(
    UUID tenantId,
    UUID locacaoId,
    BigDecimal valorTotal,
    LocalDateTime dataCheckOut
) {
    /**
     * Factory method to create event from rental data.
     */
    public static RentalCompletedEvent of(UUID tenantId, UUID locacaoId,
                                          BigDecimal valorTotal, LocalDateTime dataCheckOut) {
        return new RentalCompletedEvent(tenantId, locacaoId, valorTotal, dataCheckOut);
    }
}
