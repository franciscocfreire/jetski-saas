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
    private final com.jetski.locacoes.internal.gru.GruClient gruClient;
    private final com.jetski.locacoes.internal.ClienteAnexoService anexoService;

    @PutMapping("/{id}/anexos/{tipo}")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE', 'OPERADOR')")
    @Operation(summary = "Enviar anexo do cliente (identidade, comprovante, selfie)")
    public ResponseEntity<com.jetski.locacoes.api.dto.AnexoResumo> uploadAnexo(
        @PathVariable UUID tenantId,
        @PathVariable UUID id,
        @PathVariable String tipo,
        @org.springframework.web.bind.annotation.RequestBody
            @jakarta.validation.Valid com.jetski.locacoes.api.dto.AnexoUploadRequest req
    ) {
        validateTenantContext(tenantId);
        com.jetski.locacoes.domain.ClienteAnexo.Tipo t =
            com.jetski.locacoes.domain.ClienteAnexo.Tipo.valueOf(tipo.toUpperCase());
        var a = anexoService.salvar(id, t, req.conteudoBase64());
        return ResponseEntity.ok(new com.jetski.locacoes.api.dto.AnexoResumo(
            a.getTipo().name(), a.getContentType(), a.getUpdatedAt()));
    }

    @GetMapping("/{id}/anexos/{tipo}/download")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE', 'OPERADOR')")
    @Operation(summary = "Baixar a imagem de um anexo do cliente (streaming)")
    public ResponseEntity<byte[]> baixarAnexo(
        @PathVariable UUID tenantId,
        @PathVariable UUID id,
        @PathVariable String tipo
    ) {
        validateTenantContext(tenantId);
        var t = com.jetski.locacoes.domain.ClienteAnexo.Tipo.valueOf(tipo.toUpperCase());
        var anexo = anexoService.buscar(id, t)
            .orElseThrow(() -> new com.jetski.shared.exception.NotFoundException("Anexo não encontrado"));
        byte[] bytes = anexoService.lerImagem(anexo);
        String ct = anexo.getContentType() != null ? anexo.getContentType() : "image/jpeg";
        return ResponseEntity.ok()
            .contentType(org.springframework.http.MediaType.parseMediaType(ct))
            .body(bytes);
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/{id}/anexos/{tipo}")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE', 'OPERADOR')")
    @Operation(summary = "Apagar um anexo do cliente (identidade, comprovante, selfie)")
    public ResponseEntity<Void> deletarAnexo(
        @PathVariable UUID tenantId,
        @PathVariable UUID id,
        @PathVariable String tipo
    ) {
        validateTenantContext(tenantId);
        var t = com.jetski.locacoes.domain.ClienteAnexo.Tipo.valueOf(tipo.toUpperCase());
        anexoService.deletar(id, t);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/anexos")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE', 'OPERADOR')")
    @Operation(summary = "Listar anexos presentes do cliente")
    public ResponseEntity<java.util.List<com.jetski.locacoes.api.dto.AnexoResumo>> listarAnexos(
        @PathVariable UUID tenantId,
        @PathVariable UUID id
    ) {
        validateTenantContext(tenantId);
        var lista = anexoService.listar(id).stream()
            .map(a -> new com.jetski.locacoes.api.dto.AnexoResumo(
                a.getTipo().name(), a.getContentType(), a.getUpdatedAt()))
            .toList();
        return ResponseEntity.ok(lista);
    }

    @GetMapping("/consulta-marinha")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE', 'OPERADOR')")
    @Operation(
        summary = "Consultar nome do contribuinte por CPF na Marinha",
        description = "Pré-preenchimento do balcão: consulta o nome pelo CPF na base da Marinha " +
                      "(objContribuinte). Devolve nome=null se não encontrado."
    )
    public ResponseEntity<com.jetski.locacoes.api.dto.ConsultaCpfMarinhaResponse> consultarCpfMarinha(
        @PathVariable UUID tenantId,
        @RequestParam String cpf
    ) {
        validateTenantContext(tenantId);
        String nome = gruClient.consultarNomePorCpf(cpf);
        return ResponseEntity.ok(new com.jetski.locacoes.api.dto.ConsultaCpfMarinhaResponse(nome));
    }

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
        @RequestParam(defaultValue = "false") boolean includeInactive,
        @Parameter(description = "Filtrar por CPF/documento (dedupe de balcão)")
        @RequestParam(required = false) String cpf
    ) {
        log.info("GET /v1/tenants/{}/clientes?includeInactive={}&cpf={}", tenantId, includeInactive, cpf);

        // Validate tenant context matches path parameter
        validateTenantContext(tenantId);

        // Busca por CPF (dedupe): retorna o match (ou vazio)
        if (cpf != null && !cpf.isBlank()) {
            List<ClienteResponse> match = clienteService.buscarPorDocumento(cpf).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
            return ResponseEntity.ok(match);
        }

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
     * Cria uma pré-conta de cliente no balcão (atendimento assistido).
     * Origem=BALCAO, status=PRE_CONTA, com dedupe por CPF e proteção anti-takeover.
     */
    @PostMapping("/pre-conta")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE', 'OPERADOR')")
    @Operation(
        summary = "Criar pré-conta (balcão)",
        description = "Registra um cliente sem login (origem=BALCAO, status PRE_CONTA). " +
                      "Faz dedupe por CPF; bloqueia se já houver conta ATIVA (exige OTP)."
    )
    public ResponseEntity<ClienteResponse> criarPreConta(
        @Parameter(description = "UUID do tenant")
        @PathVariable UUID tenantId,
        @Valid @RequestBody ClienteCreateRequest request
    ) {
        log.info("POST /v1/tenants/{}/clientes/pre-conta - nome: {}", tenantId, request.getNome());

        validateTenantContext(tenantId);

        Cliente cliente = toEntity(request, tenantId);
        Cliente preConta = clienteService.criarPreConta(cliente);

        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(preConta));
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
            .rg(cliente.getRg())
            .orgaoEmissor(cliente.getOrgaoEmissor())
            .nacionalidade(cliente.getNacionalidade())
            .naturalidade(cliente.getNaturalidade())
            .estrangeiro(cliente.getEstrangeiro())
            .dataNascimento(cliente.getDataNascimento())
            .genero(cliente.getGenero())
            .email(cliente.getEmail())
            .telefone(cliente.getTelefone())
            .whatsapp(cliente.getWhatsapp())
            .enderecoJson(cliente.getEnderecoJson())
            .termoAceite(cliente.getTermoAceite())
            .origem(cliente.getOrigem() != null ? cliente.getOrigem().name() : null)
            .statusConta(cliente.getStatusConta() != null ? cliente.getStatusConta().name() : null)
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
            .rg(request.getRg())
            .orgaoEmissor(request.getOrgaoEmissor())
            .nacionalidade(request.getNacionalidade())
            .naturalidade(request.getNaturalidade())
            .estrangeiro(request.getEstrangeiro() != null ? request.getEstrangeiro() : false)
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
            .rg(request.getRg())
            .orgaoEmissor(request.getOrgaoEmissor())
            .nacionalidade(request.getNacionalidade())
            .naturalidade(request.getNaturalidade())
            .estrangeiro(request.getEstrangeiro() != null ? request.getEstrangeiro() : false)
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
