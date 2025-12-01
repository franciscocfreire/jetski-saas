package com.jetski.manutencao.internal;

import com.jetski.locacoes.domain.Jetski;
import com.jetski.locacoes.domain.JetskiStatus;
import com.jetski.locacoes.api.JetskiPublicService;
import com.jetski.manutencao.domain.OSManutencao;
import com.jetski.manutencao.domain.OSManutencaoStatus;
import com.jetski.manutencao.domain.OSManutencaoTipo;
import com.jetski.manutencao.internal.repository.OSManutencaoRepository;
import com.jetski.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Service: OS Manutenção Management
 *
 * <p>Handles maintenance order operations:
 * <ul>
 *   <li>Create, update, and list maintenance orders</li>
 *   <li>Manage status workflow (ABERTA → EM_ANDAMENTO → CONCLUIDA)</li>
 *   <li>Automatically update jetski status (MANUTENCAO ↔ DISPONIVEL)</li>
 *   <li>Block jetski availability when OS is active</li>
 * </ul>
 *
 * <h3>Business Rules:</h3>
 * <ul>
 *   <li><b>RN06</b>: When OS is ABERTA/EM_ANDAMENTO, jetski → MANUTENCAO</li>
 *   <li><b>RN06.1</b>: Jetski in MANUTENCAO cannot be reserved</li>
 *   <li>When OS is CONCLUIDA/CANCELADA, jetski → DISPONIVEL (if no other active OS)</li>
 * </ul>
 *
 * @author Jetski Team
 * @since 1.0.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OSManutencaoService {

    private final OSManutencaoRepository osManutencaoRepository;
    private final JetskiPublicService jetskiPublicService;

    /**
     * List all active maintenance orders for current tenant.
     * Active = ABERTA, EM_ANDAMENTO, AGUARDANDO_PECAS
     *
     * @return List of active OSManutencao records
     */
    @Transactional(readOnly = true)
    public List<OSManutencao> listActiveOrders() {
        log.debug("Listing all active maintenance orders");
        return osManutencaoRepository.findAllActive();
    }

    /**
     * List all maintenance orders for current tenant (including finished).
     *
     * @return List of all OSManutencao records
     */
    @Transactional(readOnly = true)
    public List<OSManutencao> listAllOrders() {
        log.debug("Listing all maintenance orders");
        return osManutencaoRepository.findAllByTenant();
    }

    /**
     * List all orders for a specific jetski.
     *
     * @param jetskiId Jetski UUID
     * @return List of OSManutencao records for this jetski
     */
    @Transactional(readOnly = true)
    public List<OSManutencao> listOrdersByJetski(UUID jetskiId) {
        log.debug("Listing maintenance orders for jetski: {}", jetskiId);
        return osManutencaoRepository.findByJetskiId(jetskiId);
    }

    /**
     * List all active orders for a specific jetski.
     * Business Rule: If any active OS exists, jetski is blocked.
     *
     * @param jetskiId Jetski UUID
     * @return List of active OSManutencao records blocking this jetski
     */
    @Transactional(readOnly = true)
    public List<OSManutencao> listActiveOrdersByJetski(UUID jetskiId) {
        log.debug("Listing active maintenance orders for jetski: {}", jetskiId);
        return osManutencaoRepository.findActiveByJetskiId(jetskiId);
    }

    /**
     * List all orders assigned to a mechanic.
     *
     * @param mecanicoId Mechanic user UUID
     * @return List of OSManutencao records
     */
    @Transactional(readOnly = true)
    public List<OSManutencao> listOrdersByMechanic(UUID mecanicoId) {
        log.debug("Listing maintenance orders for mechanic: {}", mecanicoId);
        return osManutencaoRepository.findByMecanicoId(mecanicoId);
    }

    /**
     * List all orders by status.
     *
     * @param status OSManutencaoStatus
     * @return List of OSManutencao records
     */
    @Transactional(readOnly = true)
    public List<OSManutencao> listOrdersByStatus(OSManutencaoStatus status) {
        log.debug("Listing maintenance orders by status: {}", status);
        return osManutencaoRepository.findByStatus(status);
    }

    /**
     * List all orders by type.
     *
     * @param tipo OSManutencaoTipo
     * @return List of OSManutencao records
     */
    @Transactional(readOnly = true)
    public List<OSManutencao> listOrdersByType(OSManutencaoTipo tipo) {
        log.debug("Listing maintenance orders by type: {}", tipo);
        return osManutencaoRepository.findByTipo(tipo);
    }

    /**
     * Find order by ID within current tenant.
     *
     * @param id OSManutencao UUID
     * @return OSManutencao entity
     * @throws BusinessException if not found
     */
    @Transactional(readOnly = true)
    public OSManutencao findById(UUID id) {
        log.debug("Finding maintenance order by id: {}", id);
        return osManutencaoRepository.findById(id)
            .orElseThrow(() -> new BusinessException("Ordem de serviço não encontrada"));
    }

    /**
     * Create new maintenance order.
     *
     * <p>Validations:
     * <ul>
     *   <li>Jetski must exist and be active</li>
     *   <li>Problem description is mandatory</li>
     * </ul>
     *
     * <p>Side effects:
     * <ul>
     *   <li>RN06: Sets jetski status to MANUTENCAO if OS is ABERTA or EM_ANDAMENTO</li>
     * </ul>
     *
     * @param os OSManutencao entity to create
     * @return Created OSManutencao
     * @throws BusinessException if validation fails
     */
    @Transactional
    public OSManutencao createOrder(OSManutencao os) {
        log.info("Creating new maintenance order for jetski: {}", os.getJetskiId());

        // Validate jetski exists
        Jetski jetski = jetskiPublicService.findById(os.getJetskiId());

        // Validate problem description
        if (os.getDescricaoProblema() == null || os.getDescricaoProblema().isBlank()) {
            throw new BusinessException("Descrição do problema é obrigatória");
        }

        // Set default values
        if (os.getDtAbertura() == null) {
            os.setDtAbertura(Instant.now());
        }
        if (os.getStatus() == null) {
            os.setStatus(OSManutencaoStatus.ABERTA);
        }

        // Capture current odometer reading if not provided
        if (os.getHorimetroAbertura() == null) {
            os.setHorimetroAbertura(jetski.getHorimetroAtual());
        }

        OSManutencao saved = osManutencaoRepository.save(os);
        log.info("Maintenance order created: id={}, jetski={}, tipo={}",
                 saved.getId(), saved.getJetskiId(), saved.getTipo());

        // RN06: Block jetski availability if OS is active
        if (saved.isActive()) {
            blockJetskiForMaintenance(saved.getJetskiId());
        }

        return saved;
    }

    /**
     * Update existing maintenance order.
     *
     * <p>Allows updating: mechanic, dates, diagnosis, solution, parts, costs, and observations.
     * Status changes should use dedicated methods (startOrder, finishOrder, etc.).
     *
     * @param id OSManutencao UUID
     * @param updates OSManutencao with updated fields
     * @return Updated OSManutencao
     * @throws BusinessException if validation fails
     */
    @Transactional
    public OSManutencao updateOrder(UUID id, OSManutencao updates) {
        log.info("Updating maintenance order: id={}", id);

        OSManutencao existing = findById(id);

        // Cannot update finished orders
        if (existing.isFinished()) {
            throw new BusinessException("Não é possível alterar ordem de serviço finalizada");
        }

        // Update fields
        if (updates.getMecanicoId() != null) {
            existing.setMecanicoId(updates.getMecanicoId());
        }
        if (updates.getDtPrevistaInicio() != null) {
            existing.setDtPrevistaInicio(updates.getDtPrevistaInicio());
        }
        if (updates.getDtPrevistaFim() != null) {
            existing.setDtPrevistaFim(updates.getDtPrevistaFim());
        }
        if (updates.getDescricaoProblema() != null) {
            existing.setDescricaoProblema(updates.getDescricaoProblema());
        }
        if (updates.getDiagnostico() != null) {
            existing.setDiagnostico(updates.getDiagnostico());
        }
        if (updates.getSolucao() != null) {
            existing.setSolucao(updates.getSolucao());
        }
        if (updates.getPecasJson() != null) {
            existing.setPecasJson(updates.getPecasJson());
        }
        if (updates.getValorPecas() != null) {
            existing.setValorPecas(updates.getValorPecas());
        }
        if (updates.getValorMaoObra() != null) {
            existing.setValorMaoObra(updates.getValorMaoObra());
        }
        if (updates.getObservacoes() != null) {
            existing.setObservacoes(updates.getObservacoes());
        }
        if (updates.getPrioridade() != null) {
            existing.setPrioridade(updates.getPrioridade());
        }

        // Recalculate total cost
        existing.setValorTotal(
            existing.getValorPecas().add(existing.getValorMaoObra())
        );

        OSManutencao saved = osManutencaoRepository.save(existing);
        log.info("Maintenance order updated: id={}", saved.getId());
        return saved;
    }

    /**
     * Start work on maintenance order.
     * Transitions status: ABERTA → EM_ANDAMENTO
     *
     * @param id OSManutencao UUID
     * @return Updated OSManutencao
     * @throws BusinessException if not in ABERTA status
     */
    @Transactional
    public OSManutencao startOrder(UUID id) {
        log.info("Starting maintenance order: id={}", id);

        OSManutencao os = findById(id);

        if (os.getStatus() != OSManutencaoStatus.ABERTA) {
            throw new BusinessException("Ordem de serviço deve estar ABERTA para iniciar");
        }

        os.setStatus(OSManutencaoStatus.EM_ANDAMENTO);
        os.setDtInicioReal(Instant.now());

        OSManutencao saved = osManutencaoRepository.save(os);
        log.info("Maintenance order started: id={}", saved.getId());

        return saved;
    }

    /**
     * Mark order as waiting for parts.
     * Transitions status: EM_ANDAMENTO → AGUARDANDO_PECAS
     *
     * @param id OSManutencao UUID
     * @return Updated OSManutencao
     * @throws BusinessException if not in EM_ANDAMENTO status
     */
    @Transactional
    public OSManutencao waitForParts(UUID id) {
        log.info("Marking maintenance order as waiting for parts: id={}", id);

        OSManutencao os = findById(id);

        if (os.getStatus() != OSManutencaoStatus.EM_ANDAMENTO) {
            throw new BusinessException("Ordem de serviço deve estar EM_ANDAMENTO");
        }

        os.setStatus(OSManutencaoStatus.AGUARDANDO_PECAS);

        OSManutencao saved = osManutencaoRepository.save(os);
        log.info("Maintenance order waiting for parts: id={}", saved.getId());

        return saved;
    }

    /**
     * Resume work after parts arrive.
     * Transitions status: AGUARDANDO_PECAS → EM_ANDAMENTO
     *
     * @param id OSManutencao UUID
     * @return Updated OSManutencao
     * @throws BusinessException if not in AGUARDANDO_PECAS status
     */
    @Transactional
    public OSManutencao resumeOrder(UUID id) {
        log.info("Resuming maintenance order: id={}", id);

        OSManutencao os = findById(id);

        if (os.getStatus() != OSManutencaoStatus.AGUARDANDO_PECAS) {
            throw new BusinessException("Ordem de serviço deve estar AGUARDANDO_PECAS");
        }

        os.setStatus(OSManutencaoStatus.EM_ANDAMENTO);

        OSManutencao saved = osManutencaoRepository.save(os);
        log.info("Maintenance order resumed: id={}", saved.getId());

        return saved;
    }

    /**
     * Finish maintenance order.
     * Transitions status: EM_ANDAMENTO → CONCLUIDA
     *
     * <p>Side effects:
     * <ul>
     *   <li>Sets jetski status to DISPONIVEL if no other active OS exists</li>
     * </ul>
     *
     * @param id OSManutencao UUID
     * @return Updated OSManutencao
     * @throws BusinessException if not in EM_ANDAMENTO status
     */
    @Transactional
    public OSManutencao finishOrder(UUID id, com.jetski.manutencao.api.dto.OSManutencaoFinishRequest request) {
        log.info("Finishing maintenance order: id={}", id);

        OSManutencao os = findById(id);

        if (os.getStatus() != OSManutencaoStatus.EM_ANDAMENTO) {
            throw new BusinessException("Ordem de serviço deve estar EM_ANDAMENTO");
        }

        os.setStatus(OSManutencaoStatus.CONCLUIDA);
        os.setDtConclusao(Instant.now());

        // Set final values from request
        os.setHorimetroConclusao(request.getHorimetroFechamento());
        os.setValorPecas(request.getValorPecas());
        os.setValorMaoObra(request.getValorMaoObra());

        // Calculate total
        os.setValorTotal(request.getValorPecas().add(request.getValorMaoObra()));

        // Update observacoes if provided
        if (request.getObservacoesFinais() != null && !request.getObservacoesFinais().isBlank()) {
            String currentObs = os.getObservacoes() != null ? os.getObservacoes() + "\n" : "";
            os.setObservacoes(currentObs + "Finalização: " + request.getObservacoesFinais());
        }

        OSManutencao saved = osManutencaoRepository.save(os);
        log.info("Maintenance order finished: id={}", saved.getId());

        // RN06: Release jetski if no other active OS
        releaseJetskiFromMaintenance(saved.getJetskiId());

        return saved;
    }

    /**
     * Cancel maintenance order.
     * Transitions status: ABERTA/EM_ANDAMENTO/AGUARDANDO_PECAS → CANCELADA
     *
     * <p>Side effects:
     * <ul>
     *   <li>Sets jetski status to DISPONIVEL if no other active OS exists</li>
     * </ul>
     *
     * @param id OSManutencao UUID
     * @return Updated OSManutencao
     * @throws BusinessException if already finished
     */
    @Transactional
    public OSManutencao cancelOrder(UUID id) {
        log.info("Cancelling maintenance order: id={}", id);

        OSManutencao os = findById(id);

        if (os.isFinished()) {
            throw new BusinessException("Não é possível cancelar ordem de serviço finalizada");
        }

        os.setStatus(OSManutencaoStatus.CANCELADA);

        OSManutencao saved = osManutencaoRepository.save(os);
        log.info("Maintenance order cancelled: id={}", saved.getId());

        // RN06: Release jetski if no other active OS
        releaseJetskiFromMaintenance(saved.getJetskiId());

        return saved;
    }

    /**
     * Check if jetski has any active maintenance orders.
     * Business Rule RN06.1: Jetski with active OS cannot be reserved.
     *
     * @param jetskiId Jetski UUID
     * @return true if jetski has active OS
     */
    @Transactional(readOnly = true)
    public boolean hasActiveMaintenance(UUID jetskiId) {
        return osManutencaoRepository.hasActiveMaintenanceByJetskiId(jetskiId);
    }

    /**
     * Check jetski availability with detailed status.
     *
     * @param jetskiId Jetski UUID
     * @return Availability response with reason if unavailable
     */
    @Transactional(readOnly = true)
    public com.jetski.manutencao.api.dto.JetskiDisponibilidadeResponse checkJetskiDisponibilidade(UUID jetskiId) {
        log.info("Checking jetski availability: jetskiId={}", jetskiId);

        // Check if there are active maintenance orders
        boolean hasActiveMaintenance = osManutencaoRepository.hasActiveMaintenanceByJetskiId(jetskiId);

        if (!hasActiveMaintenance) {
            return com.jetski.manutencao.api.dto.JetskiDisponibilidadeResponse.builder()
                .disponivel(true)
                .motivo(null)
                .build();
        }

        // Find active OS to get details
        java.util.List<OSManutencao> activeOrders = osManutencaoRepository.findActiveByJetskiId(jetskiId);

        if (activeOrders.isEmpty()) {
            return com.jetski.manutencao.api.dto.JetskiDisponibilidadeResponse.builder()
                .disponivel(true)
                .motivo(null)
                .build();
        }

        // Get first active order for reason
        OSManutencao firstActiveOS = activeOrders.get(0);
        String motivo = String.format("Jetski em manutenção %s (OS #%s)",
            firstActiveOS.getTipo().name().toLowerCase(),
            firstActiveOS.getId().toString().substring(0, 8));

        return com.jetski.manutencao.api.dto.JetskiDisponibilidadeResponse.builder()
            .disponivel(false)
            .motivo(motivo)
            .build();
    }

    // ========== Private Helper Methods ==========

    /**
     * Block jetski availability by setting status to MANUTENCAO.
     * Business Rule RN06.
     *
     * @param jetskiId Jetski UUID
     */
    private void blockJetskiForMaintenance(UUID jetskiId) {
        log.info("Blocking jetski for maintenance: {}", jetskiId);
        Jetski jetski = jetskiPublicService.findById(jetskiId);

        if (jetski.getStatus() != JetskiStatus.MANUTENCAO) {
            jetskiPublicService.updateStatus(jetskiId, JetskiStatus.MANUTENCAO);
            log.info("Jetski blocked for maintenance: {}", jetskiId);
        }
    }

    /**
     * Release jetski from maintenance if no other active OS exists.
     * Business Rule RN06.
     *
     * @param jetskiId Jetski UUID
     */
    private void releaseJetskiFromMaintenance(UUID jetskiId) {
        log.info("Checking if jetski can be released from maintenance: {}", jetskiId);

        // Check if there are other active OS for this jetski
        boolean hasOtherActiveOS = osManutencaoRepository.hasActiveMaintenanceByJetskiId(jetskiId);

        if (!hasOtherActiveOS) {
            Jetski jetski = jetskiPublicService.findById(jetskiId);

            if (jetski.getStatus() == JetskiStatus.MANUTENCAO) {
                jetskiPublicService.updateStatus(jetskiId, JetskiStatus.DISPONIVEL);
                log.info("Jetski released from maintenance: {}", jetskiId);
            }
        } else {
            log.info("Jetski still has active maintenance orders: {}", jetskiId);
        }
    }
}
