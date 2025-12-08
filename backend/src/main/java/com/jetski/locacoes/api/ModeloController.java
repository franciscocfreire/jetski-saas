package com.jetski.locacoes.api;

import com.jetski.locacoes.api.dto.ModeloCreateRequest;
import com.jetski.locacoes.api.dto.ModeloMidiaRequest;
import com.jetski.locacoes.api.dto.ModeloMidiaResponse;
import com.jetski.locacoes.api.dto.ModeloResponse;
import com.jetski.locacoes.api.dto.ModeloUpdateRequest;
import com.jetski.locacoes.domain.Modelo;
import com.jetski.locacoes.internal.ModeloMidiaService;
import com.jetski.locacoes.internal.ModeloService;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Controller: Jetski Models Management
 *
 * Endpoints for managing jetski models (list, create, update, deactivate).
 * Available to ADMIN_TENANT and GERENTE roles.
 *
 * @author Jetski Team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/v1/tenants/{tenantId}/modelos")
@Tag(name = "Modelos", description = "Gerenciamento de modelos de jetski")
@RequiredArgsConstructor
@Slf4j
public class ModeloController {

    private final ModeloService modeloService;
    private final ModeloMidiaService midiaService;

    /**
     * List all active jetski models for a tenant.
     *
     * Returns list of all active models with pricing information.
     * Query parameter 'includeInactive' defaults to false.
     *
     * Requires: ADMIN_TENANT or GERENTE role
     *
     * @param tenantId Tenant UUID (from path)
     * @param includeInactive Include inactive models (default: false)
     * @return List of models
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE', 'OPERADOR')")
    @Operation(
        summary = "Listar modelos de jetski",
        description = "Lista todos os modelos de jetski do tenant com informações de preços. " +
                      "Por padrão, retorna apenas modelos ativos. Use ?includeInactive=true para incluir inativos."
    )
    public ResponseEntity<List<ModeloResponse>> listModelos(
        @Parameter(description = "UUID do tenant")
        @PathVariable UUID tenantId,
        @Parameter(description = "Incluir modelos inativos")
        @RequestParam(defaultValue = "false") boolean includeInactive
    ) {
        log.info("GET /v1/tenants/{}/modelos?includeInactive={}", tenantId, includeInactive);

        // Validate tenant context matches path parameter
        validateTenantContext(tenantId);

        List<Modelo> modelos = includeInactive
            ? modeloService.listAllModels()
            : modeloService.listActiveModels();

        List<ModeloResponse> response = modelos.stream()
            .map(this::toResponse)
            .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    /**
     * Get a specific jetski model by ID.
     *
     * Returns detailed information about a jetski model.
     *
     * Requires: ADMIN_TENANT or GERENTE role
     *
     * @param tenantId Tenant UUID (from path)
     * @param id Model UUID (from path)
     * @return Model details
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE', 'OPERADOR')")
    @Operation(
        summary = "Obter modelo por ID",
        description = "Retorna os detalhes de um modelo específico de jetski."
    )
    public ResponseEntity<ModeloResponse> getModelo(
        @Parameter(description = "UUID do tenant")
        @PathVariable UUID tenantId,
        @Parameter(description = "UUID do modelo")
        @PathVariable UUID id
    ) {
        log.info("GET /v1/tenants/{}/modelos/{}", tenantId, id);

        // Validate tenant context matches path parameter
        validateTenantContext(tenantId);

        Modelo modelo = modeloService.findById(id);
        ModeloResponse response = toResponse(modelo);

        return ResponseEntity.ok(response);
    }

    /**
     * Create a new jetski model.
     *
     * Creates a new model with pricing configuration.
     *
     * Validations:
     * - Name must be unique within tenant
     * - Base price must be greater than zero
     *
     * Requires: ADMIN_TENANT or GERENTE role
     *
     * @param tenantId Tenant UUID (from path)
     * @param request Model creation request
     * @return Created model details
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE')")
    @Operation(
        summary = "Criar novo modelo de jetski",
        description = "Cria um novo modelo de jetski com configuração de preços. " +
                      "O nome do modelo deve ser único dentro do tenant."
    )
    public ResponseEntity<ModeloResponse> createModelo(
        @Parameter(description = "UUID do tenant")
        @PathVariable UUID tenantId,
        @Valid @RequestBody ModeloCreateRequest request
    ) {
        log.info("POST /v1/tenants/{}/modelos - nome: {}", tenantId, request.getNome());

        // Validate tenant context matches path parameter
        validateTenantContext(tenantId);

        Modelo modelo = toEntity(request, tenantId);
        Modelo created = modeloService.createModelo(modelo);
        ModeloResponse response = toResponse(created);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Update an existing jetski model.
     *
     * Updates model information and pricing configuration.
     *
     * Validations:
     * - Model must exist and be active
     * - If changing name, new name must be unique
     *
     * Requires: ADMIN_TENANT or GERENTE role
     *
     * @param tenantId Tenant UUID (from path)
     * @param id Model UUID (from path)
     * @param request Model update request
     * @return Updated model details
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE')")
    @Operation(
        summary = "Atualizar modelo de jetski",
        description = "Atualiza as informações de um modelo existente. " +
                      "Modelo deve estar ativo."
    )
    public ResponseEntity<ModeloResponse> updateModelo(
        @Parameter(description = "UUID do tenant")
        @PathVariable UUID tenantId,
        @Parameter(description = "UUID do modelo")
        @PathVariable UUID id,
        @Valid @RequestBody ModeloUpdateRequest request
    ) {
        log.info("PUT /v1/tenants/{}/modelos/{}", tenantId, id);

        // Validate tenant context matches path parameter
        validateTenantContext(tenantId);

        Modelo updates = toEntity(request);
        Modelo updated = modeloService.updateModelo(id, updates);
        ModeloResponse response = toResponse(updated);

        return ResponseEntity.ok(response);
    }

    /**
     * Deactivate a jetski model.
     *
     * Soft-delete: sets modelo.ativo = false.
     *
     * Validations:
     * - Model must exist and be active
     *
     * Requires: ADMIN_TENANT role
     *
     * @param tenantId Tenant UUID (from path)
     * @param id Model UUID (from path)
     * @return Deactivated model details
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN_TENANT')")
    @Operation(
        summary = "Desativar modelo de jetski",
        description = "Desativa um modelo de jetski (soft delete). " +
                      "Modelo não poderá mais ser utilizado para novos jetskis."
    )
    public ResponseEntity<ModeloResponse> deactivateModelo(
        @Parameter(description = "UUID do tenant")
        @PathVariable UUID tenantId,
        @Parameter(description = "UUID do modelo")
        @PathVariable UUID id
    ) {
        log.info("DELETE /v1/tenants/{}/modelos/{}", tenantId, id);

        // Validate tenant context matches path parameter
        validateTenantContext(tenantId);

        Modelo deactivated = modeloService.deactivateModelo(id);
        ModeloResponse response = toResponse(deactivated);

        return ResponseEntity.ok(response);
    }

    /**
     * Reactivate a jetski model.
     *
     * Sets modelo.ativo = true.
     *
     * Validations:
     * - Model must exist and be inactive
     *
     * Requires: ADMIN_TENANT role
     *
     * @param tenantId Tenant UUID (from path)
     * @param id Model UUID (from path)
     * @return Reactivated model details
     */
    @PostMapping("/{id}/reactivate")
    @PreAuthorize("hasRole('ADMIN_TENANT')")
    @Operation(
        summary = "Reativar modelo de jetski",
        description = "Reativa um modelo de jetski previamente desativado."
    )
    public ResponseEntity<ModeloResponse> reactivateModelo(
        @Parameter(description = "UUID do tenant")
        @PathVariable UUID tenantId,
        @Parameter(description = "UUID do modelo")
        @PathVariable UUID id
    ) {
        log.info("POST /v1/tenants/{}/modelos/{}/reactivate", tenantId, id);

        // Validate tenant context matches path parameter
        validateTenantContext(tenantId);

        Modelo reactivated = modeloService.reactivateModelo(id);
        ModeloResponse response = toResponse(reactivated);

        return ResponseEntity.ok(response);
    }

