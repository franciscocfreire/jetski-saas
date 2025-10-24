package com.jetski.locacoes.api;

import com.jetski.locacoes.api.dto.VendedorCreateRequest;
import com.jetski.locacoes.api.dto.VendedorResponse;
import com.jetski.locacoes.api.dto.VendedorUpdateRequest;
import com.jetski.locacoes.domain.Vendedor;
import com.jetski.locacoes.internal.VendedorService;
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
 * Controller: Sellers/Partners Management
 *
 * Endpoints for managing sellers and partners (list, create, update, deactivate).
 * Handles commission configuration (RF08, RN04).
 * Available to ADMIN_TENANT and GERENTE roles.
 *
 * @author Jetski Team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/v1/tenants/{tenantId}/vendedores")
@Tag(name = "Vendedores", description = "Gerenciamento de vendedores e parceiros")
@RequiredArgsConstructor
@Slf4j
public class VendedorController {

    private final VendedorService vendedorService;

    /**
     * List all sellers/partners for a tenant.
     *
     * Returns list of sellers with commission configuration.
     * Query parameter 'includeInactive' defaults to false.
     *
     * Requires: ADMIN_TENANT or GERENTE role
     *
     * @param tenantId Tenant UUID (from path)
     * @param includeInactive Include inactive sellers (default: false)
     * @return List of sellers
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE')")
    @Operation(
        summary = "Listar vendedores e parceiros",
        description = "Lista todos os vendedores e parceiros do tenant com configurações de comissão. " +
                      "Por padrão, retorna apenas vendedores ativos. Use ?includeInactive=true para incluir inativos."
    )
    public ResponseEntity<List<VendedorResponse>> listVendedores(
        @Parameter(description = "UUID do tenant")
        @PathVariable UUID tenantId,
        @Parameter(description = "Incluir vendedores inativos")
        @RequestParam(defaultValue = "false") boolean includeInactive
    ) {
        log.info("GET /v1/tenants/{}/vendedores?includeInactive={}", tenantId, includeInactive);

        // Validate tenant context matches path parameter
        validateTenantContext(tenantId);

        List<Vendedor> vendedores = includeInactive
            ? vendedorService.listAllSellers()
            : vendedorService.listActiveSellers();

        List<VendedorResponse> response = vendedores.stream()
            .map(this::toResponse)
            .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    /**
     * Get a specific seller by ID.
     *
     * Returns detailed information about a seller/partner.
     *
     * Requires: ADMIN_TENANT or GERENTE role
     *
     * @param tenantId Tenant UUID (from path)
     * @param id Vendedor UUID (from path)
     * @return Seller details
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE')")
    @Operation(
        summary = "Obter vendedor por ID",
        description = "Retorna os detalhes de um vendedor ou parceiro específico."
    )
    public ResponseEntity<VendedorResponse> getVendedor(
        @Parameter(description = "UUID do tenant")
        @PathVariable UUID tenantId,
        @Parameter(description = "UUID do vendedor")
        @PathVariable UUID id
    ) {
        log.info("GET /v1/tenants/{}/vendedores/{}", tenantId, id);

        // Validate tenant context matches path parameter
        validateTenantContext(tenantId);

        Vendedor vendedor = vendedorService.findById(id);
        VendedorResponse response = toResponse(vendedor);

        return ResponseEntity.ok(response);
    }

    /**
     * Create a new seller/partner.
     *
     * Creates a new seller with commission configuration.
     *
     * Validations:
     * - Name is required
     * - Tipo must be INTERNO or PARCEIRO
     *
     * Requires: ADMIN_TENANT or GERENTE role
     *
     * @param tenantId Tenant UUID (from path)
     * @param request Seller creation request
     * @return Created seller details
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE')")
    @Operation(
        summary = "Criar novo vendedor/parceiro",
        description = "Cria um novo vendedor ou parceiro com configuração de comissão (RF08, RN04). " +
                      "Tipo deve ser INTERNO (funcionário) ou PARCEIRO (externo)."
    )
    public ResponseEntity<VendedorResponse> createVendedor(
        @Parameter(description = "UUID do tenant")
        @PathVariable UUID tenantId,
        @Valid @RequestBody VendedorCreateRequest request
    ) {
        log.info("POST /v1/tenants/{}/vendedores - nome: {}, tipo: {}",
                 tenantId, request.getNome(), request.getTipo());

        // Validate tenant context matches path parameter
        validateTenantContext(tenantId);

        Vendedor vendedor = toEntity(request, tenantId);
        Vendedor created = vendedorService.createVendedor(vendedor);
        VendedorResponse response = toResponse(created);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Update an existing seller/partner.
     *
     * Updates seller information and commission configuration.
     *
     * Validations:
     * - Seller must exist and be active
     * - Name cannot be blank if provided
     *
     * Requires: ADMIN_TENANT or GERENTE role
     *
     * @param tenantId Tenant UUID (from path)
     * @param id Vendedor UUID (from path)
     * @param request Seller update request
     * @return Updated seller details
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE')")
    @Operation(
        summary = "Atualizar vendedor/parceiro",
        description = "Atualiza as informações de um vendedor ou parceiro existente. " +
                      "Vendedor deve estar ativo."
    )
    public ResponseEntity<VendedorResponse> updateVendedor(
        @Parameter(description = "UUID do tenant")
        @PathVariable UUID tenantId,
        @Parameter(description = "UUID do vendedor")
        @PathVariable UUID id,
        @Valid @RequestBody VendedorUpdateRequest request
    ) {
        log.info("PUT /v1/tenants/{}/vendedores/{}", tenantId, id);

        // Validate tenant context matches path parameter
        validateTenantContext(tenantId);

        Vendedor updates = toEntity(request);
        Vendedor updated = vendedorService.updateVendedor(id, updates);
        VendedorResponse response = toResponse(updated);

        return ResponseEntity.ok(response);
    }

    /**
     * Deactivate a seller/partner.
     *
     * Soft-delete: sets vendedor.ativo = false.
     *
     * Validations:
     * - Seller must exist and be active
     *
     * Requires: ADMIN_TENANT role
     *
     * @param tenantId Tenant UUID (from path)
     * @param id Vendedor UUID (from path)
     * @return Deactivated seller details
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN_TENANT')")
    @Operation(
        summary = "Desativar vendedor/parceiro",
        description = "Desativa um vendedor ou parceiro (soft delete). " +
                      "Vendedor não poderá mais receber novas comissões."
    )
    public ResponseEntity<VendedorResponse> deactivateVendedor(
        @Parameter(description = "UUID do tenant")
        @PathVariable UUID tenantId,
        @Parameter(description = "UUID do vendedor")
        @PathVariable UUID id
    ) {
        log.info("DELETE /v1/tenants/{}/vendedores/{}", tenantId, id);

        // Validate tenant context matches path parameter
        validateTenantContext(tenantId);

        Vendedor deactivated = vendedorService.deactivateVendedor(id);
        VendedorResponse response = toResponse(deactivated);

        return ResponseEntity.ok(response);
    }

    /**
     * Reactivate a seller/partner.
     *
     * Sets vendedor.ativo = true.
     *
     * Validations:
     * - Seller must exist and be inactive
     *
     * Requires: ADMIN_TENANT role
     *
     * @param tenantId Tenant UUID (from path)
     * @param id Vendedor UUID (from path)
     * @return Reactivated seller details
     */
    @PostMapping("/{id}/reactivate")
    @PreAuthorize("hasRole('ADMIN_TENANT')")
    @Operation(
        summary = "Reativar vendedor/parceiro",
        description = "Reativa um vendedor ou parceiro previamente desativado."
    )
    public ResponseEntity<VendedorResponse> reactivateVendedor(
        @Parameter(description = "UUID do tenant")
        @PathVariable UUID tenantId,
        @Parameter(description = "UUID do vendedor")
        @PathVariable UUID id
    ) {
        log.info("POST /v1/tenants/{}/vendedores/{}/reactivate", tenantId, id);

        // Validate tenant context matches path parameter
        validateTenantContext(tenantId);

        Vendedor reactivated = vendedorService.reactivateVendedor(id);
        VendedorResponse response = toResponse(reactivated);

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

    private VendedorResponse toResponse(Vendedor vendedor) {
        return VendedorResponse.builder()
            .id(vendedor.getId())
            .tenantId(vendedor.getTenantId())
            .nome(vendedor.getNome())
            .documento(vendedor.getDocumento())
            .tipo(vendedor.getTipo())
            .regraComissaoJson(vendedor.getRegraComissaoJson())
            .ativo(vendedor.getAtivo())
            .createdAt(vendedor.getCreatedAt())
            .updatedAt(vendedor.getUpdatedAt())
            .build();
    }

    private Vendedor toEntity(VendedorCreateRequest request, UUID tenantId) {
        return Vendedor.builder()
            .tenantId(tenantId)
            .nome(request.getNome())
            .documento(request.getDocumento())
            .tipo(request.getTipo())
            .regraComissaoJson(request.getRegraComissaoJson())
            .ativo(true)
            .build();
    }

    private Vendedor toEntity(VendedorUpdateRequest request) {
        return Vendedor.builder()
            .nome(request.getNome())
            .documento(request.getDocumento())
            .tipo(request.getTipo())
            .regraComissaoJson(request.getRegraComissaoJson())
            .build();
    }
}
