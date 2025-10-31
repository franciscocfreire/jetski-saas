package com.jetski.comissoes.api;

import com.jetski.comissoes.api.dto.PoliticaComissaoRequest;
import com.jetski.comissoes.api.dto.PoliticaComissaoResponse;
import com.jetski.comissoes.domain.PoliticaComissao;
import com.jetski.comissoes.internal.PoliticaComissaoService;
import com.jetski.shared.security.TenantContext;
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
 * REST Controller for Commission Policies
 *
 * <p>Endpoints:</p>
 * <ul>
 *   <li>POST /politicas-comissao - Criar política (GERENTE)</li>
 *   <li>GET /politicas-comissao - Listar políticas ativas</li>
 *   <li>GET /politicas-comissao/todas - Listar todas as políticas</li>
 *   <li>GET /politicas-comissao/{id} - Buscar por ID</li>
 *   <li>PUT /politicas-comissao/{id} - Atualizar política (GERENTE)</li>
 *   <li>PATCH /politicas-comissao/{id}/toggle - Ativar/desativar (GERENTE)</li>
 * </ul>
 *
 * @author Jetski Team
 * @since 0.7.0
 */
@RestController
@RequestMapping("/api/v1/politicas-comissao")
@RequiredArgsConstructor
@Slf4j
public class PoliticaComissaoController {

    private final PoliticaComissaoService politicaService;

    /**
     * Criar nova política de comissão
     * Permissão: GERENTE, ADMIN_TENANT
     */
    @PostMapping
    public ResponseEntity<PoliticaComissaoResponse> criar(
            @Valid @RequestBody PoliticaComissaoRequest request
    ) {
        UUID tenantId = TenantContext.getTenantId();

        PoliticaComissao politica = mapToDomain(request);
        PoliticaComissao criada = politicaService.criar(tenantId, politica);

        return ResponseEntity.status(HttpStatus.CREATED).body(mapToResponse(criada));
    }

    /**
     * Listar políticas ativas
     * Permissão: GERENTE, ADMIN_TENANT, VENDEDOR
     */
    @GetMapping
    public ResponseEntity<List<PoliticaComissaoResponse>> listarAtivas() {
        UUID tenantId = TenantContext.getTenantId();

        List<PoliticaComissao> politicas = politicaService.listarAtivas(tenantId);
        List<PoliticaComissaoResponse> responses = politicas.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    /**
     * Listar todas as políticas (ativas e inativas)
     * Permissão: GERENTE, ADMIN_TENANT
     */
    @GetMapping("/todas")
    public ResponseEntity<List<PoliticaComissaoResponse>> listarTodas() {
        UUID tenantId = TenantContext.getTenantId();

        List<PoliticaComissao> politicas = politicaService.listarTodas(tenantId);
        List<PoliticaComissaoResponse> responses = politicas.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    /**
     * Buscar política por ID
     * Permissão: GERENTE, ADMIN_TENANT, VENDEDOR
     */
    @GetMapping("/{id}")
    public ResponseEntity<PoliticaComissaoResponse> buscarPorId(@PathVariable UUID id) {
        UUID tenantId = TenantContext.getTenantId();

        PoliticaComissao politica = politicaService.buscarPorId(tenantId, id);
        return ResponseEntity.ok(mapToResponse(politica));
    }

    /**
     * Atualizar política existente
     * Permissão: GERENTE, ADMIN_TENANT
     */
    @PutMapping("/{id}")
    public ResponseEntity<PoliticaComissaoResponse> atualizar(
            @PathVariable UUID id,
            @Valid @RequestBody PoliticaComissaoRequest request
    ) {
        UUID tenantId = TenantContext.getTenantId();

        PoliticaComissao politica = mapToDomain(request);
        PoliticaComissao atualizada = politicaService.atualizar(tenantId, id, politica);

        return ResponseEntity.ok(mapToResponse(atualizada));
    }

    /**
     * Ativar/desativar política
     * Permissão: GERENTE, ADMIN_TENANT
     */
    @PatchMapping("/{id}/toggle")
    public ResponseEntity<PoliticaComissaoResponse> toggleAtiva(@PathVariable UUID id) {
        UUID tenantId = TenantContext.getTenantId();

        PoliticaComissao politica = politicaService.toggleAtiva(tenantId, id);
        return ResponseEntity.ok(mapToResponse(politica));
    }

    // ====================
    // Mappers
    // ====================

    private PoliticaComissao mapToDomain(PoliticaComissaoRequest request) {
        return PoliticaComissao.builder()
                .nome(request.getNome())
                .nivel(request.getNivel())
                .tipo(request.getTipo())
                .vendedorId(request.getVendedorId())
                .modeloId(request.getModeloId())
                .codigoCampanha(request.getCodigoCampanha())
                .duracaoMinMinutos(request.getDuracaoMinMinutos())
                .duracaoMaxMinutos(request.getDuracaoMaxMinutos())
                .percentualComissao(request.getPercentualComissao())
                .percentualExtra(request.getPercentualExtra())
                .valorFixo(request.getValorFixo())
                .vigenciaInicio(request.getVigenciaInicio())
                .vigenciaFim(request.getVigenciaFim())
                .ativa(request.getAtiva() != null ? request.getAtiva() : true)
                .descricao(request.getDescricao())
                .build();
    }

    private PoliticaComissaoResponse mapToResponse(PoliticaComissao politica) {
        return PoliticaComissaoResponse.builder()
                .id(politica.getId())
                .nome(politica.getNome())
                .nivel(politica.getNivel())
                .tipo(politica.getTipo())
                .vendedorId(politica.getVendedorId())
                .modeloId(politica.getModeloId())
                .codigoCampanha(politica.getCodigoCampanha())
                .duracaoMinMinutos(politica.getDuracaoMinMinutos())
                .duracaoMaxMinutos(politica.getDuracaoMaxMinutos())
                .percentualComissao(politica.getPercentualComissao())
                .percentualExtra(politica.getPercentualExtra())
                .valorFixo(politica.getValorFixo())
                .vigenciaInicio(politica.getVigenciaInicio())
                .vigenciaFim(politica.getVigenciaFim())
                .ativa(politica.getAtiva())
                .descricao(politica.getDescricao())
                .createdAt(politica.getCreatedAt())
                .updatedAt(politica.getUpdatedAt())
                .build();
    }
}
