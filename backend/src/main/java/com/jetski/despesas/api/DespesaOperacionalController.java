package com.jetski.despesas.api;

import com.jetski.despesas.api.dto.DespesaOperacionalRequest;
import com.jetski.despesas.api.dto.DespesaOperacionalResponse;
import com.jetski.despesas.api.dto.PagarDespesaRequest;
import com.jetski.despesas.api.dto.RejeitarDespesaRequest;
import com.jetski.despesas.domain.CategoriaDespesa;
import com.jetski.despesas.domain.DespesaOperacional;
import com.jetski.despesas.domain.StatusDespesa;
import com.jetski.despesas.internal.DespesaOperacionalService;
import com.jetski.shared.security.TenantContext;
import com.jetski.usuarios.api.UsuarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST Controller for Despesas Operacionais (Operational Expenses)
 *
 * <p>Endpoints:</p>
 * <ul>
 *   <li>POST /despesas-operacionais - Criar despesa</li>
 *   <li>GET /despesas-operacionais/{id} - Buscar por ID</li>
 *   <li>PUT /despesas-operacionais/{id} - Atualizar</li>
 *   <li>DELETE /despesas-operacionais/{id} - Excluir</li>
 *   <li>GET /despesas-operacionais - Listar por periodo</li>
 *   <li>GET /despesas-operacionais/dia/{data} - Listar por dia</li>
 *   <li>GET /despesas-operacionais/categoria/{categoria} - Listar por categoria</li>
 *   <li>GET /despesas-operacionais/pendentes - Listar pendentes de aprovacao</li>
 *   <li>GET /despesas-operacionais/aguardando-pagamento - Listar aguardando pagamento</li>
 *   <li>POST /despesas-operacionais/{id}/aprovar - Aprovar</li>
 *   <li>POST /despesas-operacionais/{id}/rejeitar - Rejeitar</li>
 *   <li>POST /despesas-operacionais/{id}/pagar - Marcar como paga</li>
 * </ul>
 *
 * @author Jetski Team
 * @since 0.9.0
 */
@RestController
@RequestMapping("/v1/tenants/{tenantId}/despesas-operacionais")
@RequiredArgsConstructor
@Slf4j
public class DespesaOperacionalController {

    private final DespesaOperacionalService service;
    private final UsuarioService usuarioService;

    // ========== CRUD ==========

