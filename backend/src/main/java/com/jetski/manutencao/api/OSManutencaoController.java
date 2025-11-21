package com.jetski.manutencao.api;

import com.jetski.manutencao.api.dto.JetskiDisponibilidadeResponse;
import com.jetski.manutencao.api.dto.OSManutencaoCreateRequest;
import com.jetski.manutencao.api.dto.OSManutencaoFinishRequest;
import com.jetski.manutencao.api.dto.OSManutencaoResponse;
import com.jetski.manutencao.api.dto.OSManutencaoUpdateRequest;
import com.jetski.manutencao.domain.OSManutencao;
import com.jetski.manutencao.domain.OSManutencaoStatus;
import com.jetski.manutencao.domain.OSManutencaoTipo;
import com.jetski.manutencao.internal.OSManutencaoService;
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
 * Controller: OS Manutenção Management
 *
 * <p>Endpoints for managing maintenance orders (preventive, corrective, inspection).
 * Implements business rules:
 * <ul>
 *   <li><b>RN06</b>: Active OS blocks jetski availability (status=MANUTENCAO)</li>
 *   <li><b>RN06.1</b>: Jetski in MANUTENCAO cannot be reserved</li>
 *   <li>Status workflow: ABERTA → EM_ANDAMENTO → CONCLUIDA</li>
 * </ul>
 *
 * <p>Available to ADMIN_TENANT, GERENTE, MECANICO, and OPERADOR roles (read-only).
 *
 * @author Jetski Team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/v1/tenants/{tenantId}/manutencoes")
@Tag(name = "Manutenção", description = "Gerenciamento de ordens de serviço (preventiva e corretiva)")
@RequiredArgsConstructor
@Slf4j
public class OSManutencaoController {

    private final OSManutencaoService osManutencaoService;

