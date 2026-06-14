package com.jetski.pagamentos.api;

import com.jetski.pagamentos.api.dto.DetalhesPendenciasResponse;
import com.jetski.pagamentos.api.dto.PagamentoVendedorResponse;
import com.jetski.pagamentos.api.dto.PendenciasPagamentoResponse;
import com.jetski.pagamentos.api.dto.RegistrarPagamentoRequest;
import com.jetski.pagamentos.domain.PagamentoVendedor;
import com.jetski.pagamentos.internal.PagamentoVendedorService;
import com.jetski.shared.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Controller: Seller Payments
 *
 * Endpoints for managing seller payments (list pending, register payment, history).
 * Available to FINANCEIRO role.
 *
 * @author Jetski Team
 * @since 0.12.0
 */
@RestController
@RequestMapping("/v1/tenants/{tenantId}/pagamentos")
@Tag(name = "Pagamentos Vendedores", description = "Gerenciamento de pagamentos para vendedores")
@RequiredArgsConstructor
@Slf4j
public class PagamentoVendedorController {

    private final PagamentoVendedorService pagamentoService;

    /**
     * List all sellers with pending payments.
     *
     * Returns list of sellers with approved commissions and/or unpaid diárias.
     *
     * Requires: FINANCEIRO role
     *
     * @param tenantId Tenant UUID (from path)
     * @return List of sellers with pending payments
     */
    @GetMapping("/pendencias")
    @PreAuthorize("hasAnyRole('FINANCEIRO', 'ADMIN_TENANT', 'GERENTE')")
    @Operation(
        summary = "Listar pendências de pagamento",
        description = "Lista todos os vendedores com comissões aprovadas e/ou diárias não pagas."
    )
    public ResponseEntity<List<PendenciasPagamentoResponse>> listarPendencias(
        @Parameter(description = "UUID do tenant")
        @PathVariable UUID tenantId
    ) {
        log.info("GET /v1/tenants/{}/pagamentos/pendencias", tenantId);

        validateTenantContext(tenantId);

        List<PendenciasPagamentoResponse> pendencias = pagamentoService.listarPendencias(tenantId);

        return ResponseEntity.ok(pendencias);
    }

    /**
     * Get pending payments for a specific seller.
     *
     * Requires: FINANCEIRO role
     *
     * @param tenantId Tenant UUID (from path)
     * @param vendedorId Seller UUID (from path)
     * @return Pending payments summary
     */
    @GetMapping("/pendencias/{vendedorId}")
    @PreAuthorize("hasAnyRole('FINANCEIRO', 'ADMIN_TENANT', 'GERENTE')")
    @Operation(
        summary = "Obter pendências de um vendedor",
        description = "Retorna resumo de pagamentos pendentes para um vendedor específico."
    )
    public ResponseEntity<PendenciasPagamentoResponse> getPendenciasVendedor(
        @Parameter(description = "UUID do tenant")
        @PathVariable UUID tenantId,
        @Parameter(description = "UUID do vendedor")
        @PathVariable UUID vendedorId
    ) {
        log.info("GET /v1/tenants/{}/pagamentos/pendencias/{}", tenantId, vendedorId);

        validateTenantContext(tenantId);

        PendenciasPagamentoResponse pendencias = pagamentoService.getPendenciasVendedor(tenantId, vendedorId);

        return ResponseEntity.ok(pendencias);
    }

    /**
     * Get detailed pending items for a specific seller.
     *
     * Returns list of individual items (commissions, daily allowances, bonuses)
     * for partial payment selection.
     *
     * Requires: FINANCEIRO role
     *
     * @param tenantId Tenant UUID (from path)
     * @param vendedorId Seller UUID (from path)
     * @return Detailed pending items list
     */
    @GetMapping("/pendencias/{vendedorId}/detalhes")
    @PreAuthorize("hasAnyRole('FINANCEIRO', 'ADMIN_TENANT', 'GERENTE')")
    @Operation(
        summary = "Obter detalhes das pendências de um vendedor",
        description = "Retorna lista detalhada de itens pendentes para pagamento parcial."
    )
    public ResponseEntity<DetalhesPendenciasResponse> getDetalhesPendencias(
        @Parameter(description = "UUID do tenant")
        @PathVariable UUID tenantId,
        @Parameter(description = "UUID do vendedor")
        @PathVariable UUID vendedorId
    ) {
        log.info("GET /v1/tenants/{}/pagamentos/pendencias/{}/detalhes", tenantId, vendedorId);

        validateTenantContext(tenantId);

        DetalhesPendenciasResponse detalhes = pagamentoService.getDetalhesPendencias(tenantId, vendedorId);

        return ResponseEntity.ok(detalhes);
    }

