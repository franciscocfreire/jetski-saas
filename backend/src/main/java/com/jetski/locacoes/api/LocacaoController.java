package com.jetski.locacoes.api;

import com.jetski.locacoes.api.dto.*;
import com.jetski.locacoes.domain.Cliente;
import com.jetski.locacoes.domain.Jetski;
import com.jetski.locacoes.domain.Locacao;
import com.jetski.locacoes.domain.LocacaoStatus;
import com.jetski.locacoes.domain.Modelo;
import com.jetski.locacoes.internal.LocacaoService;
import com.jetski.locacoes.internal.repository.ClienteRepository;
import com.jetski.locacoes.internal.repository.JetskiRepository;
import com.jetski.locacoes.internal.repository.LocacaoItemOpcionalRepository;
import com.jetski.locacoes.internal.repository.ModeloRepository;
import com.jetski.shared.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Controller: LocacaoController
 *
 * REST API for rental operations (check-in and check-out).
 *
 * Endpoints:
 * - POST /v1/tenants/{tenantId}/locacoes/check-in/reserva - Check-in from reservation
 * - POST /v1/tenants/{tenantId}/locacoes/check-in/walk-in - Walk-in check-in
 * - POST /v1/tenants/{tenantId}/locacoes/{id}/check-out - Check-out
 * - GET /v1/tenants/{tenantId}/locacoes - List rentals with filters
 * - GET /v1/tenants/{tenantId}/locacoes/{id} - Get rental by ID
 *
 * Authorization:
 * - OPERADOR: Can perform check-in, check-out, list, view
 * - GERENTE: Can perform all operations + view all rentals
 * - ADMIN_TENANT: Full access
 *
 * @author Jetski Team
 * @since 0.7.0
 */
@Slf4j
@RestController
@RequestMapping("/v1/tenants/{tenantId}/locacoes")
@RequiredArgsConstructor
@Tag(name = "Locacoes", description = "Rental operations API - Check-in, Check-out, Billing")
public class LocacaoController {

    private final LocacaoService locacaoService;
    private final LocacaoItemOpcionalRepository locacaoItemOpcionalRepository;
    private final JetskiRepository jetskiRepository;
    private final ModeloRepository modeloRepository;
    private final ClienteRepository clienteRepository;

