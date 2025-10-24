package com.jetski.locacoes.api;

import com.jetski.locacoes.api.dto.JetskiCreateRequest;
import com.jetski.locacoes.api.dto.JetskiResponse;
import com.jetski.locacoes.api.dto.JetskiUpdateRequest;
import com.jetski.locacoes.domain.Jetski;
import com.jetski.locacoes.domain.JetskiStatus;
import com.jetski.locacoes.internal.JetskiService;
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
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Controller: Jetski Fleet Management
 *
 * Endpoints for managing individual jetski units (list, create, update, deactivate).
 * Available to ADMIN_TENANT and GERENTE roles.
 *
 * @author Jetski Team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/v1/tenants/{tenantId}/jetskis")
@Tag(name = "Jetskis", description = "Gerenciamento da frota de jetskis")
@RequiredArgsConstructor
@Slf4j
public class JetskiController {

    private final JetskiService jetskiService;

    /**
     * List all jetskis for a tenant.
     *
     * Returns list of jetskis with status and odometer information.
     * Query parameter 'includeInactive' defaults to false.
     *
     * Requires: ADMIN_TENANT, GERENTE, or OPERADOR role
     *
     * @param tenantId Tenant UUID (from path)
     * @param includeInactive Include inactive jetskis (default: false)
     * @return List of jetskis
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE', 'OPERADOR')")
    @Operation(
        summary = "Listar jetskis da frota",
        description = "Lista todos os jetskis do tenant com informações de status e horímetro. " +
                      "Por padrão, retorna apenas jetskis ativos. Use ?includeInactive=true para incluir inativos."
    )
    public ResponseEntity<List<JetskiResponse>> listJetskis(
        @Parameter(description = "UUID do tenant")
        @PathVariable UUID tenantId,
        @Parameter(description = "Incluir jetskis inativos")
        @RequestParam(defaultValue = "false") boolean includeInactive
    ) {
        log.info("GET /v1/tenants/{}/jetskis?includeInactive={}", tenantId, includeInactive);

        // Validate tenant context matches path parameter
        validateTenantContext(tenantId);

        List<Jetski> jetskis = includeInactive
            ? jetskiService.listAllJetskis()
            : jetskiService.listActiveJetskis();

        List<JetskiResponse> response = jetskis.stream()
            .map(this::toResponse)
            .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    /**
     * Get a specific jetski by ID.
     *
     * Returns detailed information about a jetski unit.
     *
     * Requires: ADMIN_TENANT, GERENTE, or OPERADOR role
     *
     * @param tenantId Tenant UUID (from path)
     * @param id Jetski UUID (from path)
     * @return Jetski details
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE', 'OPERADOR')")
    @Operation(
        summary = "Obter jetski por ID",
        description = "Retorna os detalhes de um jetski específico da frota."
    )
    public ResponseEntity<JetskiResponse> getJetski(
        @Parameter(description = "UUID do tenant")
        @PathVariable UUID tenantId,
        @Parameter(description = "UUID do jetski")
        @PathVariable UUID id
    ) {
        log.info("GET /v1/tenants/{}/jetskis/{}", tenantId, id);

        // Validate tenant context matches path parameter
        validateTenantContext(tenantId);

        Jetski jetski = jetskiService.findById(id);
        JetskiResponse response = toResponse(jetski);

        return ResponseEntity.ok(response);
    }

    /**
     * Create a new jetski unit.
     *
     * Creates a new jetski in the fleet.
     *
     * Validations:
     * - Model must exist
     * - Serial number must be unique within tenant
     * - Initial odometer must be >= 0
     *
     * Requires: ADMIN_TENANT or GERENTE role
     *
     * @param tenantId Tenant UUID (from path)
     * @param request Jetski creation request
     * @return Created jetski details
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE')")
    @Operation(
        summary = "Criar novo jetski",
        description = "Cria um novo jetski na frota. " +
                      "O número de série deve ser único dentro do tenant."
    )
    public ResponseEntity<JetskiResponse> createJetski(
        @Parameter(description = "UUID do tenant")
        @PathVariable UUID tenantId,
        @Valid @RequestBody JetskiCreateRequest request
    ) {
        log.info("POST /v1/tenants/{}/jetskis - serie: {}", tenantId, request.getSerie());

        // Validate tenant context matches path parameter
        validateTenantContext(tenantId);

        Jetski jetski = toEntity(request, tenantId);
        Jetski created = jetskiService.createJetski(jetski);
        JetskiResponse response = toResponse(created);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Update an existing jetski.
     *
     * Updates jetski information.
     *
     * Validations:
     * - Jetski must exist and be active
     * - If changing serial, new serial must be unique
     * - Odometer cannot decrease
     *
     * Requires: ADMIN_TENANT or GERENTE role
     *
     * @param tenantId Tenant UUID (from path)
     * @param id Jetski UUID (from path)
     * @param request Jetski update request
     * @return Updated jetski details
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE')")
    @Operation(
        summary = "Atualizar jetski",
        description = "Atualiza as informações de um jetski existente. " +
                      "Horímetro não pode diminuir."
    )
    public ResponseEntity<JetskiResponse> updateJetski(
        @Parameter(description = "UUID do tenant")
        @PathVariable UUID tenantId,
        @Parameter(description = "UUID do jetski")
        @PathVariable UUID id,
        @Valid @RequestBody JetskiUpdateRequest request
    ) {
        log.info("PUT /v1/tenants/{}/jetskis/{}", tenantId, id);

        // Validate tenant context matches path parameter
        validateTenantContext(tenantId);

        Jetski updates = toEntity(request);
        Jetski updated = jetskiService.updateJetski(id, updates);
        JetskiResponse response = toResponse(updated);

        return ResponseEntity.ok(response);
    }

    /**
     * Update jetski status.
     *
     * Changes the operational status of a jetski (RN06).
     *
     * Requires: ADMIN_TENANT, GERENTE, or OPERADOR role
     *
     * @param tenantId Tenant UUID (from path)
     * @param id Jetski UUID (from path)
     * @param status New status
     * @return Updated jetski details
     */
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE', 'OPERADOR')")
    @Operation(
        summary = "Atualizar status do jetski",
        description = "Atualiza o status operacional do jetski (DISPONIVEL, LOCADO, MANUTENCAO, INDISPONIVEL). " +
                      "RN06: Jetskis em MANUTENCAO não podem ser reservados."
    )
    public ResponseEntity<JetskiResponse> updateJetskiStatus(
        @Parameter(description = "UUID do tenant")
        @PathVariable UUID tenantId,
        @Parameter(description = "UUID do jetski")
        @PathVariable UUID id,
        @Parameter(description = "Novo status")
        @RequestParam JetskiStatus status
    ) {
        log.info("PATCH /v1/tenants/{}/jetskis/{}/status?status={}", tenantId, id, status);

        // Validate tenant context matches path parameter
        validateTenantContext(tenantId);

        Jetski updated = jetskiService.updateStatus(id, status);
        JetskiResponse response = toResponse(updated);

        return ResponseEntity.ok(response);
    }