    /**
     * Register a bulk payment for a seller.
     *
     * Marks all approved commissions as PAGA and all unpaid diárias as paid.
     *
     * Requires: FINANCEIRO role
     *
     * @param tenantId Tenant UUID (from path)
     * @param vendedorId Seller UUID (from path)
     * @param request Payment details
     * @param jwt JWT token for user identification
     * @return Created payment record
     */
    @PostMapping("/vendedores/{vendedorId}/pagar")
    @PreAuthorize("hasAnyRole('FINANCEIRO', 'ADMIN_TENANT', 'GERENTE')")
    @Operation(
        summary = "Registrar pagamento em lote",
        description = "Paga todas as comissões aprovadas e diárias não pagas de um vendedor. " +
                      "Requer referência de pagamento (ex: ID da transação PIX)."
    )
    public ResponseEntity<PagamentoVendedorResponse> registrarPagamento(
        @Parameter(description = "UUID do tenant")
        @PathVariable UUID tenantId,
        @Parameter(description = "UUID do vendedor")
        @PathVariable UUID vendedorId,
        @Valid @RequestBody RegistrarPagamentoRequest request,
        @AuthenticationPrincipal Jwt jwt
    ) {
        log.info("POST /v1/tenants/{}/pagamentos/vendedores/{}/pagar", tenantId, vendedorId);

        validateTenantContext(tenantId);

        // Use internal PostgreSQL UUID resolved from Keycloak UUID by TenantFilter
        UUID userId = TenantContext.getUsuarioId();

        PagamentoVendedor pagamento = pagamentoService.registrarPagamento(
                tenantId, vendedorId, request, userId);

        PagamentoVendedorResponse response = toResponse(pagamento);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * List payment history for all sellers.
     *
     * Requires: FINANCEIRO role
     *
     * @param tenantId Tenant UUID (from path)
     * @return List of payment records
     */
    @GetMapping("/historico")
    @PreAuthorize("hasAnyRole('FINANCEIRO', 'ADMIN_TENANT', 'GERENTE')")
    @Operation(
        summary = "Listar histórico de pagamentos",
        description = "Lista todos os pagamentos realizados para vendedores."
    )
    public ResponseEntity<List<PagamentoVendedorResponse>> listarHistorico(
        @Parameter(description = "UUID do tenant")
        @PathVariable UUID tenantId
    ) {
        log.info("GET /v1/tenants/{}/pagamentos/historico", tenantId);

        validateTenantContext(tenantId);

        List<PagamentoVendedorResponse> historico = pagamentoService.listarHistorico(tenantId);

        return ResponseEntity.ok(historico);
    }

    /**
     * List payment history for a specific seller.
     *
     * Requires: FINANCEIRO role
     *
     * @param tenantId Tenant UUID (from path)
     * @param vendedorId Seller UUID (from path)
     * @return List of payment records
     */
    @GetMapping("/vendedores/{vendedorId}/historico")
    @PreAuthorize("hasAnyRole('FINANCEIRO', 'ADMIN_TENANT', 'GERENTE')")
    @Operation(
        summary = "Listar histórico de pagamentos do vendedor",
        description = "Lista todos os pagamentos realizados para um vendedor específico."
    )
    public ResponseEntity<List<PagamentoVendedorResponse>> listarHistoricoVendedor(
        @Parameter(description = "UUID do tenant")
        @PathVariable UUID tenantId,
        @Parameter(description = "UUID do vendedor")
        @PathVariable UUID vendedorId
    ) {
        log.info("GET /v1/tenants/{}/pagamentos/vendedores/{}/historico", tenantId, vendedorId);

        validateTenantContext(tenantId);

        List<PagamentoVendedorResponse> historico = pagamentoService.listarHistoricoVendedor(tenantId, vendedorId);

        return ResponseEntity.ok(historico);
    }

    // ========== Private Helper Methods ==========

    private void validateTenantContext(UUID tenantId) {
        UUID contextTenantId = TenantContext.getTenantId();
        if (!tenantId.equals(contextTenantId)) {
            log.error("Tenant mismatch: path={}, context={}", tenantId, contextTenantId);
            throw new IllegalArgumentException("Tenant ID mismatch");
        }
    }

    private PagamentoVendedorResponse toResponse(PagamentoVendedor pagamento) {
        return PagamentoVendedorResponse.builder()
                .id(pagamento.getId())
                .tenantId(pagamento.getTenantId())
                .vendedorId(pagamento.getVendedorId())
                .vendedorNome(pagamento.getVendedorNome())
                .tipoPagamento(pagamento.getTipoPagamento())
                .valorComissoes(pagamento.getValorComissoes())
                .valorDiarias(pagamento.getValorDiarias())
                .valorBonus(pagamento.getValorBonus())
                .valorTotal(pagamento.getValorTotal())
                .chavePix(pagamento.getChavePix())
                .tipoChavePix(pagamento.getTipoChavePix())
                .referenciaPagamento(pagamento.getReferenciaPagamento())
                .comprovanteUrl(pagamento.getComprovanteUrl())
                .qtdComissoes(pagamento.getQtdComissoes())
                .qtdDiarias(pagamento.getQtdDiarias())
                .qtdBonus(pagamento.getQtdBonus())
                .periodoInicio(pagamento.getPeriodoInicio())
                .periodoFim(pagamento.getPeriodoFim())
                .pagoPor(pagamento.getPagoPor())
                .observacoes(pagamento.getObservacoes())
                .createdAt(pagamento.getCreatedAt())
                .build();
    }
}
