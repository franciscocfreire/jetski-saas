package com.jetski.combustivel.api;

import com.jetski.combustivel.api.dto.FuelCostCalculationResponse;
import com.jetski.combustivel.api.dto.FuelPolicyCreateRequest;
import com.jetski.combustivel.api.dto.FuelPolicyResponse;
import com.jetski.combustivel.api.dto.FuelPolicyUpdateRequest;
import com.jetski.combustivel.domain.FuelPolicy;
import com.jetski.combustivel.domain.FuelPolicyType;
import com.jetski.combustivel.internal.FuelPolicyService;
import com.jetski.locacoes.domain.Locacao;
import com.jetski.shared.exception.NotFoundException;
import com.jetski.shared.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Controller: FuelPolicyController
 *
 * REST API for fuel policy management.
 *
 * Endpoints:
 * - POST /v1/tenants/{tenantId}/fuel-policies - Create policy
 * - GET /v1/tenants/{tenantId}/fuel-policies - List policies
 * - GET /v1/tenants/{tenantId}/fuel-policies/{id} - Get policy by ID
 * - PUT /v1/tenants/{tenantId}/fuel-policies/{id} - Update policy
 * - DELETE /v1/tenants/{tenantId}/fuel-policies/{id} - Delete policy
 * - GET /v1/tenants/{tenantId}/fuel-policies/applicable - Get applicable policy for jetski/modelo
 * - POST /v1/tenants/{tenantId}/fuel-policies/calculate - Calculate fuel cost for rental
 *
 * Authorization:
 * - GERENTE: Full access
 * - ADMIN_TENANT: Full access
 * - OPERADOR: Read-only
 *
 * @author Jetski Team
 * @since 0.8.0
 */
@Slf4j
@RestController
@RequestMapping("/v1/tenants/{tenantId}/fuel-policies")
@RequiredArgsConstructor
@Tag(name = "Combustivel", description = "Fuel management API - Refills, Policies, Pricing")
public class FuelPolicyController {

    private final FuelPolicyService fuelPolicyService;

    /**
     * POST /v1/tenants/{tenantId}/fuel-policies
     *
     * Create a new fuel policy.
     *
     * @param tenantId Tenant ID from path
     * @param request Policy creation request
     * @return 201 Created with FuelPolicyResponse
     */
    @PostMapping
    @Operation(summary = "Create fuel policy",
               description = "Create new fuel charging policy (INCLUSO, MEDIDO, TAXA_FIXA)")
    public ResponseEntity<FuelPolicyResponse> criar(
        @PathVariable UUID tenantId,
        @Valid @RequestBody FuelPolicyCreateRequest request
    ) {
        log.info("POST /v1/tenants/{}/fuel-policies - tipo={}, aplicavelA={}",
                 tenantId, request.getTipo(), request.getAplicavelA());

        validateTenantContext(tenantId);

        FuelPolicy policy = mapToEntity(request);

        FuelPolicy saved = fuelPolicyService.criar(tenantId, policy);

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(mapToResponse(saved));
    }