    /**
     * Criar nova despesa operacional
     * Permissao: OPERADOR, GERENTE, ADMIN_TENANT
     */
    @PostMapping
    public ResponseEntity<DespesaOperacionalResponse> criar(
            @Valid @RequestBody DespesaOperacionalRequest request,
            Authentication authentication
    ) {
        UUID tenantId = TenantContext.getTenantId();

        DespesaOperacional despesa = service.criar(tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(mapToResponse(despesa));
    }

    /**
     * Buscar despesa por ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<DespesaOperacionalResponse> buscarPorId(@PathVariable UUID id) {
        UUID tenantId = TenantContext.getTenantId();

        DespesaOperacional despesa = service.buscarPorId(tenantId, id);
        return ResponseEntity.ok(mapToResponse(despesa));
    }

    /**
     * Atualizar despesa (somente se PENDENTE)
     */
    @PutMapping("/{id}")
    public ResponseEntity<DespesaOperacionalResponse> atualizar(
            @PathVariable UUID id,
            @Valid @RequestBody DespesaOperacionalRequest request
    ) {
        UUID tenantId = TenantContext.getTenantId();

        DespesaOperacional despesa = service.atualizar(tenantId, id, request);
        return ResponseEntity.ok(mapToResponse(despesa));
    }

    /**
     * Excluir despesa (somente se PENDENTE)
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable UUID id) {
        UUID tenantId = TenantContext.getTenantId();

        service.excluir(tenantId, id);
        return ResponseEntity.noContent().build();
    }

    // ========== Listagens ==========

    /**
     * Listar despesas por periodo
     */
    @GetMapping
    public ResponseEntity<List<DespesaOperacionalResponse>> listarPorPeriodo(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim
    ) {
        UUID tenantId = TenantContext.getTenantId();

        List<DespesaOperacional> despesas = service.listarPorPeriodo(tenantId, dataInicio, dataFim);
        return ResponseEntity.ok(mapToResponseList(despesas));
    }

    /**
     * Listar despesas de um dia especifico
     */
    @GetMapping("/dia/{data}")
    public ResponseEntity<List<DespesaOperacionalResponse>> listarPorDia(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data
    ) {
        UUID tenantId = TenantContext.getTenantId();

        List<DespesaOperacional> despesas = service.listarPorDia(tenantId, data);
        return ResponseEntity.ok(mapToResponseList(despesas));
    }

    /**
     * Listar despesas por categoria
     */
    @GetMapping("/categoria/{categoria}")
    public ResponseEntity<List<DespesaOperacionalResponse>> listarPorCategoria(
            @PathVariable CategoriaDespesa categoria
    ) {
        UUID tenantId = TenantContext.getTenantId();

        List<DespesaOperacional> despesas = service.listarPorCategoria(tenantId, categoria);
        return ResponseEntity.ok(mapToResponseList(despesas));
    }

    /**
     * Listar despesas por status
     */
    @GetMapping("/status/{status}")
    public ResponseEntity<List<DespesaOperacionalResponse>> listarPorStatus(
            @PathVariable StatusDespesa status
    ) {
        UUID tenantId = TenantContext.getTenantId();

        List<DespesaOperacional> despesas = service.listarPorStatus(tenantId, status);
        return ResponseEntity.ok(mapToResponseList(despesas));
    }

    /**
     * Listar despesas pendentes de aprovacao
     * Permissao: GERENTE, ADMIN_TENANT
     */
    @GetMapping("/pendentes")
    public ResponseEntity<List<DespesaOperacionalResponse>> listarPendentes() {
        UUID tenantId = TenantContext.getTenantId();

        List<DespesaOperacional> despesas = service.listarPendentesAprovacao(tenantId);
        return ResponseEntity.ok(mapToResponseList(despesas));
    }

    /**
     * Listar despesas aguardando pagamento
     * Permissao: FINANCEIRO, ADMIN_TENANT
     */
    @GetMapping("/aguardando-pagamento")
    public ResponseEntity<List<DespesaOperacionalResponse>> listarAguardandoPagamento() {
        UUID tenantId = TenantContext.getTenantId();

        List<DespesaOperacional> despesas = service.listarAguardandoPagamento(tenantId);
        return ResponseEntity.ok(mapToResponseList(despesas));
    }

    // ========== Workflow ==========

    /**
     * Aprovar despesa
     * Permissao: GERENTE, ADMIN_TENANT
     */
    @PostMapping("/{id}/aprovar")
    public ResponseEntity<DespesaOperacionalResponse> aprovar(
            @PathVariable UUID id,
            Authentication authentication
    ) {
        UUID tenantId = TenantContext.getTenantId();
        UUID aprovadorId = obterUsuarioId(authentication);

        DespesaOperacional despesa = service.aprovar(tenantId, id, aprovadorId);
        return ResponseEntity.ok(mapToResponse(despesa));
    }

    /**
     * Rejeitar despesa
     * Permissao: GERENTE, ADMIN_TENANT
     */
    @PostMapping("/{id}/rejeitar")
    public ResponseEntity<DespesaOperacionalResponse> rejeitar(
            @PathVariable UUID id,
            @Valid @RequestBody RejeitarDespesaRequest request,
            Authentication authentication
    ) {
        UUID tenantId = TenantContext.getTenantId();
        UUID aprovadorId = obterUsuarioId(authentication);

        DespesaOperacional despesa = service.rejeitar(tenantId, id, aprovadorId, request.getMotivo());
        return ResponseEntity.ok(mapToResponse(despesa));
    }

    /**
     * Marcar despesa como paga
     * Permissao: FINANCEIRO, ADMIN_TENANT
     */
    @PostMapping("/{id}/pagar")
    public ResponseEntity<DespesaOperacionalResponse> pagar(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) PagarDespesaRequest request,
            Authentication authentication
    ) {
        UUID tenantId = TenantContext.getTenantId();
        UUID pagadorId = obterUsuarioId(authentication);

        String referencia = request != null ? request.getReferenciaPagamento() : null;
        DespesaOperacional despesa = service.marcarComoPaga(tenantId, id, pagadorId, referencia);
        return ResponseEntity.ok(mapToResponse(despesa));
    }

    // ========== Mappers ==========

    private DespesaOperacionalResponse mapToResponse(DespesaOperacional despesa) {
        return DespesaOperacionalResponse.builder()
                .id(despesa.getId())
                .tenantId(despesa.getTenantId())
                .dtReferencia(despesa.getDtReferencia())
                .categoria(despesa.getCategoria())
                .descricao(despesa.getDescricao())
                .valor(despesa.getValor())
                .responsavelId(despesa.getResponsavelId())
                .status(despesa.getStatus())
                .aprovadoPor(despesa.getAprovadoPor())
                .aprovadoEm(despesa.getAprovadoEm())
                .pagoPor(despesa.getPagoPor())
                .pagoEm(despesa.getPagoEm())
                .referenciaPagamento(despesa.getReferenciaPagamento())
                .observacoes(despesa.getObservacoes())
                .createdAt(despesa.getCreatedAt())
                .updatedAt(despesa.getUpdatedAt())
                .build();
    }

    private List<DespesaOperacionalResponse> mapToResponseList(List<DespesaOperacional> despesas) {
        return despesas.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private UUID obterUsuarioId(Authentication authentication) {
        return usuarioService.getUserIdFromAuthentication(authentication);
    }
}