    /**
     * POST /v1/tenants/{tenantId}/locacoes/check-in/reserva
     *
     * Check-in from existing reservation
     *
     * Process:
     * 1. Validates reservation exists and is CONFIRMADA
     * 2. Validates jetski is DISPONIVEL
     * 3. Creates Locacao with EM_CURSO status
     * 4. Updates jetski status → LOCADO
     * 5. Updates reserva status → FINALIZADA
     *
     * @param tenantId Tenant ID from path
     * @param request Check-in request with reservaId and horimetroInicio
     * @return 201 Created with LocacaoResponse
     */
    @PostMapping("/check-in/reserva")
    @Operation(summary = "Check-in from reservation",
               description = "Convert confirmed reservation to active rental")
    public ResponseEntity<LocacaoResponse> checkInFromReservation(
        @PathVariable UUID tenantId,
        @Valid @RequestBody CheckInFromReservaRequest request
    ) {
        log.info("POST /v1/tenants/{}/locacoes/check-in/reserva - reserva={}",
                 tenantId, request.getReservaId());

        validateTenantContext(tenantId);

        Locacao locacao = locacaoService.checkInFromReservation(
            tenantId,
            request.getReservaId(),
            request.getHorimetroInicio(),
            request.getObservacoes(),
            request.getChecklistSaidaJson(),
            request.getValorNegociado(),
            request.getMotivoDesconto(),
            request.getModalidadePreco()
        );

        LocacaoResponse response = toResponse(locacao);

        log.info("Check-in from reservation completed: locacao={}", locacao.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * POST /v1/tenants/{tenantId}/locacoes/check-in/walk-in
     *
     * Walk-in check-in (without prior reservation)
     *
     * Process:
     * 1. Validates jetski is DISPONIVEL
     * 2. Validates cliente exists
     * 3. Creates Locacao with EM_CURSO status
     * 4. Updates jetski status → LOCADO
     *
     * @param tenantId Tenant ID from path
     * @param request Walk-in check-in request
     * @return 201 Created with LocacaoResponse
     */
    @PostMapping("/check-in/walk-in")
    @Operation(summary = "Walk-in check-in",
               description = "Create rental without prior reservation")
    public ResponseEntity<LocacaoResponse> checkInWalkIn(
        @PathVariable UUID tenantId,
        @Valid @RequestBody CheckInWalkInRequest request
    ) {
        log.info("POST /v1/tenants/{}/locacoes/check-in/walk-in - jetski={}, cliente={}",
                 tenantId, request.getJetskiId(), request.getClienteId());

        validateTenantContext(tenantId);

        Locacao locacao = locacaoService.checkInWalkIn(
            tenantId,
            request.getJetskiId(),
            request.getClienteId(),
            request.getVendedorId(),
            request.getHorimetroInicio(),
            request.getDuracaoPrevista(),
            request.getObservacoes(),
            request.getChecklistSaidaJson(),
            request.getValorNegociado(),
            request.getMotivoDesconto(),
            request.getModalidadePreco(),
            request.getDataCheckIn()
        );

        LocacaoResponse response = toResponse(locacao);

        log.info("Walk-in check-in completed: locacao={}", locacao.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * POST /v1/tenants/{tenantId}/locacoes/{id}/check-out
     *
     * Check-out and complete rental with billing calculation
     *
     * Process:
     * 1. Validates locacao is EM_CURSO
     * 2. Validates horimetro readings
     * 3. Calculates minutosUsados from horimeters
     * 4. Applies RN01: billable minutes (tolerance + 15-min rounding)
     * 5. Calculates valorBase and valorTotal
     * 6. Updates locacao status → FINALIZADA
     * 7. Updates jetski status → DISPONIVEL
     *
     * @param tenantId Tenant ID from path
     * @param id Locacao ID
     * @param request Check-out request with horimetroFim
     * @return 200 OK with LocacaoResponse including calculated values
     */
    @PostMapping("/{id}/check-out")
    @Operation(summary = "Check-out and complete rental",
               description = "Complete rental with billing calculation (RN01)")
    public ResponseEntity<LocacaoResponse> checkOut(
        @PathVariable UUID tenantId,
        @PathVariable UUID id,
        @Valid @RequestBody CheckOutRequest request
    ) {
        log.info("POST /v1/tenants/{}/locacoes/{}/check-out - horimetroFim={}",
                 tenantId, id, request.getHorimetroFim());

        validateTenantContext(tenantId);

        Locacao locacao = locacaoService.checkOut(
            tenantId,
            id,
            request.getHorimetroFim(),
            request.getObservacoes(),
            request.getChecklistEntradaJson(),
            request.getSkipPhotos()
        );

        LocacaoResponse response = toResponse(locacao);

        log.info("Check-out completed: locacao={}, value={}", id, locacao.getValorTotal());
        return ResponseEntity.ok(response);
    }

    /**
     * GET /v1/tenants/{tenantId}/locacoes/{id}
     *
     * Get rental by ID
     *
     * @param tenantId Tenant ID from path
     * @param id Locacao ID
     * @return 200 OK with LocacaoResponse
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get rental by ID")
    public ResponseEntity<LocacaoResponse> getById(
        @PathVariable UUID tenantId,
        @PathVariable UUID id
    ) {
        log.debug("GET /v1/tenants/{}/locacoes/{}", tenantId, id);

        validateTenantContext(tenantId);

        Locacao locacao = locacaoService.findById(id);
        LocacaoResponse response = toResponse(locacao);

        return ResponseEntity.ok(response);
    }

    /**
     * PATCH /v1/tenants/{tenantId}/locacoes/{id}/cliente
     *
     * Associate a client to an existing rental (for quick check-in without client)
     *
     * @param tenantId Tenant ID from path
     * @param id Locacao ID from path
     * @param request AssociarClienteRequest with clienteId
     * @return 200 OK with updated LocacaoResponse
     */
    @PatchMapping("/{id}/cliente")
    @Operation(summary = "Associate client to rental",
               description = "Associate a client to a rental that was created without one (quick check-in)")
    public ResponseEntity<LocacaoResponse> associarCliente(
        @PathVariable UUID tenantId,
        @PathVariable UUID id,
        @Valid @RequestBody AssociarClienteRequest request
    ) {
        log.info("PATCH /v1/tenants/{}/locacoes/{}/cliente - clienteId={}",
                 tenantId, id, request.getClienteId());

        validateTenantContext(tenantId);

        Locacao locacao = locacaoService.associarCliente(tenantId, id, request.getClienteId());
        LocacaoResponse response = toResponse(locacao);

        return ResponseEntity.ok(response);
    }

    /**
     * PATCH /v1/tenants/{tenantId}/locacoes/{id}/data-check-in
     *
     * Update check-in date/time of an existing rental.
     * Useful when the operator needs to correct the start time.
     *
     * @param tenantId Tenant ID from path
     * @param id Locacao ID from path
     * @param request UpdateDataCheckInRequest with new dataCheckIn
     * @return 200 OK with updated LocacaoResponse
     */
    @PatchMapping("/{id}/data-check-in")
    @Operation(summary = "Update check-in time",
               description = "Update the check-in date/time of a rental in progress")
    public ResponseEntity<LocacaoResponse> updateDataCheckIn(
        @PathVariable UUID tenantId,
        @PathVariable UUID id,
        @Valid @RequestBody UpdateDataCheckInRequest request
    ) {
        log.info("PATCH /v1/tenants/{}/locacoes/{}/data-check-in - dataCheckIn={}",
                 tenantId, id, request.getDataCheckIn());

        validateTenantContext(tenantId);

        Locacao locacao = locacaoService.updateDataCheckIn(tenantId, id, request.getDataCheckIn());
        LocacaoResponse response = toResponse(locacao);

        return ResponseEntity.ok(response);
    }

    /**
     * GET /v1/tenants/{tenantId}/locacoes
     *
     * List rentals with optional filters
     *
     * Query params:
     * - status: Filter by status (EM_CURSO, FINALIZADA, CANCELADA)
     * - jetskiId: Filter by jetski
     * - clienteId: Filter by cliente
     *
     * @param tenantId Tenant ID from path
     * @param status Optional status filter
     * @param jetskiId Optional jetski filter
     * @param clienteId Optional cliente filter
     * @return 200 OK with list of LocacaoResponse
     */
    @GetMapping
    @Operation(summary = "List rentals with filters")
    public ResponseEntity<List<LocacaoResponse>> list(
        @PathVariable UUID tenantId,
        @Parameter(description = "Filter by status") @RequestParam(required = false) LocacaoStatus status,
        @Parameter(description = "Filter by jetski") @RequestParam(required = false) UUID jetskiId,
        @Parameter(description = "Filter by cliente") @RequestParam(required = false) UUID clienteId
    ) {
        log.debug("GET /v1/tenants/{}/locacoes - status={}, jetski={}, cliente={}",
                  tenantId, status, jetskiId, clienteId);

        validateTenantContext(tenantId);

        List<Locacao> locacoes;

        if (status != null) {
            locacoes = locacaoService.listByStatus(tenantId, status);
        } else if (jetskiId != null) {
            locacoes = locacaoService.listByJetski(tenantId, jetskiId);
        } else if (clienteId != null) {
            locacoes = locacaoService.listByCliente(tenantId, clienteId);
        } else {
            locacoes = locacaoService.listByTenant(tenantId);
        }

        List<LocacaoResponse> responses = locacoes.stream()
            .map(this::toResponse)
            .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    // ===================================================================
    // Helper Methods
    // ===================================================================

    private void validateTenantContext(UUID tenantId) {
        UUID contextTenantId = TenantContext.getTenantId();
        if (!tenantId.equals(contextTenantId)) {
            log.error("Tenant mismatch: path={}, context={}", tenantId, contextTenantId);
            throw new IllegalArgumentException("Tenant ID mismatch");
        }
    }

    private LocacaoResponse toResponse(Locacao locacao) {
        // Calculate optional items total
        BigDecimal valorItensOpcionais = locacaoItemOpcionalRepository
            .sumValorCobradoByLocacaoId(locacao.getId());

        // Fetch denormalized fields for display
        String jetskiSerie = null;
        String jetskiModeloNome = null;
        String clienteNome = null;

        // Get jetski info
        Jetski jetski = jetskiRepository.findById(locacao.getJetskiId()).orElse(null);
        if (jetski != null) {
            jetskiSerie = jetski.getSerie();
            // Get modelo name
            Modelo modelo = modeloRepository.findById(jetski.getModeloId()).orElse(null);
            if (modelo != null) {
                jetskiModeloNome = modelo.getNome();
            }
        }

        // Get cliente name (if exists)
        if (locacao.getClienteId() != null) {
            Cliente cliente = clienteRepository.findById(locacao.getClienteId()).orElse(null);
            if (cliente != null) {
                clienteNome = cliente.getNome();
            }
        }

        return LocacaoResponse.builder()
            .id(locacao.getId())
            .tenantId(locacao.getTenantId())
            .reservaId(locacao.getReservaId())
            .jetskiId(locacao.getJetskiId())
            .clienteId(locacao.getClienteId())
            .vendedorId(locacao.getVendedorId())
            .jetskiSerie(jetskiSerie)
            .jetskiModeloNome(jetskiModeloNome)
            .clienteNome(clienteNome)
            .dataCheckIn(locacao.getDataCheckIn())
            .horimetroInicio(locacao.getHorimetroInicio())
            .duracaoPrevista(locacao.getDuracaoPrevista())
            .dataCheckOut(locacao.getDataCheckOut())
            .horimetroFim(locacao.getHorimetroFim())
            .minutosUsados(locacao.getMinutosUsados())
            .minutosFaturaveis(locacao.getMinutosFaturaveis())
            .valorNegociado(locacao.getValorNegociado())
            .motivoDesconto(locacao.getMotivoDesconto())
            .modalidadePreco(locacao.getModalidadePreco())
            .valorBase(locacao.getValorBase())
            .valorItensOpcionais(valorItensOpcionais)
            .valorTotal(locacao.getValorTotal())
            .status(locacao.getStatus())
            .observacoes(locacao.getObservacoes())
            .checklistSaidaJson(locacao.getChecklistSaidaJson())
            .checklistEntradaJson(locacao.getChecklistEntradaJson())
            .createdAt(locacao.getCreatedAt())
            .updatedAt(locacao.getUpdatedAt())
            .build();
    }
}
