package com.jetski.locacoes.api;

import com.jetski.locacoes.api.dto.AlocarJetskiRequest;
import com.jetski.locacoes.api.dto.ConfirmarSinalRequest;
import com.jetski.locacoes.api.dto.DisponibilidadeResponse;
import com.jetski.locacoes.api.dto.ReservaCreateRequest;
import com.jetski.locacoes.api.dto.ReservaResponse;
import com.jetski.locacoes.api.dto.ReservaUpdateRequest;
import com.jetski.locacoes.domain.Reserva;
import com.jetski.locacoes.domain.Reserva.ReservaStatus;
import com.jetski.locacoes.internal.ReservaService;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Controller: Reservation Management
 *
 * Endpoints for managing jetski reservations/bookings.
 * Handles reservation creation, confirmation, updates, and cancellation.
 * Available to ADMIN_TENANT, GERENTE, OPERADOR, and VENDEDOR roles.
 *
 * Business Rules:
 * - RN06: Cannot reserve jetski in maintenance
 * - Schedule conflict detection: prevent overlapping reservations
 * - Reservation workflow: PENDENTE → CONFIRMADA → (converted to rental on check-in)
 *
 * @author Jetski Team
 * @since 0.2.0
 */
@RestController
@RequestMapping("/v1/tenants/{tenantId}/reservas")
@Tag(name = "Reservas", description = "Gerenciamento de reservas de jetski")
@RequiredArgsConstructor
@Slf4j
public class ReservaController {

    private final ReservaService reservaService;

