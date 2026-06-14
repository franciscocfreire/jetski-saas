package com.jetski.locacoes.event;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Domain Event: Check-In Time Changed
 *
 * <p>Published when the check-in date/time of an active rental is modified.
 * This event is consumed by:
 * <ul>
 *   <li>AuditEventListener - to record the audit trail with before/after state</li>
 * </ul>
 *
 * @param tenantId       The tenant that owns this rental
 * @param locacaoId      The unique identifier of the rental
 * @param operadorId     The user who performed the change
 * @param dataAnterior   Previous check-in date/time
 * @param dataNova       New check-in date/time
 *
 * @author Jetski Team
 * @since 0.11.0
 */
public record DataCheckInAlteradaEvent(
    UUID tenantId,
    UUID locacaoId,
    UUID operadorId,
    LocalDateTime dataAnterior,
    LocalDateTime dataNova
) {

    /**
     * Factory method for creating a DataCheckInAlteradaEvent.
     */
    public static DataCheckInAlteradaEvent of(
            UUID tenantId,
            UUID locacaoId,
            UUID operadorId,
            LocalDateTime dataAnterior,
            LocalDateTime dataNova) {
        return new DataCheckInAlteradaEvent(tenantId, locacaoId, operadorId, dataAnterior, dataNova);
    }
}