    /**
     * List all maintenance orders for a tenant.
     *
     * <p>Returns list of maintenance orders with filters:
     * <ul>
     *   <li>includeFinished (default: true) - include completed/cancelled orders</li>
     *   <li>jetskiId - filter by specific jetski</li>
     *   <li>mecanicoId - filter by specific mechanic</li>
     *   <li>status - filter by status</li>
     *   <li>tipo - filter by type</li>
     * </ul>
     *
     * <p>Requires: ADMIN_TENANT, GERENTE, MECANICO, or OPERADOR role
     *
     * @param tenantId Tenant UUID (from path)
     * @param includeFinished Include finished orders (default: true)
     * @param jetskiId Filter by jetski UUID (optional)
     * @param mecanicoId Filter by mechanic UUID (optional)
     * @param status Filter by status (optional)
     * @param tipo Filter by type (optional)
     * @return List of maintenance orders
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE', 'MECANICO', 'OPERADOR')")
    @Operation(
        summary = "Listar ordens de serviço",
        description = "Lista todas as ordens de serviço de manutenção do tenant. " +
                      "Por padrão, inclui OSs finalizadas. Use filtros para refinar a busca."
    )
    public ResponseEntity<List<OSManutencaoResponse>> listOrdens(
        @Parameter(description = "UUID do tenant")
        @PathVariable UUID tenantId,
        @Parameter(description = "Incluir OSs finalizadas (concluídas/canceladas)")
        @RequestParam(defaultValue = "true") boolean includeFinished,
        @Parameter(description = "Filtrar por jetski UUID")
        @RequestParam(required = false) UUID jetskiId,
        @Parameter(description = "Filtrar por mecânico UUID")
        @RequestParam(required = false) UUID mecanicoId,
        @Parameter(description = "Filtrar por status")
        @RequestParam(required = false) OSManutencaoStatus status,
        @Parameter(description = "Filtrar por tipo")
        @RequestParam(required = false) OSManutencaoTipo tipo
    ) {
        log.info("GET /v1/tenants/{}/manutencoes - includeFinished={}, jetskiId={}, mecanicoId={}, status={}, tipo={}",
                 tenantId, includeFinished, jetskiId, mecanicoId, status, tipo);

        validateTenantContext(tenantId);

        List<OSManutencao> ordens;

        // Apply filters
        if (jetskiId != null) {
            ordens = osManutencaoService.listOrdersByJetski(jetskiId);
        } else if (mecanicoId != null) {
            ordens = osManutencaoService.listOrdersByMechanic(mecanicoId);
        } else if (status != null) {
            ordens = osManutencaoService.listOrdersByStatus(status);
        } else if (tipo != null) {
            ordens = osManutencaoService.listOrdersByType(tipo);
        } else if (includeFinished) {
            ordens = osManutencaoService.listAllOrders();
        } else {
            ordens = osManutencaoService.listActiveOrders();
        }

        List<OSManutencaoResponse> response = ordens.stream()
            .map(this::toResponse)
            .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    /**
     * Get a specific maintenance order by ID.
     *
     * <p>Returns detailed information about a maintenance order.
     *
     * <p>Requires: ADMIN_TENANT, GERENTE, MECANICO, or OPERADOR role
     *
     * @param tenantId Tenant UUID (from path)
     * @param id OS UUID (from path)
     * @return Maintenance order details
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE', 'MECANICO', 'OPERADOR')")
    @Operation(
        summary = "Obter OS por ID",
        description = "Retorna os detalhes de uma ordem de serviço específica."
    )
    public ResponseEntity<OSManutencaoResponse> getOrdem(
        @Parameter(description = "UUID do tenant")
        @PathVariable UUID tenantId,
        @Parameter(description = "UUID da OS")
        @PathVariable UUID id
    ) {
        log.info("GET /v1/tenants/{}/manutencoes/{}", tenantId, id);

        validateTenantContext(tenantId);

        OSManutencao os = osManutencaoService.findById(id);
        OSManutencaoResponse response = toResponse(os);

        return ResponseEntity.ok(response);
    }

    /**
     * Create a new maintenance order.
     *
     * <p>Creates a new OS with status=ABERTA.
     *
     * <p>Validations:
     * <ul>
     *   <li>Jetski must exist and be active</li>
     *   <li>Problem description is mandatory</li>
     * </ul>
     *
     * <p>Side effects:
     * <ul>
     *   <li>RN06: Sets jetski status to MANUTENCAO</li>
     * </ul>
     *
     * <p>Requires: ADMIN_TENANT or GERENTE role
     *
     * @param tenantId Tenant UUID (from path)
     * @param request OS creation request
     * @return Created maintenance order details
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE')")
    @Operation(
        summary = "Criar nova ordem de serviço",
        description = "Cria uma nova OS de manutenção. O jetski será bloqueado (status=MANUTENCAO) automaticamente."
    )
    public ResponseEntity<OSManutencaoResponse> createOrdem(
        @Parameter(description = "UUID do tenant")
        @PathVariable UUID tenantId,
        @Valid @RequestBody OSManutencaoCreateRequest request
    ) {
        log.info("POST /v1/tenants/{}/manutencoes - jetski={}, tipo={}",
                 tenantId, request.getJetskiId(), request.getTipo());

        validateTenantContext(tenantId);

        OSManutencao os = toEntity(request, tenantId);
        OSManutencao created = osManutencaoService.createOrder(os);
        OSManutencaoResponse response = toResponse(created);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Update an existing maintenance order.
     *
     * <p>Updates OS information (mechanic, dates, diagnosis, solution, parts, costs).
     * Status changes should use dedicated endpoints (start, finish, cancel, etc.).
     *
     * <p>Validations:
     * <ul>
     *   <li>OS must exist and not be finished</li>
     * </ul>
     *
     * <p>Requires: ADMIN_TENANT, GERENTE, or MECANICO role
     *
     * @param tenantId Tenant UUID (from path)
     * @param id OS UUID (from path)
     * @param request OS update request
     * @return Updated maintenance order details
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE', 'MECANICO')")
    @Operation(
        summary = "Atualizar ordem de serviço",
        description = "Atualiza as informações de uma OS existente. " +
                      "Para mudar o status, use os endpoints dedicados (/start, /finish, /cancel)."
    )
    public ResponseEntity<OSManutencaoResponse> updateOrdem(
        @Parameter(description = "UUID do tenant")
        @PathVariable UUID tenantId,
        @Parameter(description = "UUID da OS")
        @PathVariable UUID id,
        @Valid @RequestBody OSManutencaoUpdateRequest request
    ) {
        log.info("PUT /v1/tenants/{}/manutencoes/{}", tenantId, id);

        validateTenantContext(tenantId);

        OSManutencao updates = toEntity(request);
        OSManutencao updated = osManutencaoService.updateOrder(id, updates);
        OSManutencaoResponse response = toResponse(updated);

        return ResponseEntity.ok(response);
    }

    /**
     * Start work on maintenance order.
     *
     * <p>Transitions status: ABERTA → EM_ANDAMENTO
     * Sets dtInicioReal to current timestamp.
     *
     * <p>Requires: ADMIN_TENANT, GERENTE, or MECANICO role
     *
     * @param tenantId Tenant UUID (from path)
     * @param id OS UUID (from path)
     * @return Updated maintenance order details
     */
    @PostMapping("/{id}/start")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE', 'MECANICO')")
    @Operation(
        summary = "Iniciar trabalho na OS",
        description = "Muda status de ABERTA para EM_ANDAMENTO e registra data/hora de início."
    )
    public ResponseEntity<OSManutencaoResponse> startOrdem(
        @Parameter(description = "UUID do tenant")
        @PathVariable UUID tenantId,
        @Parameter(description = "UUID da OS")
        @PathVariable UUID id
    ) {
        log.info("POST /v1/tenants/{}/manutencoes/{}/start", tenantId, id);

        validateTenantContext(tenantId);

        OSManutencao started = osManutencaoService.startOrder(id);
        OSManutencaoResponse response = toResponse(started);

        return ResponseEntity.ok(response);
    }

