package com.jetski.locacoes.api;

import com.jetski.locacoes.api.dto.ClienteCreateRequest;
import com.jetski.locacoes.api.dto.ClienteResponse;
import com.jetski.locacoes.api.dto.ClienteUpdateRequest;
import com.jetski.locacoes.domain.Cliente;
import com.jetski.locacoes.internal.ClienteService;
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
 * Controller: Customer Management
 *
 * Endpoints for managing rental customers (list, create, update, deactivate).
 * Handles liability terms acceptance (RF03.4).
 * Available to ADMIN_TENANT, GERENTE, and OPERADOR roles.
 *
 * @author Jetski Team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/v1/tenants/{tenantId}/clientes")
@Tag(name = "Clientes", description = "Gerenciamento de clientes (locatários)")
@RequiredArgsConstructor
@Slf4j
public class ClienteController {

    private final ClienteService clienteService;

    /**
     * List all customers for a tenant.
     *
     * Returns list of customers with contact information.
     * Query parameter 'includeInactive' defaults to false.
     *
     * Requires: ADMIN_TENANT, GERENTE, or OPERADOR role
     *
     * @param tenantId Tenant UUID (from path)
     * @param includeInactive Include inactive customers (default: false)
     * @return List of customers
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE', 'OPERADOR')")
    @Operation(
        summary = "Listar clientes",
        description = "Lista todos os clientes do tenant com informações de contato. " +
                      "Por padrão, retorna apenas clientes ativos. Use ?includeInactive=true para incluir inativos."
    )
    public ResponseEntity<List<ClienteResponse>> listClientes(
        @Parameter(description = "UUID do tenant")
        @PathVariable UUID tenantId,
        @Parameter(description = "Incluir clientes inativos")
        @RequestParam(defaultValue = "false") boolean includeInactive
    ) {
        log.info("GET /v1/tenants/{}/clientes?includeInactive={}", tenantId, includeInactive);

        // Validate tenant context matches path parameter
        validateTenantContext(tenantId);

        List<Cliente> clientes = includeInactive
            ? clienteService.listAllCustomers()
            : clienteService.listActiveCustomers();

        List<ClienteResponse> response = clientes.stream()
            .map(this::toResponse)
            .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    /**
     * Get a specific customer by ID.
     *
     * Returns detailed information about a customer.
     *
     * Requires: ADMIN_TENANT, GERENTE, or OPERADOR role
     *
     * @param tenantId Tenant UUID (from path)
     * @param id Cliente UUID (from path)
     * @return Customer details
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE', 'OPERADOR')")
    @Operation(
        summary = "Obter cliente por ID",
        description = "Retorna os detalhes de um cliente específico."
    )
    public ResponseEntity<ClienteResponse> getCliente(
        @Parameter(description = "UUID do tenant")
        @PathVariable UUID tenantId,
        @Parameter(description = "UUID do cliente")
        @PathVariable UUID id
    ) {
        log.info("GET /v1/tenants/{}/clientes/{}", tenantId, id);

        // Validate tenant context matches path parameter
        validateTenantContext(tenantId);

        Cliente cliente = clienteService.findById(id);
        ClienteResponse response = toResponse(cliente);

        return ResponseEntity.ok(response);
    }

    /**
     * Create a new customer.
     *
     * Creates a new customer for rental operations.
     *
     * Validations:
     * - Name is required
     * - Contact information recommended
     *
     * Requires: ADMIN_TENANT, GERENTE, or OPERADOR role
     *
     * @param tenantId Tenant UUID (from path)
     * @param request Customer creation request
     * @return Created customer details
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE', 'OPERADOR')")
    @Operation(
        summary = "Criar novo cliente",
        description = "Cria um novo cliente para locação. " +
                      "Cliente deve aceitar o termo de responsabilidade antes da primeira locação (RF03.4)."
    )
    public ResponseEntity<ClienteResponse> createCliente(
        @Parameter(description = "UUID do tenant")
        @PathVariable UUID tenantId,
        @Valid @RequestBody ClienteCreateRequest request
    ) {
        log.info("POST /v1/tenants/{}/clientes - nome: {}", tenantId, request.getNome());

        // Validate tenant context matches path parameter
        validateTenantContext(tenantId);

        Cliente cliente = toEntity(request, tenantId);
        Cliente created = clienteService.createCliente(cliente);
        ClienteResponse response = toResponse(created);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Update an existing customer.
     *
     * Updates customer information.
     *
     * Validations:
     * - Customer must exist and be active
     * - Name cannot be blank if provided
     *
     * Requires: ADMIN_TENANT, GERENTE, or OPERADOR role
     *
     * @param tenantId Tenant UUID (from path)
     * @param id Cliente UUID (from path)
     * @param request Customer update request
     * @return Updated customer details
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE', 'OPERADOR')")
    @Operation(
        summary = "Atualizar cliente",
        description = "Atualiza as informações de um cliente existente. " +
                      "Cliente deve estar ativo."
    )
    public ResponseEntity<ClienteResponse> updateCliente(
        @Parameter(description = "UUID do tenant")
        @PathVariable UUID tenantId,
        @Parameter(description = "UUID do cliente")
        @PathVariable UUID id,
        @Valid @RequestBody ClienteUpdateRequest request
    ) {
        log.info("PUT /v1/tenants/{}/clientes/{}", tenantId, id);

        // Validate tenant context matches path parameter
        validateTenantContext(tenantId);

        Cliente updates = toEntity(request);
        Cliente updated = clienteService.updateCliente(id, updates);
        ClienteResponse response = toResponse(updated);

        return ResponseEntity.ok(response);
    }

    /**
     * Accept liability terms for customer (RF03.4).
     *
     * Customer must accept terms before their first rental.
     *
     * Requires: ADMIN_TENANT, GERENTE, or OPERADOR role
     *
     * @param tenantId Tenant UUID (from path)
     * @param id Cliente UUID (from path)
     * @return Updated customer details with termoAceite = true
     */
    @PostMapping("/{id}/accept-terms")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE', 'OPERADOR')")
    @Operation(
        summary = "Aceitar termo de responsabilidade",
        description = "Cliente aceita o termo de responsabilidade (RF03.4). " +
                      "Obrigatório antes da primeira locação."
    )
    public ResponseEntity<ClienteResponse> acceptTerms(
        @Parameter(description = "UUID do tenant")
        @PathVariable UUID tenantId,
        @Parameter(description = "UUID do cliente")
        @PathVariable UUID id
    ) {
        log.info("POST /v1/tenants/{}/clientes/{}/accept-terms", tenantId, id);

        // Validate tenant context matches path parameter
        validateTenantContext(tenantId);

        Cliente updated = clienteService.acceptTerms(id);
        ClienteResponse response = toResponse(updated);

        return ResponseEntity.ok(response);
    }

