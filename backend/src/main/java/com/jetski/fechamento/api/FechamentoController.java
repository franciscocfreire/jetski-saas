package com.jetski.fechamento.api;

import com.jetski.fechamento.api.dto.*;
import com.jetski.fechamento.domain.FechamentoDiario;
import com.jetski.fechamento.domain.FechamentoMensal;
import com.jetski.fechamento.internal.report.FechamentoReportService;
import com.jetski.shared.security.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST Controller for Financial Closures (Daily and Monthly)
 *
 * <p>Endpoints:</p>
 * <ul>
 *   <li>POST /fechamentos/dia/consolidar - Consolidar dia específico (GERENTE)</li>
 *   <li>GET /fechamentos/dia/{id} - Buscar fechamento diário por ID</li>
 *   <li>GET /fechamentos/dia/data/{data} - Buscar fechamento diário por data</li>
 *   <li>GET /fechamentos/dia - Listar fechamentos diários por intervalo</li>
 *   <li>POST /fechamentos/dia/{id}/fechar - Fechar e bloquear dia (GERENTE)</li>
 *   <li>POST /fechamentos/dia/{id}/aprovar - Aprovar fechamento diário (ADMIN_TENANT)</li>
 *   <li>POST /fechamentos/dia/{id}/reabrir - Reabrir fechamento diário (ADMIN_TENANT)</li>
 *   <li>POST /fechamentos/mes/consolidar - Consolidar mês específico (GERENTE)</li>
 *   <li>GET /fechamentos/mes/{id} - Buscar fechamento mensal por ID</li>
 *   <li>GET /fechamentos/mes/{ano}/{mes} - Buscar fechamento mensal por período</li>
 *   <li>GET /fechamentos/mes - Listar todos os fechamentos mensais</li>
 *   <li>GET /fechamentos/mes/ano/{ano} - Listar fechamentos mensais por ano</li>
 *   <li>POST /fechamentos/mes/{id}/fechar - Fechar e bloquear mês (GERENTE)</li>
 *   <li>POST /fechamentos/mes/{id}/aprovar - Aprovar fechamento mensal (ADMIN_TENANT)</li>
 *   <li>POST /fechamentos/mes/{id}/reabrir - Reabrir fechamento mensal (ADMIN_TENANT)</li>
 * </ul>
 *
 * @author Jetski Team
 * @since 0.8.0
 */
@RestController
@RequestMapping("/v1/tenants/{tenantId}/fechamentos")
@RequiredArgsConstructor
@Slf4j
public class FechamentoController {

    private final FechamentoService fechamentoService;
    private final FechamentoReportService reportService;

    // ====================
    // Fechamento Diário
    // ====================

    /**
     * Consolidar locações de um dia específico
     * Permissão: GERENTE, ADMIN_TENANT
     */
    @PostMapping("/dia/consolidar")
    public ResponseEntity<FechamentoDiarioResponse> consolidarDia(
            @Valid @RequestBody ConsolidarDiaRequest request,
            Authentication authentication
    ) {
        UUID tenantId = TenantContext.getTenantId();
        UUID operadorId = obterUsuarioId(authentication);

        // Verificar se já existe (para retornar status HTTP correto)
        boolean existente = fechamentoService.existeFechamentoDiario(tenantId, request.getDtReferencia());

        FechamentoDiario fechamento = fechamentoService.consolidarDia(
                tenantId,
                request.getDtReferencia(),
                operadorId
        );

        // 201 Created se novo, 200 OK se reconsolidação
        HttpStatus status = existente ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(mapDiarioToResponse(fechamento));
    }

    /**
     * Buscar fechamento diário por ID
     * Permissão: GERENTE, ADMIN_TENANT, FINANCEIRO
     */
    @GetMapping("/dia/{id}")
    public ResponseEntity<FechamentoDiarioResponse> buscarFechamentoDiario(@PathVariable UUID id) {
        UUID tenantId = TenantContext.getTenantId();

        FechamentoDiario fechamento = fechamentoService.buscarFechamentoDiario(tenantId, id);
        return ResponseEntity.ok(mapDiarioToResponse(fechamento));
    }

    /**
     * Buscar fechamento diário por data
     * Permissão: GERENTE, ADMIN_TENANT, FINANCEIRO
     */
    @GetMapping("/dia/data/{data}")
    public ResponseEntity<FechamentoDiarioResponse> buscarFechamentoDiarioPorData(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data
    ) {
        UUID tenantId = TenantContext.getTenantId();

        FechamentoDiario fechamento = fechamentoService.buscarFechamentoDiarioPorData(tenantId, data);
        return ResponseEntity.ok(mapDiarioToResponse(fechamento));
    }