    /**
     * Mark order as waiting for parts.
     *
     * <p>Transitions status: EM_ANDAMENTO → AGUARDANDO_PECAS
     *
     * <p>Requires: ADMIN_TENANT, GERENTE, or MECANICO role
     *
     * @param tenantId Tenant UUID (from path)
     * @param id OS UUID (from path)
     * @return Updated maintenance order details
     */
    @PostMapping("/{id}/wait-for-parts")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE', 'MECANICO')")
    @Operation(
        summary = "Marcar OS como aguardando peças",
        description = "Muda status de EM_ANDAMENTO para AGUARDANDO_PECAS."
    )
    public ResponseEntity<OSManutencaoResponse> waitForParts(
        @Parameter(description = "UUID do tenant")
        @PathVariable UUID tenantId,
        @Parameter(description = "UUID da OS")
        @PathVariable UUID id
    ) {
        log.info("POST /v1/tenants/{}/manutencoes/{}/wait-for-parts", tenantId, id);

        validateTenantContext(tenantId);

        OSManutencao waiting = osManutencaoService.waitForParts(id);
        OSManutencaoResponse response = toResponse(waiting);

        return ResponseEntity.ok(response);
    }

    /**
     * Resume work after parts arrive.
     *
     * <p>Transitions status: AGUARDANDO_PECAS → EM_ANDAMENTO
     *
     * <p>Requires: ADMIN_TENANT, GERENTE, or MECANICO role
     *
     * @param tenantId Tenant UUID (from path)
     * @param id OS UUID (from path)
     * @return Updated maintenance order details
     */
    @PostMapping("/{id}/resume")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE', 'MECANICO')")
    @Operation(
        summary = "Retomar trabalho na OS",
        description = "Muda status de AGUARDANDO_PECAS para EM_ANDAMENTO."
    )
    public ResponseEntity<OSManutencaoResponse> resumeOrdem(
        @Parameter(description = "UUID do tenant")
        @PathVariable UUID tenantId,
        @Parameter(description = "UUID da OS")
        @PathVariable UUID id
    ) {
        log.info("POST /v1/tenants/{}/manutencoes/{}/resume", tenantId, id);

        validateTenantContext(tenantId);

        OSManutencao resumed = osManutencaoService.resumeOrder(id);
        OSManutencaoResponse response = toResponse(resumed);

        return ResponseEntity.ok(response);
    }