    /**
     * List all reservations for a tenant.
     *
     * Returns list of reservations with optional status filter.
     * Query parameter 'status' filters by reservation status.
     * Query parameter 'includeInactive' defaults to false.
     *
     * Requires: ADMIN_TENANT, GERENTE, OPERADOR, or VENDEDOR role
     *
     * @param tenantId Tenant UUID (from path)
     * @param status Optional reservation status filter
     * @param includeInactive Include cancelled/finalized reservations (default: false)
     * @return List of reservations
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE', 'OPERADOR', 'VENDEDOR')")
    @Operation(
        summary = "Listar reservas",
        description = "Lista todas as reservas do tenant. " +
                      "Por padrão, retorna apenas reservas ativas (PENDENTE, CONFIRMADA). " +
                      "Use ?includeInactive=true para incluir canceladas/finalizadas. " +
                      "Use ?status=PENDENTE para filtrar por status específico."
    )
    public ResponseEntity<List<ReservaResponse>> listReservas(
        @Parameter(description = "UUID do tenant")
        @PathVariable UUID tenantId,
        @Parameter(description = "Filtrar por status (PENDENTE, CONFIRMADA, CANCELADA, FINALIZADA)")
        @RequestParam(required = false) ReservaStatus status,
        @Parameter(description = "Incluir reservas canceladas/finalizadas")
        @RequestParam(defaultValue = "false") boolean includeInactive
    ) {
        log.info("GET /v1/tenants/{}/reservas?status={}&includeInactive={}",
                 tenantId, status, includeInactive);

        // Validate tenant context matches path parameter
        validateTenantContext(tenantId);

        List<Reserva> reservas;
        if (status != null) {
            reservas = reservaService.listByStatus(status);
        } else if (includeInactive) {
            reservas = reservaService.listAllReservations();
        } else {
            reservas = reservaService.listActiveReservations();
        }

        List<ReservaResponse> response = reservas.stream()
            .map(this::toResponse)
            .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    /**
     * Get a specific reservation by ID.
     *
     * Returns detailed information about a reservation.
     *
     * Requires: ADMIN_TENANT, GERENTE, OPERADOR, or VENDEDOR role
     *
     * @param tenantId Tenant UUID (from path)
     * @param id Reserva UUID (from path)
     * @return Reservation details
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE', 'OPERADOR', 'VENDEDOR')")
    @Operation(
        summary = "Obter reserva por ID",
        description = "Retorna os detalhes de uma reserva específica."
    )
    public ResponseEntity<ReservaResponse> getReserva(
        @Parameter(description = "UUID do tenant")
        @PathVariable UUID tenantId,
        @Parameter(description = "UUID da reserva")
        @PathVariable UUID id
    ) {
        log.info("GET /v1/tenants/{}/reservas/{}", tenantId, id);

        // Validate tenant context matches path parameter
        validateTenantContext(tenantId);

        Reserva reserva = reservaService.findById(id);
        ReservaResponse response = toResponse(reserva);

        return ResponseEntity.ok(response);
    }

    /**
     * Create a new reservation.
     *
     * Creates a new jetski reservation/booking.
     *
     * Validations:
     * - Jetski must be DISPONIVEL (RN06)
     * - No schedule conflicts
     * - Start date before end date
     * - Start date cannot be in past
     *
     * Requires: ADMIN_TENANT, GERENTE, OPERADOR, or VENDEDOR role
     *
     * @param tenantId Tenant UUID (from path)
     * @param request Reservation creation request
     * @return Created reservation details
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE', 'OPERADOR', 'VENDEDOR')")
    @Operation(
        summary = "Criar nova reserva",
        description = "Cria uma nova reserva de jetski. " +
                      "Valida disponibilidade do jetski (RN06) e detecta conflitos de agenda. " +
                      "Reserva criada com status PENDENTE (requer confirmação)."
    )
    public ResponseEntity<ReservaResponse> createReserva(
        @Parameter(description = "UUID do tenant")
        @PathVariable UUID tenantId,
        @Valid @RequestBody ReservaCreateRequest request
    ) {
        log.info("POST /v1/tenants/{}/reservas - jetski: {}, cliente: {}",
                 tenantId, request.getJetskiId(), request.getClienteId());

        // Validate tenant context matches path parameter
        validateTenantContext(tenantId);

        Reserva reserva = toEntity(request, tenantId);
        Reserva created = reservaService.createReserva(reserva);
        ReservaResponse response = toResponse(created);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Update an existing reservation.
     *
     * Updates reservation information (dates, notes).
     *
     * Validations:
     * - Can only update PENDENTE or CONFIRMADA reservations
     * - If dates changed, validates no conflicts
     * - Start date must be before end date
     *
     * Requires: ADMIN_TENANT, GERENTE, or OPERADOR role
     *
     * @param tenantId Tenant UUID (from path)
     * @param id Reserva UUID (from path)
     * @param request Reservation update request
     * @return Updated reservation details
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE', 'OPERADOR')")
    @Operation(
        summary = "Atualizar reserva",
        description = "Atualiza as informações de uma reserva existente. " +
                      "Apenas reservas PENDENTE ou CONFIRMADA podem ser atualizadas. " +
                      "Se datas forem alteradas, valida conflitos de agenda."
    )
    public ResponseEntity<ReservaResponse> updateReserva(
        @Parameter(description = "UUID do tenant")
        @PathVariable UUID tenantId,
        @Parameter(description = "UUID da reserva")
        @PathVariable UUID id,
        @Valid @RequestBody ReservaUpdateRequest request
    ) {
        log.info("PUT /v1/tenants/{}/reservas/{}", tenantId, id);

        // Validate tenant context matches path parameter
        validateTenantContext(tenantId);

        Reserva updates = toEntity(request);
        Reserva updated = reservaService.updateReserva(id, updates);
        ReservaResponse response = toResponse(updated);

        return ResponseEntity.ok(response);
    }

    /**
     * Confirm a pending reservation.
     *
     * Transition: PENDENTE → CONFIRMADA
     *
     * Re-validates jetski availability and checks for conflicts.
     *
     * Requires: ADMIN_TENANT, GERENTE, or OPERADOR role
     *
     * @param tenantId Tenant UUID (from path)
     * @param id Reserva UUID (from path)
     * @return Confirmed reservation details
     */
    @PostMapping("/{id}/confirmar")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE', 'OPERADOR')")
    @Operation(
        summary = "Confirmar reserva pendente",
        description = "Confirma uma reserva com status PENDENTE. " +
                      "Re-valida disponibilidade do jetski e conflitos de agenda antes de confirmar."
    )
    public ResponseEntity<ReservaResponse> confirmReserva(
        @Parameter(description = "UUID do tenant")
        @PathVariable UUID tenantId,
        @Parameter(description = "UUID da reserva")
        @PathVariable UUID id
    ) {
        log.info("POST /v1/tenants/{}/reservas/{}/confirmar", tenantId, id);

        // Validate tenant context matches path parameter
        validateTenantContext(tenantId);

        Reserva confirmed = reservaService.confirmReservation(id);
        ReservaResponse response = toResponse(confirmed);

        return ResponseEntity.ok(response);
    }