    /**
     * Listar fechamentos diários por intervalo de datas
     * Permissão: GERENTE, ADMIN_TENANT, FINANCEIRO
     */
    @GetMapping("/dia")
    public ResponseEntity<List<FechamentoDiarioResponse>> listarFechamentosDiarios(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim
    ) {
        UUID tenantId = TenantContext.getTenantId();

        List<FechamentoDiario> fechamentos = fechamentoService.listarFechamentosDiarios(tenantId, dataInicio, dataFim);
        List<FechamentoDiarioResponse> responses = fechamentos.stream()
                .map(this::mapDiarioToResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    /**
     * Fechar e bloquear fechamento diário
     * Permissão: GERENTE, ADMIN_TENANT
     */
    @PostMapping("/dia/{id}/fechar")
    public ResponseEntity<FechamentoDiarioResponse> fecharDia(
            @PathVariable UUID id,
            Authentication authentication
    ) {
        UUID tenantId = TenantContext.getTenantId();
        UUID operadorId = obterUsuarioId(authentication);

        FechamentoDiario fechamento = fechamentoService.fecharDia(tenantId, id, operadorId);
        return ResponseEntity.ok(mapDiarioToResponse(fechamento));
    }

    /**
     * Aprovar fechamento diário
     * Permissão: ADMIN_TENANT
     */
    @PostMapping("/dia/{id}/aprovar")
    public ResponseEntity<FechamentoDiarioResponse> aprovarFechamentoDiario(@PathVariable UUID id) {
        UUID tenantId = TenantContext.getTenantId();

        FechamentoDiario fechamento = fechamentoService.aprovarFechamentoDiario(tenantId, id);
        return ResponseEntity.ok(mapDiarioToResponse(fechamento));
    }

    /**
     * Reabrir fechamento diário (apenas se não estiver aprovado)
     * Permissão: ADMIN_TENANT
     */
    @PostMapping("/dia/{id}/reabrir")
    public ResponseEntity<FechamentoDiarioResponse> reabrirFechamentoDiario(@PathVariable UUID id) {
        UUID tenantId = TenantContext.getTenantId();

        FechamentoDiario fechamento = fechamentoService.reabrirFechamentoDiario(tenantId, id);
        return ResponseEntity.ok(mapDiarioToResponse(fechamento));
    }

    /**
     * Forçar reabertura de fechamento diário (mesmo se aprovado)
     * Permissão: ADMIN_TENANT (uso administrativo e testes)
     */
    @PostMapping("/dia/{id}/forcar-reabrir")
    public ResponseEntity<FechamentoDiarioResponse> forcarReabrirFechamentoDiario(@PathVariable UUID id) {
        UUID tenantId = TenantContext.getTenantId();

        FechamentoDiario fechamento = fechamentoService.forcarReabrirFechamentoDiario(tenantId, id);
        return ResponseEntity.ok(mapDiarioToResponse(fechamento));
    }

    /**
     * Verificar divergências em fechamentos de um período.
     * Retorna lista de fechamentos cujos valores armazenados diferem dos valores atuais das locações.
     * Permissão: GERENTE, ADMIN_TENANT, FINANCEIRO
     */
    @GetMapping("/dia/divergencias")
    public ResponseEntity<List<DivergenciaResponse>> verificarDivergencias(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim
    ) {
        UUID tenantId = TenantContext.getTenantId();

        List<DivergenciaResponse> divergencias = fechamentoService.verificarDivergencias(
                tenantId, dataInicio, dataFim);

        log.info("Verificação de divergências: {} encontradas para período {} a {} (tenant: {})",
                divergencias.size(), dataInicio, dataFim, tenantId);

        return ResponseEntity.ok(divergencias);
    }

    // ====================
    // Fechamento Mensal
    // ====================

    /**
     * Consolidar fechamentos diários de um mês
     * Permissão: GERENTE, ADMIN_TENANT
     */
    @PostMapping("/mes/consolidar")
    public ResponseEntity<FechamentoMensalResponse> consolidarMes(
            @Valid @RequestBody ConsolidarMesRequest request,
            Authentication authentication
    ) {
        UUID tenantId = TenantContext.getTenantId();
        UUID operadorId = obterUsuarioId(authentication);

        // Verificar se já existe (para retornar status HTTP correto)
        boolean existente = fechamentoService.existeFechamentoMensal(tenantId, request.getAno(), request.getMes());

        FechamentoMensal fechamento = fechamentoService.consolidarMes(
                tenantId,
                request.getAno(),
                request.getMes(),
                operadorId
        );

        // 201 Created se novo, 200 OK se reconsolidação
        HttpStatus status = existente ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(mapMensalToResponse(fechamento));
    }

    /**
     * Buscar fechamento mensal por ID
     * Permissão: GERENTE, ADMIN_TENANT, FINANCEIRO
     */
    @GetMapping("/mes/{id}")
    public ResponseEntity<FechamentoMensalResponse> buscarFechamentoMensal(@PathVariable UUID id) {
        UUID tenantId = TenantContext.getTenantId();

        FechamentoMensal fechamento = fechamentoService.buscarFechamentoMensal(tenantId, id);
        return ResponseEntity.ok(mapMensalToResponse(fechamento));
    }

    /**
     * Buscar fechamento mensal por ano e mês
     * Permissão: GERENTE, ADMIN_TENANT, FINANCEIRO
     */
    @GetMapping("/mes/{ano}/{mes}")
    public ResponseEntity<FechamentoMensalResponse> buscarFechamentoMensalPorPeriodo(
            @PathVariable Integer ano,
            @PathVariable Integer mes
    ) {
        UUID tenantId = TenantContext.getTenantId();

        FechamentoMensal fechamento = fechamentoService.buscarFechamentoMensalPorPeriodo(tenantId, ano, mes);
        return ResponseEntity.ok(mapMensalToResponse(fechamento));
    }

    /**
     * Listar todos os fechamentos mensais
     * Permissão: GERENTE, ADMIN_TENANT, FINANCEIRO
     */
    @GetMapping("/mes")
    public ResponseEntity<List<FechamentoMensalResponse>> listarFechamentosMensais() {
        UUID tenantId = TenantContext.getTenantId();

        List<FechamentoMensal> fechamentos = fechamentoService.listarFechamentosMensais(tenantId);
        List<FechamentoMensalResponse> responses = fechamentos.stream()
                .map(this::mapMensalToResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    /**
     * Listar fechamentos mensais por ano
     * Permissão: GERENTE, ADMIN_TENANT, FINANCEIRO
     */
    @GetMapping("/mes/ano/{ano}")
    public ResponseEntity<List<FechamentoMensalResponse>> listarFechamentosMensaisPorAno(@PathVariable Integer ano) {
        UUID tenantId = TenantContext.getTenantId();

        List<FechamentoMensal> fechamentos = fechamentoService.listarFechamentosMensaisPorAno(tenantId, ano);
        List<FechamentoMensalResponse> responses = fechamentos.stream()
                .map(this::mapMensalToResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    /**
     * Fechar e bloquear fechamento mensal
     * Permissão: GERENTE, ADMIN_TENANT
     */
    @PostMapping("/mes/{id}/fechar")
    public ResponseEntity<FechamentoMensalResponse> fecharMes(@PathVariable UUID id) {
        UUID tenantId = TenantContext.getTenantId();

        FechamentoMensal fechamento = fechamentoService.fecharMes(tenantId, id);
        return ResponseEntity.ok(mapMensalToResponse(fechamento));
    }

    /**
     * Aprovar fechamento mensal
     * Permissão: ADMIN_TENANT
     */
    @PostMapping("/mes/{id}/aprovar")
    public ResponseEntity<FechamentoMensalResponse> aprovarFechamentoMensal(@PathVariable UUID id) {
        UUID tenantId = TenantContext.getTenantId();

        FechamentoMensal fechamento = fechamentoService.aprovarFechamentoMensal(tenantId, id);
        return ResponseEntity.ok(mapMensalToResponse(fechamento));
    }

    /**
     * Reabrir fechamento mensal (apenas se não estiver aprovado)
     * Permissão: ADMIN_TENANT
     */
    @PostMapping("/mes/{id}/reabrir")
    public ResponseEntity<FechamentoMensalResponse> reabrirFechamentoMensal(@PathVariable UUID id) {
        UUID tenantId = TenantContext.getTenantId();

        FechamentoMensal fechamento = fechamentoService.reabrirFechamentoMensal(tenantId, id);
        return ResponseEntity.ok(mapMensalToResponse(fechamento));
    }

    /**
     * Forçar reabertura de fechamento mensal (mesmo se aprovado)
     * Permissão: ADMIN_TENANT (uso administrativo e testes)
     */
    @PostMapping("/mes/{id}/forcar-reabrir")
    public ResponseEntity<FechamentoMensalResponse> forcarReabrirFechamentoMensal(@PathVariable UUID id) {
        UUID tenantId = TenantContext.getTenantId();

        FechamentoMensal fechamento = fechamentoService.forcarReabrirFechamentoMensal(tenantId, id);
        return ResponseEntity.ok(mapMensalToResponse(fechamento));
    }

    // ====================
    // Mappers & Helpers
    // ====================

    private FechamentoDiarioResponse mapDiarioToResponse(FechamentoDiario fechamento) {
        return FechamentoDiarioResponse.builder()
                .id(fechamento.getId())
                .dtReferencia(fechamento.getDtReferencia())
                .operadorId(fechamento.getOperadorId())
                .totalLocacoes(fechamento.getTotalLocacoes())
                .totalFaturado(fechamento.getTotalFaturado())
                .totalCombustivel(fechamento.getTotalCombustivel())
                .totalComissoes(fechamento.getTotalComissoes())
                .totalDinheiro(fechamento.getTotalDinheiro())
                .totalCartao(fechamento.getTotalCartao())
                .totalPix(fechamento.getTotalPix())
                .totalDespesasOperacionais(fechamento.getTotalDespesasOperacionais())
                .totalDiariasVendedores(fechamento.getTotalDiariasVendedores())
                .status(fechamento.getStatus())
                .dtFechamento(fechamento.getDtFechamento())
                .bloqueado(fechamento.getBloqueado())
                .observacoes(fechamento.getObservacoes())
                .divergenciasJson(fechamento.getDivergenciasJson())
                .createdAt(fechamento.getCreatedAt())
                .updatedAt(fechamento.getUpdatedAt())
                .valoresHash(fechamento.getValoresHash())
                .hasDivergencia(fechamento.hasDivergencia())
                .build();
    }

    private FechamentoMensalResponse mapMensalToResponse(FechamentoMensal fechamento) {
        return FechamentoMensalResponse.builder()
                .id(fechamento.getId())
                .ano(fechamento.getAno())
                .mes(fechamento.getMes())
                .operadorId(fechamento.getOperadorId())
                .totalLocacoes(fechamento.getTotalLocacoes())
                .totalFaturado(fechamento.getTotalFaturado())
                .totalCustos(fechamento.getTotalCustos())
                .totalComissoes(fechamento.getTotalComissoes())
                .totalManutencoes(fechamento.getTotalManutencoes())
                .totalDespesasOperacionais(fechamento.getTotalDespesasOperacionais())
                .totalDiariasVendedores(fechamento.getTotalDiariasVendedores())
                .resultadoLiquido(fechamento.getResultadoLiquido())
                .status(fechamento.getStatus())
                .dtFechamento(fechamento.getDtFechamento())
                .bloqueado(fechamento.getBloqueado())
                .observacoes(fechamento.getObservacoes())
                .relatorioUrl(fechamento.getRelatorioUrl())
                .createdAt(fechamento.getCreatedAt())
                .updatedAt(fechamento.getUpdatedAt())
                .build();
    }

    // ====================
    // Relatórios
    // ====================

    /**
     * Gerar relatório de fechamento diário
     * Formato: pdf ou excel
     */
    @GetMapping("/dia/{id}/relatorio")
    public ResponseEntity<byte[]> gerarRelatorioDiario(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "pdf") String formato
    ) {
        UUID tenantId = TenantContext.getTenantId();
        FechamentoDiario fechamento = fechamentoService.buscarFechamentoDiario(tenantId, id);

        // TODO: Get tenant name from TenantService
        String tenantName = "Jetski SaaS";

        byte[] content;
        String contentType;
        String filename;
        String dataFormatted = fechamento.getDtReferencia().toString();

        if ("excel".equalsIgnoreCase(formato)) {
            content = reportService.generateDiarioExcel(fechamento, tenantName);
            contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            filename = "fechamento_diario_" + dataFormatted + ".xlsx";
        } else {
            content = reportService.generateDiarioPdf(fechamento, tenantName);
            contentType = "application/pdf";
            filename = "fechamento_diario_" + dataFormatted + ".pdf";
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(content);
    }

    /**
     * Gerar relatório de fechamento mensal
     * Formato: pdf ou excel
     */
    @GetMapping("/mes/{id}/relatorio")
    public ResponseEntity<byte[]> gerarRelatorioMensal(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "pdf") String formato
    ) {
        UUID tenantId = TenantContext.getTenantId();
        FechamentoMensal fechamento = fechamentoService.buscarFechamentoMensal(tenantId, id);

        // TODO: Get tenant name from TenantService
        String tenantName = "Jetski SaaS";

        byte[] content;
        String contentType;
        String filename;
        String periodo = fechamento.getAno() + "_" + String.format("%02d", fechamento.getMes());

        if ("excel".equalsIgnoreCase(formato)) {
            content = reportService.generateMensalExcel(fechamento, tenantName);
            contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            filename = "fechamento_mensal_" + periodo + ".xlsx";
        } else {
            content = reportService.generateMensalPdf(fechamento, tenantName);
            contentType = "application/pdf";
            filename = "fechamento_mensal_" + periodo + ".pdf";
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(content);
    }

    // ====================
    // Helper Methods
    // ====================

    /**
     * Obtém ID do usuário autenticado
     */
    private UUID obterUsuarioId(Authentication authentication) {
        // Use TenantContext.getUsuarioId() which is already resolved from Keycloak UUID
        // to internal PostgreSQL UUID by TenantFilter via usuario_identity_provider table
        return TenantContext.getUsuarioId();
    }
}
