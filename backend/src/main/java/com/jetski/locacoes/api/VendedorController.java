package com.jetski.locacoes.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jetski.bonus.domain.BonusVendedor;
import com.jetski.bonus.internal.BonusService;
import com.jetski.comissoes.domain.Comissao;
import com.jetski.comissoes.internal.CommissionService;
import com.jetski.comissoes.api.dto.ComissaoResponse;
import com.jetski.comissoes.internal.repository.ComissaoRepository;
import com.jetski.locacoes.api.dto.*;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
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
    private final CommissionService commissionService;
    private final ComissaoRepository comissaoRepository;
    private final BonusService bonusService;
    private final ObjectMapper objectMapper;

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

    // ========== NOVOS ENDPOINTS: RESUMO E PAGAMENTO EM LOTE ==========

    /**
     * List sellers with commission summary.
     *
     * Returns list of sellers with total pending, approved, and paid commissions.
     *
     * Requires: ADMIN_TENANT, GERENTE, or FINANCEIRO role
     *
     * @param tenantId Tenant UUID (from path)
     * @param includeInactive Include inactive sellers (default: false)
     * @return List of sellers with commission summary
     */
    @GetMapping("/resumo")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE', 'FINANCEIRO')")
    @Operation(
        summary = "Listar vendedores com resumo de comissões",
        description = "Lista vendedores com totais de comissões pendentes, aprovadas e pagas."
    )
    public ResponseEntity<List<VendedorResumoResponse>> listVendedoresWithSummary(
        @Parameter(description = "UUID do tenant")
        @PathVariable UUID tenantId,
        @Parameter(description = "Incluir vendedores inativos")
        @RequestParam(defaultValue = "false") boolean includeInactive
    ) {
        log.info("GET /v1/tenants/{}/vendedores/resumo?includeInactive={}", tenantId, includeInactive);

        validateTenantContext(tenantId);

        List<VendedorResumoResponse> response = vendedorService.listSellersWithSummary(tenantId, includeInactive);

        return ResponseEntity.ok(response);
    }

    /**
     * Get seller details with commission totals and bonus status.
     *
     * Returns detailed information about a seller including:
     * - Basic info (name, email, phone, type)
     * - Commission totals (pending, approved, paid)
     * - Bonus status (eligible, progress towards next bonus)
     *
     * Requires: ADMIN_TENANT, GERENTE, or FINANCEIRO role
     *
     * @param tenantId Tenant UUID (from path)
     * @param id Vendedor UUID (from path)
     * @return Seller details with commission summary and bonus status
     */
    @GetMapping("/{id}/detalhes")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE', 'FINANCEIRO')")
    @Operation(
        summary = "Obter detalhes do vendedor com resumo de comissões",
        description = "Retorna detalhes completos do vendedor com totais de comissões e status do bonus."
    )
    public ResponseEntity<VendedorDetalheResponse> getVendedorDetails(
        @Parameter(description = "UUID do tenant")
        @PathVariable UUID tenantId,
        @Parameter(description = "UUID do vendedor")
        @PathVariable UUID id
    ) {
        log.info("GET /v1/tenants/{}/vendedores/{}/detalhes", tenantId, id);

        validateTenantContext(tenantId);

        VendedorDetalheResponse response = vendedorService.getSellerDetails(tenantId, id);

        return ResponseEntity.ok(response);
    }

    /**
     * Pay all approved commissions for a seller in bulk.
     *
     * Marks all APPROVED commissions for the seller as PAID.
     * Requires a payment reference (e.g., PIX-2024-001).
     *
     * Requires: FINANCEIRO role
     *
     * @param tenantId Tenant UUID (from path)
     * @param vendedorId Vendedor UUID (from path)
     * @param request Payment details (reference, observation)
     * @param jwt JWT token for user identification
     * @return Payment result with total amount and number of commissions paid
     */
    @PostMapping("/{vendedorId}/comissoes/pagar-lote")
    @PreAuthorize("hasRole('FINANCEIRO')")
    @Operation(
        summary = "Pagar comissões em lote",
        description = "Paga todas as comissões aprovadas de um vendedor de uma vez. " +
                      "Requer referência de pagamento (ex: PIX-2024-001)."
    )
    public ResponseEntity<PagamentoLoteResponse> pagarComissoesEmLote(
        @Parameter(description = "UUID do tenant")
        @PathVariable UUID tenantId,
        @Parameter(description = "UUID do vendedor")
        @PathVariable UUID vendedorId,
        @Valid @RequestBody PagamentoLoteRequest request,
        @AuthenticationPrincipal Jwt jwt
    ) {
        log.info("POST /v1/tenants/{}/vendedores/{}/comissoes/pagar-lote", tenantId, vendedorId);

        validateTenantContext(tenantId);

        // Extrair user ID do JWT
        UUID userId = UUID.fromString(jwt.getSubject());

        // Executar pagamento em lote
        CommissionService.PagamentoLoteResult result = commissionService.pagarComissoesVendedor(
                tenantId, vendedorId, userId, request.getReferenciaPagamento()
        );

        // Construir resposta
        PagamentoLoteResponse response = PagamentoLoteResponse.builder()
                .vendedorId(result.vendedorId())
                .nomeVendedor(result.nomeVendedor())
                .qtdComissoesPagas(result.qtdComissoesPagas())
                .valorTotalPago(result.valorTotalPago())
                .dataHoraPagamento(result.dataHoraPagamento())
                .referenciaPagamento(result.referenciaPagamento())
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * List commissions for a seller.
     *
     * Returns all commissions for the specified seller.
     * Can filter by status (PENDENTE, APROVADA, PAGA, CANCELADA).
     *
     * Requires: ADMIN_TENANT, GERENTE, or FINANCEIRO role
     *
     * @param tenantId Tenant UUID (from path)
     * @param vendedorId Vendedor UUID (from path)
     * @param status Optional status filter
     * @return List of commissions
     */
    @GetMapping("/{vendedorId}/comissoes")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE', 'FINANCEIRO')")
    @Operation(
        summary = "Listar comissões do vendedor",
        description = "Lista todas as comissões de um vendedor. Pode filtrar por status."
    )
    public ResponseEntity<List<ComissaoResponse>> listComissoes(
        @Parameter(description = "UUID do tenant")
        @PathVariable UUID tenantId,
        @Parameter(description = "UUID do vendedor")
        @PathVariable UUID vendedorId,
        @Parameter(description = "Filtrar por status (PENDENTE, APROVADA, PAGA, CANCELADA)")
        @RequestParam(required = false) String status
    ) {
        log.info("GET /v1/tenants/{}/vendedores/{}/comissoes?status={}", tenantId, vendedorId, status);

        validateTenantContext(tenantId);

        List<Comissao> comissoes;
        if (status != null && !status.isBlank()) {
            comissoes = comissaoRepository.findByTenantIdAndVendedorIdAndStatusOrderByCreatedAtDesc(
                tenantId, vendedorId, com.jetski.comissoes.domain.StatusComissao.valueOf(status)
            );
        } else {
            comissoes = comissaoRepository.findByTenantIdAndVendedorIdOrderByCreatedAtDesc(tenantId, vendedorId);
        }

        List<ComissaoResponse> response = comissoes.stream()
            .map(this::toComissaoResponse)
            .toList();

        return ResponseEntity.ok(response);
    }

    /**
     * List bonuses for a seller.
     *
     * Returns all bonuses achieved by the seller.
     *
     * Requires: ADMIN_TENANT, GERENTE, or FINANCEIRO role
     *
     * @param tenantId Tenant UUID (from path)
     * @param vendedorId Vendedor UUID (from path)
     * @return List of bonuses
     */
    @GetMapping("/{vendedorId}/bonus")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE', 'FINANCEIRO')")
    @Operation(
        summary = "Listar bônus do vendedor",
        description = "Lista todos os bônus conquistados pelo vendedor."
    )
    public ResponseEntity<List<BonusVendedorResponse>> listBonus(
        @Parameter(description = "UUID do tenant")
        @PathVariable UUID tenantId,
        @Parameter(description = "UUID do vendedor")
        @PathVariable UUID vendedorId
    ) {
        log.info("GET /v1/tenants/{}/vendedores/{}/bonus", tenantId, vendedorId);

        validateTenantContext(tenantId);

        List<BonusVendedor> bonuses = bonusService.listarPorVendedor(tenantId, vendedorId);

        List<BonusVendedorResponse> response = bonuses.stream()
            .map(this::toBonusResponse)
            .toList();

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
        BigDecimal comissaoPercentual = extractComissaoPercentual(vendedor.getRegraComissaoJson());

        return VendedorResponse.builder()
            .id(vendedor.getId())
            .tenantId(vendedor.getTenantId())
            .nome(vendedor.getNome())
            .documento(vendedor.getDocumento())
            .email(vendedor.getEmail())
            .telefone(vendedor.getTelefone())
            .chavePix(vendedor.getChavePix())
            .tipoChavePix(vendedor.getTipoChavePix())
            .tipo(vendedor.getTipo())
            .comissaoPercentual(comissaoPercentual)
            .regraComissaoJson(vendedor.getRegraComissaoJson())
            .diariaBase(vendedor.getDiariaBase())
            .ativo(vendedor.getAtivo())
            .createdAt(vendedor.getCreatedAt())
            .updatedAt(vendedor.getUpdatedAt())
            .build();
    }

    private Vendedor toEntity(VendedorCreateRequest request, UUID tenantId) {
        String regraJson = request.getRegraComissaoJson();
        // If comissaoPercentual is provided and no custom JSON, create default JSON
        if ((regraJson == null || regraJson.isBlank()) && request.getComissaoPercentual() != null) {
            regraJson = createDefaultRegraJson(request.getComissaoPercentual());
        }

        return Vendedor.builder()
            .tenantId(tenantId)
            .nome(request.getNome())
            .documento(request.getDocumento())
            .email(request.getEmail())
            .telefone(request.getTelefone())
            .chavePix(request.getChavePix())
            .tipoChavePix(request.getTipoChavePix())
            .tipo(request.getTipo() != null ? request.getTipo() : com.jetski.locacoes.domain.VendedorTipo.INTERNO)
            .regraComissaoJson(regraJson)
            .diariaBase(request.getDiariaBase())
            .ativo(true)
            .build();
    }

    private Vendedor toEntity(VendedorUpdateRequest request) {
        String regraJson = request.getRegraComissaoJson();
        // If comissaoPercentual is provided and no custom JSON, create default JSON
        if ((regraJson == null || regraJson.isBlank()) && request.getComissaoPercentual() != null) {
            regraJson = createDefaultRegraJson(request.getComissaoPercentual());
        }

        return Vendedor.builder()
            .nome(request.getNome())
            .documento(request.getDocumento())
            .email(request.getEmail())
            .telefone(request.getTelefone())
            .chavePix(request.getChavePix())
            .tipoChavePix(request.getTipoChavePix())
            .tipo(request.getTipo())
            .regraComissaoJson(regraJson)
            .diariaBase(request.getDiariaBase())
            .build();
    }

    /**
     * Extract percentual_padrao from regraComissaoJson.
     * Returns null if JSON is invalid or field not found.
     */
    private BigDecimal extractComissaoPercentual(String regraJson) {
        if (regraJson == null || regraJson.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(regraJson);
            JsonNode percentualNode = node.get("percentual_padrao");
            if (percentualNode != null && percentualNode.isNumber()) {
                return BigDecimal.valueOf(percentualNode.asDouble());
            }
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse regraComissaoJson: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Create default regraComissaoJson with just percentual_padrao.
     */
    private String createDefaultRegraJson(BigDecimal percentual) {
        try {
            return objectMapper.writeValueAsString(
                java.util.Map.of("percentual_padrao", percentual)
            );
        } catch (JsonProcessingException e) {
            log.error("Failed to create regraComissaoJson: {}", e.getMessage());
            return "{\"percentual_padrao\": " + percentual + "}";
        }
    }

    /**
     * Convert Comissao entity to response DTO.
     */
    private ComissaoResponse toComissaoResponse(Comissao comissao) {
        return ComissaoResponse.builder()
            .id(comissao.getId())
            .locacaoId(comissao.getLocacaoId())
            .vendedorId(comissao.getVendedorId())
            .politicaId(comissao.getPoliticaId())
            .status(comissao.getStatus())
            .dataLocacao(comissao.getDataLocacao())
            .valorTotalLocacao(comissao.getValorTotalLocacao())
            .valorCombustivel(comissao.getValorCombustivel())
            .valorMultas(comissao.getValorMultas())
            .valorTaxas(comissao.getValorTaxas())
            .valorComissionavel(comissao.getValorComissionavel())
            .valorComissao(comissao.getValorComissao())
            .tipoComissao(comissao.getTipoComissao())
            .percentualAplicado(comissao.getPercentualAplicado())
            .vendaAcimaPrecoBase(comissao.getVendaAcimaPrecoBase())
            .aprovadoPor(comissao.getAprovadoPor())
            .aprovadoEm(comissao.getAprovadoEm())
            .pagoPor(comissao.getPagoPor())
            .pagoEm(comissao.getPagoEm())
            .referenciaPagamento(comissao.getReferenciaPagamento())
            .createdAt(comissao.getCreatedAt())
            .updatedAt(comissao.getUpdatedAt())
            .build();
    }

    /**
     * Convert BonusVendedor entity to response DTO.
     */
    private BonusVendedorResponse toBonusResponse(BonusVendedor bonus) {
        return BonusVendedorResponse.builder()
            .id(bonus.getId())
            .vendedorId(bonus.getVendedorId())
            .metaAtingida(bonus.getMetaAtingida())
            .valorBonus(bonus.getValorBonus())
            .status(bonus.getStatus())
            .aprovadoPor(bonus.getAprovadoPor())
            .aprovadoEm(bonus.getAprovadoEm())
            .pagoPor(bonus.getPagoPor())
            .pagoEm(bonus.getPagoEm())
            .referenciaPagamento(bonus.getReferenciaPagamento())
            .createdAt(bonus.getCreatedAt())
            .build();
    }
}