    /**
     * Partially update a jetski model.
     *
     * Similar to PUT but only updates provided fields.
     *
     * @param tenantId Tenant UUID (from path)
     * @param id Model UUID (from path)
     * @param request Partial model update request
     * @return Updated model details
     */
    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE')")
    @Operation(
        summary = "Atualizar parcialmente modelo de jetski",
        description = "Atualiza apenas os campos fornecidos de um modelo existente."
    )
    public ResponseEntity<ModeloResponse> patchModelo(
        @Parameter(description = "UUID do tenant")
        @PathVariable UUID tenantId,
        @Parameter(description = "UUID do modelo")
        @PathVariable UUID id,
        @RequestBody ModeloUpdateRequest request
    ) {
        log.info("PATCH /v1/tenants/{}/modelos/{}", tenantId, id);

        // Validate tenant context matches path parameter
        validateTenantContext(tenantId);

        Modelo updates = toEntity(request);
        Modelo updated = modeloService.updateModelo(id, updates);
        ModeloResponse response = toResponse(updated);

        return ResponseEntity.ok(response);
    }

    // ========== Media Endpoints ==========

    /**
     * List all media (images/videos) for a model.
     */
    @GetMapping("/{id}/midias")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE', 'OPERADOR')")
    @Operation(
        summary = "Listar mídias do modelo",
        description = "Lista todas as imagens e vídeos associados a um modelo de jetski."
    )
    public ResponseEntity<List<ModeloMidiaResponse>> listMidias(
        @PathVariable UUID tenantId,
        @PathVariable UUID id
    ) {
        log.info("GET /v1/tenants/{}/modelos/{}/midias", tenantId, id);
        validateTenantContext(tenantId);

        List<ModeloMidiaResponse> midias = midiaService.listByModelo(id);
        return ResponseEntity.ok(midias);
    }

