package com.jetski.manutencao.api;

import com.jetski.manutencao.api.dto.RejeitarDespesaManutencaoRequest;
import com.jetski.despesas.domain.StatusDespesa;
import com.jetski.locacoes.api.JetskiPublicService;
import com.jetski.locacoes.domain.Jetski;
import com.jetski.manutencao.api.dto.DespesaManutencaoResponse;
import com.jetski.manutencao.api.dto.GerarDespesaManutencaoRequest;
import com.jetski.manutencao.api.dto.PagarDespesaManutencaoRequest;
import com.jetski.manutencao.domain.DespesaManutencao;
import com.jetski.manutencao.domain.OSManutencao;
import com.jetski.manutencao.internal.DespesaManutencaoService;
import com.jetski.manutencao.internal.OSManutencaoService;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST Controller for Despesas de Manutencao (Maintenance Expenses)
 *
 * <p>Endpoints:</p>
 * <ul>
 *   <li>GET /despesas-manutencao - Listar por periodo</li>
 *   <li>GET /despesas-manutencao/pendentes - Listar pendentes de aprovacao</li>
 *   <li>GET /despesas-manutencao/aguardando-pagamento - Listar aguardando pagamento</li>
 *   <li>GET /despesas-manutencao/{id} - Buscar por ID</li>
 *   <li>GET /despesas-manutencao/os/{osId} - Listar por OS</li>
 *   <li>POST /manutencoes/{osId}/gerar-despesa - Gerar despesas parceladas</li>
 *   <li>POST /despesas-manutencao/{id}/aprovar - Aprovar</li>
 *   <li>POST /despesas-manutencao/{id}/rejeitar - Rejeitar</li>
 *   <li>POST /despesas-manutencao/{id}/pagar - Marcar como paga</li>
 *   <li>POST /despesas-manutencao/{id}/cancelar - Cancelar</li>
 * </ul>
 *
 * @author Jetski Team
 * @since 0.9.0
 */
@RestController
@RequestMapping("/v1/tenants/{tenantId}")
@RequiredArgsConstructor
@Slf4j
public class DespesaManutencaoController {

    private final DespesaManutencaoService service;
    private final OSManutencaoService osManutencaoService;
    private final JetskiPublicService jetskiPublicService;
    private final UsuarioService usuarioService;

    // ========== Gerar Despesas ==========

