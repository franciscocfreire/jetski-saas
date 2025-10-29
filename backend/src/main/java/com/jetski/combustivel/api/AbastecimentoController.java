package com.jetski.combustivel.api;

import com.jetski.combustivel.api.dto.AbastecimentoCreateRequest;
import com.jetski.combustivel.api.dto.AbastecimentoResponse;
import com.jetski.combustivel.domain.Abastecimento;
import com.jetski.combustivel.domain.TipoAbastecimento;
import com.jetski.combustivel.internal.AbastecimentoService;
import com.jetski.shared.exception.NotFoundException;
import com.jetski.shared.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Controller: AbastecimentoController
 *
 * REST API for fuel refill management.
 *
 * Endpoints:
 * - POST /v1/tenants/{tenantId}/abastecimentos - Register new refill
 * - GET /v1/tenants/{tenantId}/abastecimentos - List refills with filters
 * - GET /v1/tenants/{tenantId}/abastecimentos/{id} - Get refill by ID
 * - GET /v1/tenants/{tenantId}/abastecimentos/jetski/{jetskiId} - List by jetski
 * - GET /v1/tenants/{tenantId}/abastecimentos/locacao/{locacaoId} - List by rental
 *
 * Authorization:
 * - OPERADOR: Can register refills, view
 * - GERENTE: Full access
 * - ADMIN_TENANT: Full access
 *
 * @author Jetski Team
 * @since 0.8.0
 */
@Slf4j
@RestController
@RequestMapping("/v1/tenants/{tenantId}/abastecimentos")
@RequiredArgsConstructor
@Tag(name = "Combustivel", description = "Fuel management API - Refills, Policies, Pricing")
public class AbastecimentoController {

    private final AbastecimentoService abastecimentoService;

    /**
     * POST /v1/tenants/{tenantId}/abastecimentos
     *
     * Register a new fuel refill.
     * Automatically updates daily fuel price average.
     *
     * @param tenantId Tenant ID from path
     * @param request Refill request data
     * @return 201 Created with AbastecimentoResponse
     */
    @PostMapping
    @Operation(summary = "Register fuel refill",
               description = "Register new fuel refill and update daily price average")
    public ResponseEntity<AbastecimentoResponse> registrar(
        @PathVariable UUID tenantId,
        @Valid @RequestBody AbastecimentoCreateRequest request
    ) {
        log.info("POST /v1/tenants/{}/abastecimentos - jetski={}, tipo={}",
                 tenantId, request.getJetskiId(), request.getTipo());

        validateTenantContext(tenantId);
        UUID responsavelId = TenantContext.getUsuarioId();

        Abastecimento abastecimento = mapToEntity(request);
        Abastecimento saved = abastecimentoService.registrar(tenantId, responsavelId, abastecimento);

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(mapToResponse(saved));
    }

