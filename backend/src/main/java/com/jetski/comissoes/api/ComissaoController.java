package com.jetski.comissoes.api;

import com.jetski.comissoes.api.dto.AprovarComissaoRequest;
import com.jetski.comissoes.api.dto.ComissaoResponse;
import com.jetski.comissoes.api.dto.PagarComissaoRequest;
import com.jetski.comissoes.domain.Comissao;
import com.jetski.comissoes.internal.CommissionService;
import com.jetski.shared.security.TenantContext;
import com.jetski.usuarios.api.UsuarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST Controller for Commissions (Comissões)
 *
 * <p>Endpoints:</p>
 * <ul>
 *   <li>GET /comissoes - Listar comissões (filtros opcionais)</li>
 *   <li>GET /comissoes/{id} - Buscar por ID</li>
 *   <li>GET /comissoes/vendedor/{vendedorId} - Listar por vendedor</li>
 *   <li>GET /comissoes/pendentes - Listar pendentes de aprovação (GERENTE)</li>
 *   <li>GET /comissoes/aguardando-pagamento - Listar aguardando pagamento (FINANCEIRO)</li>
 *   <li>POST /comissoes/{id}/aprovar - Aprovar comissão (GERENTE)</li>
 *   <li>POST /comissoes/{id}/pagar - Marcar como paga (FINANCEIRO)</li>
 * </ul>
 *
 * @author Jetski Team
 * @since 0.7.0
 */
@RestController
@RequestMapping("/v1/tenants/{tenantId}/comissoes")
@RequiredArgsConstructor
@Slf4j
public class ComissaoController {

    private final CommissionService commissionService;
    private final UsuarioService usuarioService;

    /**
     * Buscar comissão por ID
     * Permissão: GERENTE, ADMIN_TENANT, VENDEDOR (próprias), FINANCEIRO
     */
    @GetMapping("/{id}")
    public ResponseEntity<ComissaoResponse> buscarPorId(@PathVariable UUID id) {
        UUID tenantId = TenantContext.getTenantId();

        Comissao comissao = commissionService.buscarPorId(tenantId, id);
        return ResponseEntity.ok(mapToResponse(comissao));
    }

    /**
     * Listar comissões de um vendedor
     * Permissão: GERENTE, ADMIN_TENANT, VENDEDOR (próprias)
     */
    @GetMapping("/vendedor/{vendedorId}")
    public ResponseEntity<List<ComissaoResponse>> listarPorVendedor(@PathVariable UUID vendedorId) {
        UUID tenantId = TenantContext.getTenantId();

        List<Comissao> comissoes = commissionService.listarPorVendedor(tenantId, vendedorId);
        List<ComissaoResponse> responses = comissoes.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    /**
     * Listar comissões pendentes de aprovação
     * Permissão: GERENTE, ADMIN_TENANT
     */
    @GetMapping("/pendentes")
    public ResponseEntity<List<ComissaoResponse>> listarPendentes() {
        UUID tenantId = TenantContext.getTenantId();

        List<Comissao> comissoes = commissionService.listarPendentesAprovacao(tenantId);
        List<ComissaoResponse> responses = comissoes.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    /**
     * Listar comissões aguardando pagamento
     * Permissão: FINANCEIRO, ADMIN_TENANT
     */
    @GetMapping("/aguardando-pagamento")
    public ResponseEntity<List<ComissaoResponse>> listarAguardandoPagamento() {
        UUID tenantId = TenantContext.getTenantId();

        List<Comissao> comissoes = commissionService.listarAguardandoPagamento(tenantId);
        List<ComissaoResponse> responses = comissoes.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    /**
     * Listar comissões por período (para fechamento mensal)
     * Permissão: GERENTE, FINANCEIRO, ADMIN_TENANT
     */
    @GetMapping("/periodo")
    public ResponseEntity<List<ComissaoResponse>> listarPorPeriodo(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant fim
    ) {
        UUID tenantId = TenantContext.getTenantId();

        List<Comissao> comissoes = commissionService.buscarPorPeriodo(tenantId, inicio, fim);
        List<ComissaoResponse> responses = comissoes.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    /**
     * Aprovar comissão
     * Permissão: GERENTE, ADMIN_TENANT
     */
    @PostMapping("/{id}/aprovar")
    public ResponseEntity<ComissaoResponse> aprovar(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) AprovarComissaoRequest request,
            Authentication authentication
    ) {
        UUID tenantId = TenantContext.getTenantId();
        UUID aprovadoPor = obterUsuarioId(authentication);

        Comissao comissao = commissionService.aprovarComissao(tenantId, id, aprovadoPor);
        return ResponseEntity.ok(mapToResponse(comissao));
    }

    /**
     * Marcar comissão como paga
     * Permissão: FINANCEIRO, ADMIN_TENANT
     */
    @PostMapping("/{id}/pagar")
    public ResponseEntity<ComissaoResponse> pagar(
            @PathVariable UUID id,
            @Valid @RequestBody PagarComissaoRequest request,
            Authentication authentication
    ) {
        UUID tenantId = TenantContext.getTenantId();
        UUID pagoPor = obterUsuarioId(authentication);

        Comissao comissao = commissionService.marcarComoPaga(
                tenantId,
                id,
                pagoPor,
                request.getReferenciaPagamento()
        );

        return ResponseEntity.ok(mapToResponse(comissao));
    }

    // ====================
    // Mappers & Helpers
    // ====================

    private ComissaoResponse mapToResponse(Comissao comissao) {
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
                .politicaNome(comissao.getPoliticaNome())
                .politicaNivel(comissao.getPoliticaNivel())
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
     * Obtém ID do usuário autenticado
     */
    private UUID obterUsuarioId(Authentication authentication) {
        return usuarioService.getUserIdFromAuthentication(authentication);
    }
}