    /**
     * Cancel a reservation.
     *
     * Soft-cancel: sets status to CANCELADA.
     * Can cancel PENDENTE or CONFIRMADA reservations.
     *
     * Requires: ADMIN_TENANT, GERENTE, or OPERADOR role
     *
     * @param tenantId Tenant UUID (from path)
     * @param id Reserva UUID (from path)
     * @return Cancelled reservation details
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE', 'OPERADOR')")
    @Operation(
        summary = "Cancelar reserva",
        description = "Cancela uma reserva (soft delete, altera status para CANCELADA). " +
                      "Apenas reservas PENDENTE ou CONFIRMADA podem ser canceladas."
    )
    public ResponseEntity<ReservaResponse> cancelReserva(
        @Parameter(description = "UUID do tenant")
        @PathVariable UUID tenantId,
        @Parameter(description = "UUID da reserva")
        @PathVariable UUID id
    ) {
        log.info("DELETE /v1/tenants/{}/reservas/{}", tenantId, id);

        // Validate tenant context matches path parameter
        validateTenantContext(tenantId);

        Reserva cancelled = reservaService.cancelReservation(id);
        ReservaResponse response = toResponse(cancelled);

        return ResponseEntity.ok(response);
    }

    /**
     * Confirm deposit payment for a reservation.
     *
     * Upgrades reservation from BAIXA to ALTA priority.
     * Transition: BAIXA priority → ALTA priority (guaranteed)
     *
     * Validations:
     * - Reservation must be PENDENTE or CONFIRMADA
     * - Deposit not already paid
     * - Physical capacity available for guaranteed reservations
     * - Deposit amount must be positive
     *
     * Requires: ADMIN_TENANT, GERENTE, or OPERADOR role
     *
     * @param tenantId Tenant UUID (from path)
     * @param id Reserva UUID (from path)
     * @param request Deposit confirmation request
     * @return Updated reservation with ALTA priority
     */
    @PostMapping("/{id}/confirmar-sinal")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE', 'OPERADOR')")
    @Operation(
        summary = "Confirmar pagamento de sinal",
        description = "Confirma o pagamento do sinal e atualiza a reserva para prioridade ALTA. " +
                      "Reservas com sinal são garantidas e não expiram automaticamente. " +
                      "Valida capacidade física antes de confirmar."
    )
    public ResponseEntity<ReservaResponse> confirmarSinal(
        @Parameter(description = "UUID do tenant")
        @PathVariable UUID tenantId,
        @Parameter(description = "UUID da reserva")
        @PathVariable UUID id,
        @Valid @RequestBody ConfirmarSinalRequest request
    ) {
        log.info("POST /v1/tenants/{}/reservas/{}/confirmar-sinal - valor: {}",
                 tenantId, id, request.getValorSinal());

        // Validate tenant context matches path parameter
        validateTenantContext(tenantId);

        Reserva upgraded = reservaService.confirmarSinal(id, request.getValorSinal());
        ReservaResponse response = toResponse(upgraded);

        return ResponseEntity.ok(response);
    }

    /**
     * Allocate specific jetski to a reservation.
     *
     * Used when:
     * - Customer arrives for check-in
     * - Operator pre-assigns a specific jetski
     * - Customer requested particular unit
     *
     * Validations:
     * - Reservation must be CONFIRMADA
     * - No jetski allocated yet (jetski_id is null)
     * - Jetski must belong to reserved modelo
     * - Jetski must be DISPONIVEL
     * - No schedule conflicts for the jetski
     *
     * Requires: ADMIN_TENANT, GERENTE, or OPERADOR role
     *
     * @param tenantId Tenant UUID (from path)
     * @param id Reserva UUID (from path)
     * @param request Jetski allocation request
     * @return Updated reservation with allocated jetski
     */
    @PostMapping("/{id}/alocar-jetski")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE', 'OPERADOR')")
    @Operation(
        summary = "Alocar jetski específico à reserva",
        description = "Aloca um jetski específico à reserva. " +
                      "Usado durante check-in ou pré-alocação. " +
                      "Valida que jetski pertence ao modelo e está disponível."
    )
    public ResponseEntity<ReservaResponse> alocarJetski(
        @Parameter(description = "UUID do tenant")
        @PathVariable UUID tenantId,
        @Parameter(description = "UUID da reserva")
        @PathVariable UUID id,
        @Valid @RequestBody AlocarJetskiRequest request
    ) {
        log.info("POST /v1/tenants/{}/reservas/{}/alocar-jetski - jetski: {}",
                 tenantId, id, request.getJetskiId());

        // Validate tenant context matches path parameter
        validateTenantContext(tenantId);

        Reserva allocated = reservaService.alocarJetski(id, request.getJetskiId());
        ReservaResponse response = toResponse(allocated);

        return ResponseEntity.ok(response);
    }