    /**
     * GET /v1/tenants/{tenantId}/abastecimentos
     *
     * List refills with optional filters.
     *
     * @param tenantId Tenant ID
     * @param jetskiId Optional: filter by jetski
     * @param tipo Optional: filter by type
     * @param dataInicio Optional: filter by start date
     * @param dataFim Optional: filter by end date
     * @return 200 OK with list of AbastecimentoResponse
     */
    @GetMapping
    @Operation(summary = "List refills",
               description = "List all refills with optional filters")
    public ResponseEntity<List<AbastecimentoResponse>> listar(
        @PathVariable UUID tenantId,
        @RequestParam(required = false) UUID jetskiId,
        @RequestParam(required = false) TipoAbastecimento tipo,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim
    ) {
        log.info("GET /v1/tenants/{}/abastecimentos - jetski={}, tipo={}, periodo={} a {}",
                 tenantId, jetskiId, tipo, dataInicio, dataFim);

        validateTenantContext(tenantId);

        List<Abastecimento> result;
        if (jetskiId != null) {
            result = abastecimentoService.listarPorJetski(tenantId, jetskiId, dataInicio, dataFim);
        } else if (tipo != null) {
            result = abastecimentoService.listarPorTipo(tenantId, tipo, dataInicio, dataFim);
        } else {
            result = abastecimentoService.listarTodos(tenantId, dataInicio, dataFim);
        }

        List<AbastecimentoResponse> responses = result.stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    /**
     * GET /v1/tenants/{tenantId}/abastecimentos/{id}
     *
     * Get refill by ID.
     *
     * @param tenantId Tenant ID
     * @param id Refill ID
     * @return 200 OK with AbastecimentoResponse
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get refill by ID")
    public ResponseEntity<AbastecimentoResponse> buscarPorId(
        @PathVariable UUID tenantId,
        @PathVariable Long id
    ) {
        log.info("GET /v1/tenants/{}/abastecimentos/{}", tenantId, id);

        validateTenantContext(tenantId);

        Abastecimento abastecimento = abastecimentoService.buscarPorId(tenantId, id)
            .orElseThrow(() -> new NotFoundException("Abastecimento não encontrado: " + id));

        return ResponseEntity.ok(mapToResponse(abastecimento));
    }

    /**
     * GET /v1/tenants/{tenantId}/abastecimentos/jetski/{jetskiId}
     *
     * List refills by jetski.
     *
     * @param tenantId Tenant ID
     * @param jetskiId Jetski ID
     * @return 200 OK with list of AbastecimentoResponse
     */
    @GetMapping("/jetski/{jetskiId}")
    @Operation(summary = "List refills by jetski")
    public ResponseEntity<List<AbastecimentoResponse>> listarPorJetski(
        @PathVariable UUID tenantId,
        @PathVariable UUID jetskiId
    ) {
        log.info("GET /v1/tenants/{}/abastecimentos/jetski/{}", tenantId, jetskiId);

        validateTenantContext(tenantId);

        List<Abastecimento> result = abastecimentoService.listarPorJetski(tenantId, jetskiId, null, null);

        List<AbastecimentoResponse> responses = result.stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    /**
     * GET /v1/tenants/{tenantId}/abastecimentos/locacao/{locacaoId}
     *
     * List refills by rental.
     *
     * @param tenantId Tenant ID
     * @param locacaoId Rental ID
     * @return 200 OK with list of AbastecimentoResponse
     */
    @GetMapping("/locacao/{locacaoId}")
    @Operation(summary = "List refills by rental")
    public ResponseEntity<List<AbastecimentoResponse>> listarPorLocacao(
        @PathVariable UUID tenantId,
        @PathVariable UUID locacaoId
    ) {
        log.info("GET /v1/tenants/{}/abastecimentos/locacao/{}", tenantId, locacaoId);

        validateTenantContext(tenantId);

        List<Abastecimento> result = abastecimentoService.listarPorLocacao(tenantId, locacaoId);

        List<AbastecimentoResponse> responses = result.stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    // =====================================================
    // Helper Methods
    // =====================================================

    private void validateTenantContext(UUID tenantId) {
        if (!TenantContext.getTenantId().equals(tenantId)) {
            throw new IllegalArgumentException("Tenant ID mismatch");
        }
    }

    private Abastecimento mapToEntity(AbastecimentoCreateRequest request) {
        return Abastecimento.builder()
            .jetskiId(request.getJetskiId())
            .locacaoId(request.getLocacaoId())
            .tipo(request.getTipo())
            .litros(request.getLitros())
            .precoLitro(request.getPrecoLitro())
            .custoTotal(request.getCustoTotal())
            .dataHora(request.getDataHora())
            .observacoes(request.getObservacoes())
            .build();
    }

    private AbastecimentoResponse mapToResponse(Abastecimento entity) {
        return AbastecimentoResponse.builder()
            .id(entity.getId())
            .tenantId(entity.getTenantId())
            .jetskiId(entity.getJetskiId())
            .locacaoId(entity.getLocacaoId())
            .tipo(entity.getTipo())
            .litros(entity.getLitros())
            .precoLitro(entity.getPrecoLitro())
            .custoTotal(entity.getCustoTotal())
            .dataHora(entity.getDataHora())
            .observacoes(entity.getObservacoes())
            .responsavelId(entity.getResponsavelId())
            .createdAt(entity.getCreatedAt())
            .updatedAt(entity.getUpdatedAt())
            .build();
    }
}