    /**
     * Gerar despesas parceladas para uma OS concluida
     * Permissao: GERENTE, ADMIN_TENANT
     */
    @PostMapping("/manutencoes/{osId}/gerar-despesa")
    public ResponseEntity<List<DespesaManutencaoResponse>> gerarDespesas(
            @PathVariable UUID osId,
            @Valid @RequestBody GerarDespesaManutencaoRequest request,
            Authentication authentication
    ) {
        UUID tenantId = TenantContext.getTenantId();

        List<DespesaManutencao> despesas = service.gerarDespesasParceladas(
                tenantId,
                osId,
                request.getNumeroParcelas(),
                request.getPrimeiroVencimento(),
                request.getObservacoes()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(mapToResponseList(despesas));
    }

    // ========== Busca ==========

    /**
     * Buscar despesa por ID
     */
    @GetMapping("/despesas-manutencao/{id}")
    public ResponseEntity<DespesaManutencaoResponse> buscarPorId(@PathVariable UUID id) {
        UUID tenantId = TenantContext.getTenantId();

        DespesaManutencao despesa = service.buscarPorId(tenantId, id);
        return ResponseEntity.ok(mapToResponseWithOS(despesa));
    }

    // ========== Listagens ==========

    /**
     * Listar despesas por periodo de vencimento
     */
    @GetMapping("/despesas-manutencao")
    public ResponseEntity<List<DespesaManutencaoResponse>> listarPorPeriodo(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim
    ) {
        UUID tenantId = TenantContext.getTenantId();

        List<DespesaManutencao> despesas = service.listarPorPeriodo(tenantId, dataInicio, dataFim);
        return ResponseEntity.ok(mapToResponseList(despesas));
    }

    /**
     * Listar despesas de uma OS especifica
     */
    @GetMapping("/despesas-manutencao/os/{osId}")
    public ResponseEntity<List<DespesaManutencaoResponse>> listarPorOS(@PathVariable UUID osId) {
        UUID tenantId = TenantContext.getTenantId();

        List<DespesaManutencao> despesas = service.listarPorOS(tenantId, osId);
        return ResponseEntity.ok(mapToResponseListWithOS(despesas));
    }

    /**
     * Listar despesas por status
     */
    @GetMapping("/despesas-manutencao/status/{status}")
    public ResponseEntity<List<DespesaManutencaoResponse>> listarPorStatus(
            @PathVariable StatusDespesa status
    ) {
        UUID tenantId = TenantContext.getTenantId();

        List<DespesaManutencao> despesas = service.listarPorStatus(tenantId, status);
        return ResponseEntity.ok(mapToResponseList(despesas));
    }

    /**
     * Listar despesas pendentes de aprovacao
     * Permissao: GERENTE, ADMIN_TENANT
     */
    @GetMapping("/despesas-manutencao/pendentes")
    public ResponseEntity<List<DespesaManutencaoResponse>> listarPendentes() {
        UUID tenantId = TenantContext.getTenantId();

        List<DespesaManutencao> despesas = service.listarPendentesAprovacao(tenantId);
        return ResponseEntity.ok(mapToResponseListWithOS(despesas));
    }

    /**
     * Listar despesas aguardando pagamento
     * Permissao: FINANCEIRO, ADMIN_TENANT
     */
    @GetMapping("/despesas-manutencao/aguardando-pagamento")
    public ResponseEntity<List<DespesaManutencaoResponse>> listarAguardandoPagamento() {
        UUID tenantId = TenantContext.getTenantId();

        List<DespesaManutencao> despesas = service.listarAguardandoPagamento(tenantId);
        return ResponseEntity.ok(mapToResponseListWithOS(despesas));
    }

    /**
     * Verificar se ja existem despesas para uma OS
     */
    @GetMapping("/manutencoes/{osId}/tem-despesa")
    public ResponseEntity<Boolean> temDespesa(@PathVariable UUID osId) {
        UUID tenantId = TenantContext.getTenantId();

        boolean existe = service.existeDespesaParaOS(tenantId, osId);
        return ResponseEntity.ok(existe);
    }

    // ========== Workflow ==========

    /**
     * Aprovar despesa
     * Permissao: GERENTE, ADMIN_TENANT
     */
    @PostMapping("/despesas-manutencao/{id}/aprovar")
    public ResponseEntity<DespesaManutencaoResponse> aprovar(
            @PathVariable UUID id,
            Authentication authentication
    ) {
        UUID tenantId = TenantContext.getTenantId();
        Integer membroId = obterMembroId(tenantId, authentication);

        DespesaManutencao despesa = service.aprovar(tenantId, id, membroId);
        return ResponseEntity.ok(mapToResponseWithOS(despesa));
    }

    /**
     * Rejeitar despesa
     * Permissao: GERENTE, ADMIN_TENANT
     */
    @PostMapping("/despesas-manutencao/{id}/rejeitar")
    public ResponseEntity<DespesaManutencaoResponse> rejeitar(
            @PathVariable UUID id,
            @Valid @RequestBody RejeitarDespesaManutencaoRequest request,
            Authentication authentication
    ) {
        UUID tenantId = TenantContext.getTenantId();
        Integer membroId = obterMembroId(tenantId, authentication);

        DespesaManutencao despesa = service.rejeitar(tenantId, id, membroId, request.getMotivo());
        return ResponseEntity.ok(mapToResponseWithOS(despesa));
    }

    /**
     * Marcar despesa como paga
     * Permissao: FINANCEIRO, ADMIN_TENANT
     */
    @PostMapping("/despesas-manutencao/{id}/pagar")
    public ResponseEntity<DespesaManutencaoResponse> pagar(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) PagarDespesaManutencaoRequest request,
            Authentication authentication
    ) {
        UUID tenantId = TenantContext.getTenantId();
        Integer membroId = obterMembroId(tenantId, authentication);

        String referencia = request != null ? request.getReferenciaPagamento() : null;
        DespesaManutencao despesa = service.marcarComoPaga(tenantId, id, membroId, referencia);
        return ResponseEntity.ok(mapToResponseWithOS(despesa));
    }

