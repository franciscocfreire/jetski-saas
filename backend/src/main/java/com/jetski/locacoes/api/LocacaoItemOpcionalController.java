package com.jetski.locacoes.api;

import com.jetski.locacoes.api.dto.LocacaoItemOpcionalRequest;
import com.jetski.locacoes.api.dto.LocacaoItemOpcionalResponse;
import com.jetski.locacoes.domain.ItemOpcional;
import com.jetski.locacoes.domain.LocacaoItemOpcional;
import com.jetski.locacoes.internal.ItemOpcionalService;
import com.jetski.locacoes.internal.LocacaoItemOpcionalService;
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
 * Controller: LocacaoItemOpcionalController
 *
 * REST API for managing optional items attached to rentals.
 *
 * Endpoints:
 * - GET /v1/tenants/{tenantId}/locacoes/{locacaoId}/itens - List items
 * - POST /v1/tenants/{tenantId}/locacoes/{locacaoId}/itens - Add item
 * - PUT /v1/tenants/{tenantId}/locacoes/{locacaoId}/itens/{id} - Update item
 * - DELETE /v1/tenants/{tenantId}/locacoes/{locacaoId}/itens/{id} - Remove item
 *
 * @author Jetski Team
 * @since 0.8.0
 */
@Slf4j
@RestController
@RequestMapping("/v1/tenants/{tenantId}/locacoes/{locacaoId}/itens")
@RequiredArgsConstructor
@Tag(name = "Locacao Itens Opcionais", description = "Optional items attached to rentals API")
public class LocacaoItemOpcionalController {

    private final LocacaoItemOpcionalService locacaoItemOpcionalService;
    private final ItemOpcionalService itemOpcionalService;

    /**
     * GET /v1/tenants/{tenantId}/locacoes/{locacaoId}/itens
     *
     * List all optional items for a rental.
     */
    @GetMapping
    @Operation(summary = "List rental optional items",
               description = "List all optional items attached to a rental")
    public ResponseEntity<List<LocacaoItemOpcionalResponse>> list(
        @PathVariable UUID tenantId,
        @PathVariable UUID locacaoId
    ) {
        log.debug("GET /v1/tenants/{}/locacoes/{}/itens", tenantId, locacaoId);

        validateTenantContext(tenantId);

        List<LocacaoItemOpcional> items = locacaoItemOpcionalService.listByLocacao(tenantId, locacaoId);

        List<LocacaoItemOpcionalResponse> responses = items.stream()
            .map(item -> toResponse(tenantId, item))
            .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    /**
     * POST /v1/tenants/{tenantId}/locacoes/{locacaoId}/itens
     *
     * Add an optional item to a rental.
     */
    @PostMapping
    @Operation(summary = "Add optional item to rental",
               description = "Add an optional add-on item to a rental")
    public ResponseEntity<LocacaoItemOpcionalResponse> add(
        @PathVariable UUID tenantId,
        @PathVariable UUID locacaoId,
        @Valid @RequestBody LocacaoItemOpcionalRequest request
    ) {
        log.info("POST /v1/tenants/{}/locacoes/{}/itens - item={}",
                 tenantId, locacaoId, request.getItemOpcionalId());

        validateTenantContext(tenantId);

        LocacaoItemOpcional item = locacaoItemOpcionalService.addItem(
            tenantId,
            locacaoId,
            request.getItemOpcionalId(),
            request.getValorCobrado(),
            request.getObservacao()
        );

        LocacaoItemOpcionalResponse response = toResponse(tenantId, item);

        log.info("Optional item added to rental: id={}", item.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * PUT /v1/tenants/{tenantId}/locacoes/{locacaoId}/itens/{id}
     *
     * Update an optional item in a rental.
     */
    @PutMapping("/{id}")
    @Operation(summary = "Update rental optional item",
               description = "Update the price or note of an optional item in a rental")
    public ResponseEntity<LocacaoItemOpcionalResponse> update(
        @PathVariable UUID tenantId,
        @PathVariable UUID locacaoId,
        @PathVariable UUID id,
        @RequestBody LocacaoItemOpcionalRequest request
    ) {
        log.info("PUT /v1/tenants/{}/locacoes/{}/itens/{}", tenantId, locacaoId, id);

        validateTenantContext(tenantId);

        LocacaoItemOpcional item = locacaoItemOpcionalService.updateItem(
            tenantId,
            locacaoId,
            id,
            request.getValorCobrado(),
            request.getObservacao()
        );

        LocacaoItemOpcionalResponse response = toResponse(tenantId, item);

        log.info("Optional item updated: id={}", item.getId());
        return ResponseEntity.ok(response);
    }

    /**
     * DELETE /v1/tenants/{tenantId}/locacoes/{locacaoId}/itens/{id}
     *
     * Remove an optional item from a rental.
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Remove optional item from rental",
               description = "Remove an optional item from a rental")
    public ResponseEntity<Void> remove(
        @PathVariable UUID tenantId,
        @PathVariable UUID locacaoId,
        @PathVariable UUID id
    ) {
        log.info("DELETE /v1/tenants/{}/locacoes/{}/itens/{}", tenantId, locacaoId, id);

        validateTenantContext(tenantId);

        locacaoItemOpcionalService.removeItem(tenantId, locacaoId, id);

        log.info("Optional item removed: id={}", id);
        return ResponseEntity.noContent().build();
    }

    /**
     * GET /v1/tenants/{tenantId}/locacoes/{locacaoId}/itens/total
     *
     * Calculate total value of optional items.
     */
    @GetMapping("/total")
    @Operation(summary = "Calculate optional items total",
               description = "Calculate the total value of all optional items for a rental")
    public ResponseEntity<BigDecimal> getTotal(
        @PathVariable UUID tenantId,
        @PathVariable UUID locacaoId
    ) {
        log.debug("GET /v1/tenants/{}/locacoes/{}/itens/total", tenantId, locacaoId);

        validateTenantContext(tenantId);

        BigDecimal total = locacaoItemOpcionalService.calculateTotal(locacaoId);

        return ResponseEntity.ok(total);
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

    private LocacaoItemOpcionalResponse toResponse(UUID tenantId, LocacaoItemOpcional item) {
        // Get item name from catalog
        String itemNome = null;
        try {
            ItemOpcional catalogItem = itemOpcionalService.findById(tenantId, item.getItemOpcionalId());
            itemNome = catalogItem.getNome();
        } catch (Exception e) {
            log.warn("Could not fetch item name for id={}", item.getItemOpcionalId());
        }

        return LocacaoItemOpcionalResponse.builder()
            .id(item.getId())
            .locacaoId(item.getLocacaoId())
            .itemOpcionalId(item.getItemOpcionalId())
            .itemNome(itemNome)
            .valorCobrado(item.getValorCobrado())
            .valorOriginal(item.getValorOriginal())
            .observacao(item.getObservacao())
            .negociado(item.isNegociado())
            .createdAt(item.getCreatedAt())
            .build();
    }
}