    /**
     * GET /v1/tenants/{tenantId}/fuel-policies
     *
     * List all fuel policies with optional filters.
     *
     * @param tenantId Tenant ID
     * @param aplicavelA Optional: filter by type (GLOBAL, MODELO, JETSKI)
     * @param ativo Optional: filter by active status
     * @return 200 OK with list of FuelPolicyResponse
     */
    @GetMapping
    @Operation(summary = "List fuel policies",
               description = "List all fuel policies with optional filters")
    public ResponseEntity<List<FuelPolicyResponse>> listar(
        @PathVariable UUID tenantId,
        @RequestParam(required = false) FuelPolicyType aplicavelA,
        @RequestParam(required = false) Boolean ativo
    ) {
        log.info("GET /v1/tenants/{}/fuel-policies - aplicavelA={}, ativo={}",
                 tenantId, aplicavelA, ativo);

        validateTenantContext(tenantId);

        List<FuelPolicy> policies;
        if (aplicavelA != null) {
            policies = fuelPolicyService.listarPorTipo(tenantId, aplicavelA, ativo);
        } else {
            policies = fuelPolicyService.listarTodas(tenantId, ativo);
        }

        List<FuelPolicyResponse> responses = policies.stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    /**
     * GET /v1/tenants/{tenantId}/fuel-policies/{id}
     *
     * Get policy by ID.
     *
     * @param tenantId Tenant ID
     * @param id Policy ID
     * @return 200 OK with FuelPolicyResponse
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get policy by ID")
    public ResponseEntity<FuelPolicyResponse> buscarPorId(
        @PathVariable UUID tenantId,
        @PathVariable Long id
    ) {
        log.info("GET /v1/tenants/{}/fuel-policies/{}", tenantId, id);

        validateTenantContext(tenantId);

        FuelPolicy policy = fuelPolicyService.buscarPorId(tenantId, id)
            .orElseThrow(() -> new NotFoundException("Política de combustível não encontrada: " + id));

        return ResponseEntity.ok(mapToResponse(policy));
    }

    /**
     * PUT /v1/tenants/{tenantId}/fuel-policies/{id}
     *
     * Update existing policy.
     *
     * @param tenantId Tenant ID
     * @param id Policy ID
     * @param request Update request
     * @return 200 OK with updated FuelPolicyResponse
     */
    @PutMapping("/{id}")
    @Operation(summary = "Update policy")
    public ResponseEntity<FuelPolicyResponse> atualizar(
        @PathVariable UUID tenantId,
        @PathVariable Long id,
        @Valid @RequestBody FuelPolicyUpdateRequest request
    ) {
        log.info("PUT /v1/tenants/{}/fuel-policies/{}", tenantId, id);

        validateTenantContext(tenantId);

        FuelPolicy updated = fuelPolicyService.atualizar(tenantId, id, request);

        return ResponseEntity.ok(mapToResponse(updated));
    }

    /**
     * DELETE /v1/tenants/{tenantId}/fuel-policies/{id}
     *
     * Delete (inactivate) policy.
     *
     * @param tenantId Tenant ID
     * @param id Policy ID
     * @return 204 No Content
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete policy",
               description = "Soft delete by setting ativo=false")
    public ResponseEntity<Void> deletar(
        @PathVariable UUID tenantId,
        @PathVariable Long id
    ) {
        log.info("DELETE /v1/tenants/{}/fuel-policies/{}", tenantId, id);

        validateTenantContext(tenantId);

        fuelPolicyService.deletar(tenantId, id);

        return ResponseEntity.noContent().build();
    }

    /**
     * GET /v1/tenants/{tenantId}/fuel-policies/applicable
     *
     * Get applicable policy for jetski/modelo following hierarchy:
     * 1. JETSKI specific
     * 2. MODELO specific
     * 3. GLOBAL
     *
     * @param tenantId Tenant ID
     * @param jetskiId Jetski ID
     * @param modeloId Modelo ID
     * @return 200 OK with FuelPolicyResponse
     */
    @GetMapping("/applicable")
    @Operation(summary = "Get applicable policy",
               description = "Get applicable policy following hierarchy: JETSKI → MODELO → GLOBAL")
    public ResponseEntity<FuelPolicyResponse> buscarPoliticaAplicavel(
        @PathVariable UUID tenantId,
        @RequestParam UUID jetskiId,
        @RequestParam UUID modeloId
    ) {
        log.info("GET /v1/tenants/{}/fuel-policies/applicable - jetski={}, modelo={}",
                 tenantId, jetskiId, modeloId);

        validateTenantContext(tenantId);

        FuelPolicy policy = fuelPolicyService.buscarPoliticaAplicavel(tenantId, jetskiId, modeloId);

        return ResponseEntity.ok(mapToResponse(policy));
    }

    // =====================================================
    // Helper Methods
    // =====================================================

    private void validateTenantContext(UUID tenantId) {
        if (!TenantContext.getTenantId().equals(tenantId)) {
            throw new IllegalArgumentException("Tenant ID mismatch");
        }
    }

    private FuelPolicy mapToEntity(FuelPolicyCreateRequest request) {
        return FuelPolicy.builder()
            .nome(request.getNome())
            .tipo(request.getTipo())
            .aplicavelA(request.getAplicavelA())
            .referenciaId(request.getReferenciaId())
            .valorTaxaPorHora(request.getValorTaxaPorHora())
            .comissionavel(request.getComissionavel())
            .ativo(request.getAtivo())
            .prioridade(request.getPrioridade())
            .descricao(request.getDescricao())
            .build();
    }

    private FuelPolicyResponse mapToResponse(FuelPolicy entity) {
        return FuelPolicyResponse.builder()
            .id(entity.getId())
            .tenantId(entity.getTenantId())
            .nome(entity.getNome())
            .tipo(entity.getTipo())
            .aplicavelA(entity.getAplicavelA())
            .referenciaId(entity.getReferenciaId())
            .valorTaxaPorHora(entity.getValorTaxaPorHora())
            .comissionavel(entity.getComissionavel())
            .ativo(entity.getAtivo())
            .prioridade(entity.getPrioridade())
            .descricao(entity.getDescricao())
            .createdAt(entity.getCreatedAt())
            .updatedAt(entity.getUpdatedAt())
            .build();
    }
}