    /**
     * Check modelo availability for a given period.
     *
     * Returns detailed metrics:
     * - Total available jetskis for modelo
     * - Current guaranteed and regular reservations
     * - Maximum reservations allowed (overbooking limit)
     * - Whether new reservations can be accepted (with/without deposit)
     * - Remaining slots
     *
     * Public endpoint - can be called without authentication for customer-facing availability check.
     *
     * @param tenantId Tenant UUID (from path)
     * @param modeloId Modelo UUID (query param)
     * @param dataInicio Start of period (query param)
     * @param dataFimPrevista End of period (query param)
     * @return Detailed availability information
     */
    @GetMapping("/disponibilidade")
    @Operation(
        summary = "Verificar disponibilidade de modelo",
        description = "Retorna informações detalhadas sobre disponibilidade de um modelo para um período. " +
                      "Inclui métricas de capacidade, reservas garantidas/regulares, vagas restantes. " +
                      "Endpoint público para consulta de disponibilidade."
    )
    public ResponseEntity<DisponibilidadeResponse> verificarDisponibilidade(
        @Parameter(description = "UUID do tenant")
        @PathVariable UUID tenantId,
        @Parameter(description = "UUID do modelo")
        @RequestParam UUID modeloId,
        @Parameter(description = "Data/hora de início do período")
        @RequestParam LocalDateTime dataInicio,
        @Parameter(description = "Data/hora de fim do período")
        @RequestParam LocalDateTime dataFimPrevista
    ) {
        log.info("GET /v1/tenants/{}/reservas/disponibilidade?modeloId={}&dataInicio={}&dataFimPrevista={}",
                 tenantId, modeloId, dataInicio, dataFimPrevista);

        // Validate tenant context matches path parameter
        validateTenantContext(tenantId);

        // Get detailed availability from service
        ReservaService.DisponibilidadeDetalhada detalhada =
            reservaService.verificarDisponibilidadeDetalhada(modeloId, dataInicio, dataFimPrevista);

        // Convert to response DTO
        DisponibilidadeResponse response = DisponibilidadeResponse.builder()
            .modeloId(detalhada.getModeloId())
            .modeloNome(detalhada.getModeloNome())
            .dataInicio(detalhada.getDataInicio())
            .dataFimPrevista(detalhada.getDataFimPrevista())
            .totalJetskis(detalhada.getTotalJetskis())
            .reservasGarantidas(detalhada.getReservasGarantidas())
            .totalReservas(detalhada.getTotalReservas())
            .maximoReservas(detalhada.getMaximoReservas())
            .aceitaComSinal(detalhada.isAceitaComSinal())
            .aceitaSemSinal(detalhada.isAceitaSemSinal())
            .vagasGarantidas(detalhada.getVagasGarantidas())
            .vagasRegulares(detalhada.getVagasRegulares())
            .build();

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

    private ReservaResponse toResponse(Reserva reserva) {
        return ReservaResponse.builder()
            .id(reserva.getId())
            .tenantId(reserva.getTenantId())
            .modeloId(reserva.getModeloId())
            .jetskiId(reserva.getJetskiId())
            .clienteId(reserva.getClienteId())
            .vendedorId(reserva.getVendedorId())
            .dataInicio(reserva.getDataInicio())
            .dataFimPrevista(reserva.getDataFimPrevista())
            .expiraEm(reserva.getExpiraEm())
            .status(reserva.getStatus())
            .prioridade(reserva.getPrioridade())
            .sinalPago(reserva.getSinalPago())
            .valorSinal(reserva.getValorSinal())
            .sinalPagoEm(reserva.getSinalPagoEm())
            .observacoes(reserva.getObservacoes())
            .ativo(reserva.getAtivo())
            .createdAt(reserva.getCreatedAt())
            .updatedAt(reserva.getUpdatedAt())
            .build();
    }

    private Reserva toEntity(ReservaCreateRequest request, UUID tenantId) {
        return Reserva.builder()
            .tenantId(tenantId)
            .modeloId(request.getModeloId())
            .jetskiId(request.getJetskiId())
            .clienteId(request.getClienteId())
            .vendedorId(request.getVendedorId())
            .dataInicio(request.getDataInicio())
            .dataFimPrevista(request.getDataFimPrevista())
            .observacoes(request.getObservacoes())
            .status(ReservaStatus.PENDENTE)
            .ativo(true)
            .build();
    }

    private Reserva toEntity(ReservaUpdateRequest request) {
        return Reserva.builder()
            .dataInicio(request.getDataInicio())
            .dataFimPrevista(request.getDataFimPrevista())
            .observacoes(request.getObservacoes())
            .build();
    }
}