    /**
     * Cancelar despesa
     * Permissao: GERENTE, ADMIN_TENANT
     */
    @PostMapping("/despesas-manutencao/{id}/cancelar")
    public ResponseEntity<DespesaManutencaoResponse> cancelar(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) RejeitarDespesaManutencaoRequest request,
            Authentication authentication
    ) {
        UUID tenantId = TenantContext.getTenantId();
        Integer membroId = obterMembroId(tenantId, authentication);

        String motivo = request != null ? request.getMotivo() : null;
        DespesaManutencao despesa = service.cancelar(tenantId, id, membroId, motivo);
        return ResponseEntity.ok(mapToResponseWithOS(despesa));
    }

    // ========== Mappers ==========

    private DespesaManutencaoResponse mapToResponse(DespesaManutencao despesa) {
        return DespesaManutencaoResponse.builder()
                .id(despesa.getId())
                .tenantId(despesa.getTenantId())
                .osManutencaoId(despesa.getOsManutencaoId())
                .dtVencimento(despesa.getDtVencimento())
                .numeroParcela(despesa.getNumeroParcela())
                .totalParcelas(despesa.getTotalParcelas())
                .descricaoParcela(despesa.getDescricaoParcela())
                .valor(despesa.getValor())
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

    private DespesaManutencaoResponse mapToResponseWithOS(DespesaManutencao despesa) {
        DespesaManutencaoResponse response = mapToResponse(despesa);

        // Enriquece com dados da OS
        try {
            OSManutencao os = osManutencaoService.findById(despesa.getOsManutencaoId());

            // Gera um numero de OS a partir do ID (primeiros 8 caracteres)
            response.setOsNumero("OS-" + os.getId().toString().substring(0, 8).toUpperCase());
            response.setDescricaoProblema(os.getDescricaoProblema());

            // Busca o jetski pelo ID
            if (os.getJetskiId() != null) {
                try {
                    Jetski jetski = jetskiPublicService.findById(os.getJetskiId());
                    response.setJetskiNome(jetski.getSerie());
                } catch (Exception e) {
                    log.debug("Nao foi possivel buscar jetski {}: {}", os.getJetskiId(), e.getMessage());
                }
            }

            BigDecimal valorPecas = os.getValorPecas() != null ? os.getValorPecas() : BigDecimal.ZERO;
            BigDecimal valorMaoObra = os.getValorMaoObra() != null ? os.getValorMaoObra() : BigDecimal.ZERO;
            response.setValorTotalOS(valorPecas.add(valorMaoObra));
        } catch (Exception e) {
            log.warn("Nao foi possivel enriquecer despesa {} com dados da OS: {}", despesa.getId(), e.getMessage());
        }

        return response;
    }

    private List<DespesaManutencaoResponse> mapToResponseList(List<DespesaManutencao> despesas) {
        return despesas.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private List<DespesaManutencaoResponse> mapToResponseListWithOS(List<DespesaManutencao> despesas) {
        return despesas.stream()
                .map(this::mapToResponseWithOS)
                .collect(Collectors.toList());
    }

    private UUID obterUsuarioId(Authentication authentication) {
        return usuarioService.getUserIdFromAuthentication(authentication);
    }

    private Integer obterMembroId(UUID tenantId, Authentication authentication) {
        UUID usuarioId = obterUsuarioId(authentication);
        return usuarioService.findMembroId(tenantId, usuarioId).orElse(null);
    }
}