    /**
     * Deactivate a jetski.
     *
     * Soft-delete: sets jetski.ativo = false.
     *
     * Validations:
     * - Jetski must exist and be active
     *
     * Requires: ADMIN_TENANT role
     *
     * @param tenantId Tenant UUID (from path)
     * @param id Jetski UUID (from path)
     * @return Deactivated jetski details
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN_TENANT')")
    @Operation(
        summary = "Desativar jetski",
        description = "Desativa um jetski (soft delete). " +
                      "Jetski não poderá mais ser utilizado para novas locações."
    )
    public ResponseEntity<JetskiResponse> deactivateJetski(
        @Parameter(description = "UUID do tenant")
        @PathVariable UUID tenantId,
        @Parameter(description = "UUID do jetski")
        @PathVariable UUID id
    ) {
        log.info("DELETE /v1/tenants/{}/jetskis/{}", tenantId, id);

        // Validate tenant context matches path parameter
        validateTenantContext(tenantId);

        Jetski deactivated = jetskiService.deactivateJetski(id);
        JetskiResponse response = toResponse(deactivated);

        return ResponseEntity.ok(response);
    }

    /**
     * Reactivate a jetski.
     *
     * Sets jetski.ativo = true.
     *
     * Validations:
     * - Jetski must exist and be inactive
     *
     * Requires: ADMIN_TENANT role
     *
     * @param tenantId Tenant UUID (from path)
     * @param id Jetski UUID (from path)
     * @return Reactivated jetski details
     */
    @PostMapping("/{id}/reactivate")
    @PreAuthorize("hasRole('ADMIN_TENANT')")
    @Operation(
        summary = "Reativar jetski",
        description = "Reativa um jetski previamente desativado."
    )
    public ResponseEntity<JetskiResponse> reactivateJetski(
        @Parameter(description = "UUID do tenant")
        @PathVariable UUID tenantId,
        @Parameter(description = "UUID do jetski")
        @PathVariable UUID id
    ) {
        log.info("POST /v1/tenants/{}/jetskis/{}/reactivate", tenantId, id);

        // Validate tenant context matches path parameter
        validateTenantContext(tenantId);

        Jetski reactivated = jetskiService.reactivateJetski(id);
        JetskiResponse response = toResponse(reactivated);

        return ResponseEntity.ok(response);
    }

    // ========== Private Helper Methods ==========

    private void validateTenantContext(UUID tenantId) {
        UUID contextTenantId = TenantContext.getTenantId();
        if (!tenantId.equals(contextTenantId)) {
            log.error("Tenant mismatch: path={}, context={}", tenantId, contextTenantId);
            throw new IllegalArgumentException("Tenant ID mismatch");
        }
    }

    private JetskiResponse toResponse(Jetski jetski) {
        return JetskiResponse.builder()
            .id(jetski.getId())
            .tenantId(jetski.getTenantId())
            .modeloId(jetski.getModeloId())
            .serie(jetski.getSerie())
            .ano(jetski.getAno())
            .horimetroAtual(jetski.getHorimetroAtual())
            .status(jetski.getStatus())
            .ativo(jetski.getAtivo())
            .createdAt(jetski.getCreatedAt())
            .updatedAt(jetski.getUpdatedAt())
            .build();
    }

    private Jetski toEntity(JetskiCreateRequest request, UUID tenantId) {
        return Jetski.builder()
            .tenantId(tenantId)
            .modeloId(request.getModeloId())
            .serie(request.getSerie())
            .ano(request.getAno())
            .horimetroAtual(request.getHorimetroAtual())
            .status(request.getStatus() != null ? request.getStatus() : JetskiStatus.DISPONIVEL)
            .ativo(true)
            .build();
    }

    private Jetski toEntity(JetskiUpdateRequest request) {
        return Jetski.builder()
            .serie(request.getSerie())
            .ano(request.getAno())
            .horimetroAtual(request.getHorimetroAtual())
            .status(request.getStatus())
            .build();
    }
}