    /**
     * Deactivate a customer.
     *
     * Soft-delete: sets cliente.ativo = false.
     * Historical rentals are preserved for LGPD compliance.
     *
     * Validations:
     * - Customer must exist and be active
     *
     * Requires: ADMIN_TENANT role
     *
     * @param tenantId Tenant UUID (from path)
     * @param id Cliente UUID (from path)
     * @return Deactivated customer details
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN_TENANT')")
    @Operation(
        summary = "Desativar cliente",
        description = "Desativa um cliente (soft delete). " +
                      "Histórico de locações é preservado para conformidade com LGPD."
    )
    public ResponseEntity<ClienteResponse> deactivateCliente(
        @Parameter(description = "UUID do tenant")
        @PathVariable UUID tenantId,
        @Parameter(description = "UUID do cliente")
        @PathVariable UUID id
    ) {
        log.info("DELETE /v1/tenants/{}/clientes/{}", tenantId, id);

        // Validate tenant context matches path parameter
        validateTenantContext(tenantId);

        Cliente deactivated = clienteService.deactivateCliente(id);
        ClienteResponse response = toResponse(deactivated);

        return ResponseEntity.ok(response);
    }

    /**
     * Reactivate a customer.
     *
     * Sets cliente.ativo = true.
     *
     * Validations:
     * - Customer must exist and be inactive
     *
     * Requires: ADMIN_TENANT role
     *
     * @param tenantId Tenant UUID (from path)
     * @param id Cliente UUID (from path)
     * @return Reactivated customer details
     */
    @PostMapping("/{id}/reactivate")
    @PreAuthorize("hasRole('ADMIN_TENANT')")
    @Operation(
        summary = "Reativar cliente",
        description = "Reativa um cliente previamente desativado."
    )
    public ResponseEntity<ClienteResponse> reactivateCliente(
        @Parameter(description = "UUID do tenant")
        @PathVariable UUID tenantId,
        @Parameter(description = "UUID do cliente")
        @PathVariable UUID id
    ) {
        log.info("POST /v1/tenants/{}/clientes/{}/reactivate", tenantId, id);

        // Validate tenant context matches path parameter
        validateTenantContext(tenantId);

        Cliente reactivated = clienteService.reactivateCliente(id);
        ClienteResponse response = toResponse(reactivated);

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

    private ClienteResponse toResponse(Cliente cliente) {
        return ClienteResponse.builder()
            .id(cliente.getId())
            .tenantId(cliente.getTenantId())
            .nome(cliente.getNome())
            .documento(cliente.getDocumento())
            .dataNascimento(cliente.getDataNascimento())
            .genero(cliente.getGenero())
            .email(cliente.getEmail())
            .telefone(cliente.getTelefone())
            .whatsapp(cliente.getWhatsapp())
            .enderecoJson(cliente.getEnderecoJson())
            .termoAceite(cliente.getTermoAceite())
            .ativo(cliente.getAtivo())
            .createdAt(cliente.getCreatedAt())
            .updatedAt(cliente.getUpdatedAt())
            .build();
    }

    private Cliente toEntity(ClienteCreateRequest request, UUID tenantId) {
        return Cliente.builder()
            .tenantId(tenantId)
            .nome(request.getNome())
            .documento(request.getDocumento())
            .dataNascimento(request.getDataNascimento())
            .genero(request.getGenero())
            .email(request.getEmail())
            .telefone(request.getTelefone())
            .whatsapp(request.getWhatsapp())
            .enderecoJson(request.getEnderecoJson())
            .termoAceite(request.getTermoAceite() != null ? request.getTermoAceite() : false)
            .ativo(true)
            .build();
    }

    private Cliente toEntity(ClienteUpdateRequest request) {
        return Cliente.builder()
            .nome(request.getNome())
            .documento(request.getDocumento())
            .dataNascimento(request.getDataNascimento())
            .genero(request.getGenero())
            .email(request.getEmail())
            .telefone(request.getTelefone())
            .whatsapp(request.getWhatsapp())
            .enderecoJson(request.getEnderecoJson())
            .termoAceite(request.getTermoAceite())
            .build();
    }
}