    /**
     * Add a new media item (image/video) to a model.
     */
    @PostMapping("/{id}/midias")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE')")
    @Operation(
        summary = "Adicionar mídia ao modelo",
        description = "Adiciona uma nova imagem ou vídeo ao modelo de jetski."
    )
    public ResponseEntity<ModeloMidiaResponse> addMidia(
        @PathVariable UUID tenantId,
        @PathVariable UUID id,
        @Valid @RequestBody ModeloMidiaRequest request
    ) {
        log.info("POST /v1/tenants/{}/modelos/{}/midias - tipo: {}", tenantId, id, request.tipo());
        validateTenantContext(tenantId);

        ModeloMidiaResponse created = midiaService.addMidia(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Update an existing media item.
     */
    @PutMapping("/{id}/midias/{midiaId}")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE')")
    @Operation(
        summary = "Atualizar mídia",
        description = "Atualiza uma imagem ou vídeo existente."
    )
    public ResponseEntity<ModeloMidiaResponse> updateMidia(
        @PathVariable UUID tenantId,
        @PathVariable UUID id,
        @PathVariable UUID midiaId,
        @Valid @RequestBody ModeloMidiaRequest request
    ) {
        log.info("PUT /v1/tenants/{}/modelos/{}/midias/{}", tenantId, id, midiaId);
        validateTenantContext(tenantId);

        ModeloMidiaResponse updated = midiaService.updateMidia(midiaId, request);
        return ResponseEntity.ok(updated);
    }

    /**
     * Delete a media item.
     */
    @DeleteMapping("/{id}/midias/{midiaId}")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE')")
    @Operation(
        summary = "Remover mídia",
        description = "Remove uma imagem ou vídeo do modelo."
    )
    public ResponseEntity<Void> deleteMidia(
        @PathVariable UUID tenantId,
        @PathVariable UUID id,
        @PathVariable UUID midiaId
    ) {
        log.info("DELETE /v1/tenants/{}/modelos/{}/midias/{}", tenantId, id, midiaId);
        validateTenantContext(tenantId);

        midiaService.deleteMidia(midiaId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Set a media item as the principal (main) image.
     */
    @PostMapping("/{id}/midias/{midiaId}/principal")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE')")
    @Operation(
        summary = "Definir mídia como principal",
        description = "Define uma imagem como a imagem principal do modelo (exibida no marketplace)."
    )
    public ResponseEntity<ModeloMidiaResponse> setPrincipal(
        @PathVariable UUID tenantId,
        @PathVariable UUID id,
        @PathVariable UUID midiaId
    ) {
        log.info("POST /v1/tenants/{}/modelos/{}/midias/{}/principal", tenantId, id, midiaId);
        validateTenantContext(tenantId);

        ModeloMidiaResponse updated = midiaService.setPrincipal(midiaId);
        return ResponseEntity.ok(updated);
    }

    /**
     * Reorder media items for a model.
     */
    @PutMapping("/{id}/midias/reorder")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE')")
    @Operation(
        summary = "Reordenar mídias",
        description = "Reordena as mídias do modelo conforme a lista de IDs fornecida."
    )
    public ResponseEntity<List<ModeloMidiaResponse>> reorderMidias(
        @PathVariable UUID tenantId,
        @PathVariable UUID id,
        @RequestBody List<UUID> orderedIds
    ) {
        log.info("PUT /v1/tenants/{}/modelos/{}/midias/reorder - count: {}", tenantId, id, orderedIds.size());
        validateTenantContext(tenantId);

        List<ModeloMidiaResponse> reordered = midiaService.reorder(id, orderedIds);
        return ResponseEntity.ok(reordered);
    }

    // ========== Private Helper Methods ==========

    private void validateTenantContext(UUID tenantId) {
        UUID contextTenantId = TenantContext.getTenantId();
        if (!tenantId.equals(contextTenantId)) {
            log.error("Tenant mismatch: path={}, context={}", tenantId, contextTenantId);
            throw new IllegalArgumentException("Tenant ID mismatch");
        }
    }

    private ModeloResponse toResponse(Modelo modelo) {
        return ModeloResponse.builder()
            .id(modelo.getId())
            .tenantId(modelo.getTenantId())
            .nome(modelo.getNome())
            .fabricante(modelo.getFabricante())
            .potenciaHp(modelo.getPotenciaHp())
            .capacidadePessoas(modelo.getCapacidadePessoas())
            .precoBaseHora(modelo.getPrecoBaseHora())
            .toleranciaMin(modelo.getToleranciaMin())
            .taxaHoraExtra(modelo.getTaxaHoraExtra())
            .incluiCombustivel(modelo.getIncluiCombustivel())
            .caucao(modelo.getCaucao())
            .fotoReferenciaUrl(modelo.getFotoReferenciaUrl())
            .pacotesJson(modelo.getPacotesJson())
            .ativo(modelo.getAtivo())
            .exibirNoMarketplace(modelo.getExibirNoMarketplace())
            .createdAt(modelo.getCreatedAt())
            .updatedAt(modelo.getUpdatedAt())
            .build();
    }

    private Modelo toEntity(ModeloCreateRequest request, UUID tenantId) {
        return Modelo.builder()
            .tenantId(tenantId)
            .nome(request.getNome())
            .fabricante(request.getFabricante())
            .potenciaHp(request.getPotenciaHp())
            .capacidadePessoas(request.getCapacidadePessoas())
            .precoBaseHora(request.getPrecoBaseHora())
            .toleranciaMin(request.getToleranciaMin() != null ? request.getToleranciaMin() : 5)
            .taxaHoraExtra(request.getTaxaHoraExtra() != null ? request.getTaxaHoraExtra() : BigDecimal.ZERO)
            .incluiCombustivel(request.getIncluiCombustivel() != null ? request.getIncluiCombustivel() : false)
            .caucao(request.getCaucao() != null ? request.getCaucao() : BigDecimal.ZERO)
            .fotoReferenciaUrl(request.getFotoReferenciaUrl())
            .pacotesJson(request.getPacotesJson())
            .ativo(true)
            .build();
    }

    private Modelo toEntity(ModeloUpdateRequest request) {
        return Modelo.builder()
            .nome(request.getNome())
            .fabricante(request.getFabricante())
            .potenciaHp(request.getPotenciaHp())
            .capacidadePessoas(request.getCapacidadePessoas())
            .precoBaseHora(request.getPrecoBaseHora())
            .toleranciaMin(request.getToleranciaMin())
            .taxaHoraExtra(request.getTaxaHoraExtra())
            .incluiCombustivel(request.getIncluiCombustivel())
            .caucao(request.getCaucao())
            .fotoReferenciaUrl(request.getFotoReferenciaUrl())
            .pacotesJson(request.getPacotesJson())
            .exibirNoMarketplace(request.getExibirNoMarketplace())
            .build();
    }
}
