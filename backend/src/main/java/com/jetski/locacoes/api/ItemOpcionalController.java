package com.jetski.locacoes.api;

import com.jetski.locacoes.api.dto.ItemOpcionalRequest;
import com.jetski.locacoes.api.dto.ItemOpcionalResponse;
import com.jetski.locacoes.domain.ItemOpcional;
import com.jetski.locacoes.internal.ItemOpcionalService;
import com.jetski.shared.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Controller: ItemOpcionalController
 *
 * REST API for managing optional add-on items catalog.
 *
 * Endpoints:
 * - GET /v1/tenants/{tenantId}/itens-opcionais - List items
 * - POST /v1/tenants/{tenantId}/itens-opcionais - Create item
 * - GET /v1/tenants/{tenantId}/itens-opcionais/{id} - Get item by ID
 * - PUT /v1/tenants/{tenantId}/itens-opcionais/{id} - Update item
 * - DELETE /v1/tenants/{tenantId}/itens-opcionais/{id} - Deactivate item
 *
 * @author Jetski Team
 * @since 0.8.0
 */
@Slf4j
@RestController
@RequestMapping("/v1/tenants/{tenantId}/itens-opcionais")
@RequiredArgsConstructor
@Tag(name = "Itens Opcionais", description = "Optional add-on items catalog API")
public class ItemOpcionalController {

    private final ItemOpcionalService itemOpcionalService;

    /**
     * GET /v1/tenants/{tenantId}/itens-opcionais
     *
     * List optional items with optional filter.
     */
    @GetMapping
    @Operation(summary = "List optional items", description = "List all optional items for the tenant")
    public ResponseEntity<List<ItemOpcionalResponse>> list(
        @PathVariable UUID tenantId,
        @Parameter(description = "Include inactive items") @RequestParam(defaultValue = "false") boolean includeInactive
    ) {
        log.debug("GET /v1/tenants/{}/itens-opcionais?includeInactive={}", tenantId, includeInactive);

        validateTenantContext(tenantId);

        List<ItemOpcional> items = includeInactive
            ? itemOpcionalService.listAll(tenantId)
            : itemOpcionalService.listActive(tenantId);

        List<ItemOpcionalResponse> responses = items.stream()
            .map(this::toResponse)
            .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    /**
     * POST /v1/tenants/{tenantId}/itens-opcionais
     *
     * Create a new optional item.
     */
    @PostMapping
    @Operation(summary = "Create optional item", description = "Create a new optional add-on item")
    public ResponseEntity<ItemOpcionalResponse> create(
        @PathVariable UUID tenantId,
        @Valid @RequestBody ItemOpcionalRequest request
    ) {
        log.info("POST /v1/tenants/{}/itens-opcionais - nome={}", tenantId, request.getNome());

        validateTenantContext(tenantId);

        ItemOpcional item = ItemOpcional.builder()
            .tenantId(tenantId)
            .nome(request.getNome())
            .descricao(request.getDescricao())
            .precoBase(request.getPrecoBase())
            .ativo(request.getAtivo() != null ? request.getAtivo() : true)
            .build();

        item = itemOpcionalService.create(item);
        ItemOpcionalResponse response = toResponse(item);

        log.info("Optional item created: id={}", item.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * GET /v1/tenants/{tenantId}/itens-opcionais/{id}
     *
     * Get optional item by ID.
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get optional item", description = "Get optional item by ID")
    public ResponseEntity<ItemOpcionalResponse> getById(
        @PathVariable UUID tenantId,
        @PathVariable UUID id
    ) {
        log.debug("GET /v1/tenants/{}/itens-opcionais/{}", tenantId, id);

        validateTenantContext(tenantId);

        ItemOpcional item = itemOpcionalService.findById(tenantId, id);
        ItemOpcionalResponse response = toResponse(item);

        return ResponseEntity.ok(response);
    }

    /**
     * PUT /v1/tenants/{tenantId}/itens-opcionais/{id}
     *
     * Update an optional item.
     */
    @PutMapping("/{id}")
    @Operation(summary = "Update optional item", description = "Update an existing optional item")
    public ResponseEntity<ItemOpcionalResponse> update(
        @PathVariable UUID tenantId,
        @PathVariable UUID id,
        @Valid @RequestBody ItemOpcionalRequest request
    ) {
        log.info("PUT /v1/tenants/{}/itens-opcionais/{}", tenantId, id);

        validateTenantContext(tenantId);

        ItemOpcional updates = ItemOpcional.builder()
            .nome(request.getNome())
            .descricao(request.getDescricao())
            .precoBase(request.getPrecoBase())
            .ativo(request.getAtivo())
            .build();

        ItemOpcional item = itemOpcionalService.update(tenantId, id, updates);
        ItemOpcionalResponse response = toResponse(item);

        log.info("Optional item updated: id={}", item.getId());
        return ResponseEntity.ok(response);
    }

    /**
     * DELETE /v1/tenants/{tenantId}/itens-opcionais/{id}
     *
     * Deactivate an optional item (soft delete).
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Deactivate optional item", description = "Deactivate (soft delete) an optional item")
    public ResponseEntity<ItemOpcionalResponse> delete(
        @PathVariable UUID tenantId,
        @PathVariable UUID id
    ) {
        log.info("DELETE /v1/tenants/{}/itens-opcionais/{}", tenantId, id);

        validateTenantContext(tenantId);

        ItemOpcional item = itemOpcionalService.deactivate(tenantId, id);
        ItemOpcionalResponse response = toResponse(item);

        log.info("Optional item deactivated: id={}", item.getId());
        return ResponseEntity.ok(response);
    }

    // ===================================================================
    // Helper Methods
    // ===================================================================

    private void validateTenantContext(UUID tenantId) {
        UUID contextTenantId = TenantContext.getTenantId();
        if (!tenantId.equals(contextTenantId)) {
            log.error("Tenant mismatch: path={}, context={}", tenantId, contextTenantId);
            throw new IllegalArgumentException("Tenant ID mismatch");
        }
    }

    private ItemOpcionalResponse toResponse(ItemOpcional item) {
        return ItemOpcionalResponse.builder()
            .id(item.getId())
            .tenantId(item.getTenantId())
            .nome(item.getNome())
            .descricao(item.getDescricao())
            .precoBase(item.getPrecoBase())
            .ativo(item.getAtivo())
            .createdAt(item.getCreatedAt())
            .updatedAt(item.getUpdatedAt())
            .build();
    }
}
