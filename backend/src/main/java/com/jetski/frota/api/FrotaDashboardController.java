package com.jetski.frota.api;

import com.jetski.frota.api.dto.FrotaDashboardResponse;
import com.jetski.frota.internal.FrotaKpiService;
import com.jetski.shared.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST Controller: Fleet Dashboard
 *
 * Exposes comprehensive fleet management dashboard with KPIs and operational metrics.
 *
 * Available to: GERENTE, ADMIN_TENANT
 *
 * Endpoints:
 * - GET /dashboard - Get complete fleet dashboard
 *
 * @author Jetski Team
 * @since 0.9.0
 */
@RestController
@RequestMapping("/v1/frota")
@Tag(name = "Frota Dashboard", description = "Fleet management dashboard with KPIs and metrics")
@SecurityRequirement(name = "bearer-jwt")
@RequiredArgsConstructor
@Slf4j
public class FrotaDashboardController {

    private final FrotaKpiService frotaKpiService;

    /**
     * GET /v1/frota/dashboard
     *
     * Get comprehensive fleet dashboard with all KPIs and operational metrics.
     *
     * Returns:
     * - Fleet summary (total, available, in use, maintenance, unavailable)
     * - Status distribution
     * - Utilization metrics (rate, hours rented, average duration)
     * - Revenue metrics (today, week, month, averages)
     * - Maintenance metrics (open orders, pending, average time, upcoming maintenance)
     * - Top performers (most revenue, highest utilization)
     * - Attention items (overdue maintenance, low utilization, long maintenance)
     *
     * Access: GERENTE, ADMIN_TENANT
     *
     * @return Complete fleet dashboard
     */
    @GetMapping("/dashboard")
    @Operation(
        summary = "Get fleet dashboard",
        description = "Returns comprehensive fleet management dashboard with KPIs, utilization metrics, revenue analysis, maintenance tracking, and attention items"
    )
    @ApiResponse(
        responseCode = "200",
        description = "Dashboard generated successfully",
        content = @Content(schema = @Schema(implementation = FrotaDashboardResponse.class))
    )
    @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing JWT token")
    @ApiResponse(responseCode = "403", description = "Forbidden - user does not have required role")
    public ResponseEntity<FrotaDashboardResponse> getDashboard() {
        UUID tenantId = TenantContext.getTenantId();
        log.info("GET /v1/frota/dashboard - tenant_id: {}", tenantId);

        FrotaDashboardResponse dashboard = frotaKpiService.generateDashboard(tenantId);

        log.info("Fleet dashboard generated for tenant {}: {} jetskis, {:.1f}% disponibilidade, {:.1f}% utilização",
                tenantId,
                dashboard.getSummary().getTotalJetskis(),
                dashboard.getSummary().getPercentualDisponibilidade(),
                dashboard.getUtilization().getTaxaUtilizacao());

        return ResponseEntity.ok(dashboard);
    }
}
