package com.jetski.locacoes.domain.event;

import java.util.Map;
import java.util.UUID;

/**
 * Domain Event: Finalized Rental Edited
 *
 * <p>Published when a finalized rental (FINALIZADA status) is edited by
 * GERENTE or ADMIN_TENANT before the daily closure is locked.
 *
 * <p>This event is consumed by:
 * <ul>
 *   <li>AuditEventListener - to record the audit trail with before/after state</li>
 *   <li>DashboardMetricsService - to invalidate cache</li>
 *   <li>Future: Notifications, reconciliation alerts, etc.</li>
 * </ul>
 *
 * <p><strong>Security Note:</strong><br>
 * This action is restricted to GERENTE and ADMIN_TENANT roles and is only
 * allowed when the FechamentoDiario for the rental's date is not bloqueado.
 *
 * @param tenantId        The tenant that owns this rental
 * @param locacaoId       The unique identifier of the edited rental
 * @param operadorId      The user who performed the edit
 * @param dadosAnteriores Map of field names to previous values (for audit)
 * @param dadosNovos      Map of field names to new values (for audit)
 * @param motivoEdicao    Reason provided for the edit (mandatory)
 *
 * @author Jetski Team
 * @since 0.11.0
 */
public record LocacaoEditadaEvent(
    UUID tenantId,
    UUID locacaoId,
    UUID operadorId,
    Map<String, Object> dadosAnteriores,
    Map<String, Object> dadosNovos,
    String motivoEdicao
) {

    /**
     * Factory method for creating a LocacaoEditadaEvent.
     *
     * @param tenantId        Tenant ID
     * @param locacaoId       Rental ID
     * @param operadorId      User who performed the edit
     * @param dadosAnteriores Previous state
     * @param dadosNovos      New state
     * @param motivoEdicao    Reason for the edit
     * @return new LocacaoEditadaEvent instance
     */
    public static LocacaoEditadaEvent of(
            UUID tenantId,
            UUID locacaoId,
            UUID operadorId,
            Map<String, Object> dadosAnteriores,
            Map<String, Object> dadosNovos,
            String motivoEdicao) {
        return new LocacaoEditadaEvent(
            tenantId, locacaoId, operadorId, dadosAnteriores, dadosNovos, motivoEdicao
        );
    }
}