    /**
     * Finish maintenance order.
     *
     * <p>Transitions status: EM_ANDAMENTO → CONCLUIDA
     * Sets dtConclusao to current timestamp.
     * Captures final odometer reading.
     *
     * <p>Side effects:
     * <ul>
     *   <li>Sets jetski status to DISPONIVEL if no other active OS exists</li>
     * </ul>
     *
     * <p>Requires: ADMIN_TENANT, GERENTE, or MECANICO role
     *
     * @param tenantId Tenant UUID (from path)
     * @param id OS UUID (from path)
     * @return Updated maintenance order details
     */
    @PostMapping("/{id}/finish")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE', 'MECANICO')")
    @Operation(
        summary = "Finalizar OS",
        description = "Muda status de EM_ANDAMENTO para CONCLUIDA. " +
                      "O jetski será liberado (status=DISPONIVEL) se não houver outras OSs ativas."
    )
    public ResponseEntity<OSManutencaoResponse> finishOrdem(
        @Parameter(description = "UUID do tenant")
        @PathVariable UUID tenantId,
        @Parameter(description = "UUID da OS")
        @PathVariable UUID id,
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Dados de finalização da OS",
            required = true
        )
        @RequestBody @Valid OSManutencaoFinishRequest request
    ) {
        log.info("POST /v1/tenants/{}/manutencoes/{}/finish", tenantId, id);

        validateTenantContext(tenantId);

        OSManutencao finished = osManutencaoService.finishOrder(id, request);
        OSManutencaoResponse response = toResponse(finished);

        return ResponseEntity.ok(response);
    }

    /**
     * Cancel maintenance order.
     *
     * <p>Transitions status: * → CANCELADA
     *
     * <p>Side effects:
     * <ul>
     *   <li>Sets jetski status to DISPONIVEL if no other active OS exists</li>
     * </ul>
     *
     * <p>Requires: ADMIN_TENANT or GERENTE role
     *
     * @param tenantId Tenant UUID (from path)
     * @param id OS UUID (from path)
     * @return Updated maintenance order details
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE')")
    @Operation(
        summary = "Cancelar OS",
        description = "Cancela uma ordem de serviço. " +
                      "O jetski será liberado (status=DISPONIVEL) se não houver outras OSs ativas."
    )
    public ResponseEntity<OSManutencaoResponse> cancelOrdem(
        @Parameter(description = "UUID do tenant")
        @PathVariable UUID tenantId,
        @Parameter(description = "UUID da OS")
        @PathVariable UUID id
    ) {
        log.info("DELETE /v1/tenants/{}/manutencoes/{}", tenantId, id);

        validateTenantContext(tenantId);

        OSManutencao cancelled = osManutencaoService.cancelOrder(id);
        OSManutencaoResponse response = toResponse(cancelled);

        return ResponseEntity.ok(response);
    }

    /**
     * Check if jetski has active maintenance.
     *
     * <p>Business Rule RN06.1: Jetski with active OS cannot be reserved.
     *
     * <p>Requires: ADMIN_TENANT, GERENTE, or OPERADOR role
     *
     * @param tenantId Tenant UUID (from path)
     * @param jetskiId Jetski UUID (from query)
     * @return true if jetski has active maintenance
     */
    @GetMapping("/check-availability")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE', 'OPERADOR')")
    @Operation(
        summary = "Verificar disponibilidade de jetski",
        description = "Verifica se jetski possui OSs ativas (bloqueando reservas)."
    )
    public ResponseEntity<Boolean> checkAvailability(
        @Parameter(description = "UUID do tenant")
        @PathVariable UUID tenantId,
        @Parameter(description = "UUID do jetski")
        @RequestParam UUID jetskiId
    ) {
        log.info("GET /v1/tenants/{}/manutencoes/check-availability?jetskiId={}", tenantId, jetskiId);

        validateTenantContext(tenantId);

        boolean hasActiveMaintenance = osManutencaoService.hasActiveMaintenance(jetskiId);

        return ResponseEntity.ok(hasActiveMaintenance);
    }

    /**
     * Check jetski availability (RN06).
     *
     * <p>Returns detailed availability status with reason if unavailable.
     *
     * <p>Requires: ADMIN_TENANT, GERENTE, or OPERADOR role
     *
     * @param tenantId Tenant UUID (from path)
     * @param jetskiId Jetski UUID (from path)
     * @return Availability details
     */
    @GetMapping("/jetski/{jetskiId}/disponibilidade")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE', 'OPERADOR')")
    @Operation(
        summary = "Verificar disponibilidade do jetski",
        description = "Verifica se jetski está disponível ou bloqueado por manutenção (RN06)."
    )
    public ResponseEntity<JetskiDisponibilidadeResponse> getJetskiDisponibilidade(
        @Parameter(description = "UUID do tenant")
        @PathVariable UUID tenantId,
        @Parameter(description = "UUID do jetski")
        @PathVariable UUID jetskiId
    ) {
        log.info("GET /v1/tenants/{}/manutencoes/jetski/{}/disponibilidade", tenantId, jetskiId);

        validateTenantContext(tenantId);

        JetskiDisponibilidadeResponse response = osManutencaoService.checkJetskiDisponibilidade(jetskiId);

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

    private OSManutencaoResponse toResponse(OSManutencao os) {
        return OSManutencaoResponse.builder()
            .id(os.getId())
            .tenantId(os.getTenantId())
            .jetskiId(os.getJetskiId())
            .mecanicoId(os.getMecanicoId())
            .tipo(os.getTipo())
            .prioridade(os.getPrioridade())
            .dtAbertura(os.getDtAbertura())
            .dtPrevistaInicio(os.getDtPrevistaInicio())
            .dtInicioReal(os.getDtInicioReal())
            .dtPrevistaFim(os.getDtPrevistaFim())
            .dtConclusao(os.getDtConclusao())
            .descricaoProblema(os.getDescricaoProblema())
            .diagnostico(os.getDiagnostico())
            .solucao(os.getSolucao())
            .pecasJson(os.getPecasJson())
            .valorPecas(os.getValorPecas())
            .valorMaoObra(os.getValorMaoObra())
            .valorTotal(os.getValorTotal())
            .horimetroAbertura(os.getHorimetroAbertura())
            .horimetroConclusao(os.getHorimetroConclusao())
            .status(os.getStatus())
            .observacoes(os.getObservacoes())
            .createdAt(os.getCreatedAt())
            .updatedAt(os.getUpdatedAt())
            .build();
    }

    private OSManutencao toEntity(OSManutencaoCreateRequest request, UUID tenantId) {
        return OSManutencao.builder()
            .tenantId(tenantId)
            .jetskiId(request.getJetskiId())
            .mecanicoId(request.getMecanicoId())
            .tipo(request.getTipo())
            .prioridade(request.getPrioridade())
            .dtPrevistaInicio(request.getDtPrevistaInicio())
            .dtPrevistaFim(request.getDtPrevistaFim())
            .descricaoProblema(request.getDescricaoProblema())
            .diagnostico(request.getDiagnostico())
            .solucao(request.getSolucao())
            .pecasJson(request.getPecasJson())
            .valorPecas(request.getValorPecas())
            .valorMaoObra(request.getValorMaoObra())
            .valorTotal(request.getValorPecas().add(request.getValorMaoObra()))
            .horimetroAbertura(request.getHorimetroAbertura())
            .observacoes(request.getObservacoes())
            .status(OSManutencaoStatus.ABERTA)
            .build();
    }

    private OSManutencao toEntity(OSManutencaoUpdateRequest request) {
        return OSManutencao.builder()
            .mecanicoId(request.getMecanicoId())
            .prioridade(request.getPrioridade())
            .dtPrevistaInicio(request.getDtPrevistaInicio())
            .dtPrevistaFim(request.getDtPrevistaFim())
            .descricaoProblema(request.getDescricaoProblema())
            .diagnostico(request.getDiagnostico())
            .solucao(request.getSolucao())
            .pecasJson(request.getPecasJson())
            .valorPecas(request.getValorPecas())
            .valorMaoObra(request.getValorMaoObra())
            .observacoes(request.getObservacoes())
            .build();
    }
}
