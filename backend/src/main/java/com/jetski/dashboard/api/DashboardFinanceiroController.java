package com.jetski.dashboard.api;

import com.jetski.dashboard.api.dto.*;
import com.jetski.dashboard.internal.DashboardFinanceiroService;
import com.jetski.shared.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * REST Controller for Financial Dashboard
 *
 * <p>Endpoints:</p>
 * <ul>
 *   <li>GET /dashboard/financeiro/calendario/{ano}/{mes} - Monthly calendar data</li>
 *   <li>GET /dashboard/financeiro/receitas-despesas - Revenue vs Expenses chart data</li>
 *   <li>GET /dashboard/financeiro/dre/{ano}/{mes} - Simplified DRE (Income Statement)</li>
 *   <li>GET /dashboard/financeiro/pendentes - Pending records summary</li>
 * </ul>
 *
 * @author Jetski Team
 * @since 0.9.0
 */
@RestController
@RequestMapping("/v1/tenants/{tenantId}/dashboard/financeiro")
@RequiredArgsConstructor
@Slf4j
public class DashboardFinanceiroController {

    private final DashboardFinanceiroService dashboardService;

    /**
     * Get monthly financial calendar data.
     * Returns daily financial data for all days of the specified month.
     *
     * @param tenantId Tenant ID (from path)
     * @param ano Year
     * @param mes Month (1-12)
     * @return Calendar data with daily indicators
     */
    @GetMapping("/calendario/{ano}/{mes}")
    public ResponseEntity<CalendarioFinanceiroResponse> getCalendario(
            @PathVariable UUID tenantId,
            @PathVariable Integer ano,
            @PathVariable Integer mes
    ) {
        UUID contextTenantId = TenantContext.getTenantId();
        log.debug("GET calendario financeiro: {}/{} (tenant: {})", ano, mes, contextTenantId);

        CalendarioFinanceiroResponse response = dashboardService.getCalendarioMensal(contextTenantId, ano, mes);
        return ResponseEntity.ok(response);
    }

    /**
     * Get revenue vs expenses chart data for a date range.
     *
     * @param tenantId Tenant ID (from path)
     * @param dataInicio Start date
     * @param dataFim End date
     * @return List of daily revenue/expense data
     */
    @GetMapping("/receitas-despesas")
    public ResponseEntity<List<ReceitaDespesaDiaResponse>> getReceitasDespesas(
            @PathVariable UUID tenantId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim
    ) {
        UUID contextTenantId = TenantContext.getTenantId();
        log.debug("GET receitas-despesas: {} a {} (tenant: {})", dataInicio, dataFim, contextTenantId);

        List<ReceitaDespesaDiaResponse> response = dashboardService.getReceitasDespesas(
                contextTenantId, dataInicio, dataFim);
        return ResponseEntity.ok(response);
    }

    /**
     * Get simplified DRE (Income Statement) for a month.
     *
     * @param tenantId Tenant ID (from path)
     * @param ano Year
     * @param mes Month (1-12)
     * @return DRE data
     */
    @GetMapping("/dre/{ano}/{mes}")
    public ResponseEntity<DRESimplificadoResponse> getDRE(
            @PathVariable UUID tenantId,
            @PathVariable Integer ano,
            @PathVariable Integer mes
    ) {
        UUID contextTenantId = TenantContext.getTenantId();
        log.debug("GET DRE: {}/{} (tenant: {})", ano, mes, contextTenantId);

        DRESimplificadoResponse response = dashboardService.getDRESimplificado(contextTenantId, ano, mes);
        return ResponseEntity.ok(response);
    }

    /**
     * Get pending records that need attention.
     *
     * @param tenantId Tenant ID (from path)
     * @return Pending records summary
     */
    @GetMapping("/pendentes")
    public ResponseEntity<RegistrosPendentesResponse> getPendentes(@PathVariable UUID tenantId) {
        UUID contextTenantId = TenantContext.getTenantId();
        log.debug("GET registros pendentes (tenant: {})", contextTenantId);

        RegistrosPendentesResponse response = dashboardService.getRegistrosPendentes(contextTenantId);
        return ResponseEntity.ok(response);
    }
}
