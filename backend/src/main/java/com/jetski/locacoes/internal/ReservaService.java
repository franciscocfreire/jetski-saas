package com.jetski.locacoes.internal;

import com.jetski.locacoes.api.ModeloService;
import com.jetski.locacoes.domain.Jetski;
import com.jetski.locacoes.domain.JetskiStatus;
import com.jetski.locacoes.domain.Modelo;
import com.jetski.locacoes.domain.Reserva;
import com.jetski.locacoes.domain.Reserva.PagamentoStatus;
import com.jetski.locacoes.domain.Reserva.PagamentoTipo;
import com.jetski.locacoes.domain.Reserva.ReservaPrioridade;
import com.jetski.locacoes.domain.Reserva.ReservaStatus;
import com.jetski.locacoes.domain.ReservaConfig;
import com.jetski.locacoes.internal.repository.JetskiRepository;
import com.jetski.locacoes.internal.repository.ReservaRepository;
import com.jetski.reservas.domain.event.PagamentoConfirmadoEvent;
import com.jetski.reservas.domain.event.PagamentoRecusadoEvent;
import com.jetski.reservas.domain.event.ReservationCancelledEvent;
import com.jetski.reservas.domain.event.ReservationConfirmedEvent;
import com.jetski.reservas.domain.event.ReservationCreatedEvent;
import com.jetski.shared.exception.BusinessException;
import com.jetski.shared.security.TenantContext;
import com.jetski.tenant.TenantTimeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Service: Reserva Management
 *
 * Handles reservation/booking operations:
 * - Create reservation with conflict detection
 * - Confirm and cancel reservations
 * - Validate jetski availability (Business rule RN06)
 * - Prevent schedule conflicts
 * - Modelo-based booking with optional deposit (v0.3.0)
 * - Controlled overbooking for reservations without deposit (v0.3.0)
 * - Automatic expiration handling (v0.3.0)
 * - Jetski allocation at check-in (v0.3.0)
 *
 * Business Rules:
 * - RN06: Cannot reserve jetski in MANUTENCAO status
 * - Schedule conflict detection: prevent overlapping reservations
 * - RF03.4: Customer must have accepted terms before check-in (validated at rental creation, not reservation)
 * - Reservations are BY MODELO, not specific jetski (v0.3.0)
 * - Deposit system: ALTA priority (guaranteed) vs BAIXA priority (overbooking) (v0.3.0)
 * - Grace period for no-show auto-expiration (v0.3.0)
 * - Overbooking limits configurable per tenant (v0.3.0)
 *
 * @author Jetski Team
 * @since 0.2.0
 * @version 0.3.0 - Refactored to modelo-based booking with deposit system
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ReservaService {

    private final ReservaRepository reservaRepository;
    private final JetskiService jetskiService;
    private final ClienteService clienteService;
    private final ModeloService modeloService;
    private final ReservaConfigService reservaConfigService;
    private final JetskiRepository jetskiRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final TenantTimeService tenantTimeService;
    private final ClienteNotificacaoService clienteNotificacaoService;

    /**
     * List all active reservations for current tenant.
     * RLS automatically filters by tenant_id.
     *
     * @return List of active Reserva records
     */
    @Transactional(readOnly = true)
    public List<Reserva> listActiveReservations() {
        log.debug("Listing all active reservations");
        return reservaRepository.findAllActive();
    }

    /**
     * List all reservations for current tenant (including cancelled/finalized).
     * RLS automatically filters by tenant_id.
     *
     * @return List of all Reserva records
     */
    @Transactional(readOnly = true)
    public List<Reserva> listAllReservations() {
        log.debug("Listing all reservations");
        return reservaRepository.findAll();
    }

    /**
     * List reservations by status.
     *
     * @param status Reservation status
     * @return List of reservations with specified status
     */
    @Transactional(readOnly = true)
    public List<Reserva> listByStatus(ReservaStatus status) {
        log.debug("Listing reservations by status: {}", status);
        return reservaRepository.findByStatus(status);
    }

    /**
     * List pending reservations (awaiting confirmation).
     *
     * @return List of pending reservations
     */
    @Transactional(readOnly = true)
    public List<Reserva> listPendingReservations() {
        log.debug("Listing pending reservations");
        return reservaRepository.findPendingReservations();
    }

    /**
     * List reservations for a specific jetski.
     *
     * @param jetskiId Jetski UUID
     * @return List of reservations for the jetski
     */
    @Transactional(readOnly = true)
    public List<Reserva> listByJetski(UUID jetskiId) {
        log.debug("Listing reservations for jetski: {}", jetskiId);
        return reservaRepository.findByJetskiId(jetskiId);
    }

    /**
     * List reservations for a specific customer.
     *
     * @param clienteId Customer UUID
     * @return List of customer's reservations
     */
    @Transactional(readOnly = true)
    public List<Reserva> listByCliente(UUID clienteId) {
        log.debug("Listing reservations for cliente: {}", clienteId);
        return reservaRepository.findByClienteId(clienteId);
    }

    /**
     * List confirmed reservations for today.
     *
     * @return List of today's confirmed reservations
     */
    @Transactional(readOnly = true)
    public List<Reserva> listTodayReservations() {
        LocalDateTime startOfDay = tenantTimeService.today().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);
        log.debug("Listing today's confirmed reservations: {} to {}", startOfDay, endOfDay);
        return reservaRepository.findTodayConfirmedReservations(startOfDay, endOfDay);
    }

    /**
     * Find reservation by ID within current tenant.
     *
     * @param id Reserva UUID
     * @return Reserva entity
     * @throws BusinessException if not found
     */
    @Transactional(readOnly = true)
    public Reserva findById(UUID id) {
        log.debug("Finding reservation by id: {}", id);
        return reservaRepository.findById(id)
            .orElseThrow(() -> new BusinessException("Reserva não encontrada"));
    }

    /**
     * Create new reservation (modelo-based booking).
     *
     * Validations (v0.3.0):
     * - Modelo must exist and be active
     * - Cliente must exist and be active
     * - Start date must be before end date
     * - Start date must not be in the past
     * - Check overbooking limits for reservations without deposit
     * - If deposit paid: validate against physical jetski capacity (guaranteed reservation)
     * - If no deposit: allow overbooking within configured limits
     * - Calculate expiraEm based on tenant config (grace period)
     * - If jetskiId provided: validate it belongs to modelo and is available
     *
     * @param reserva Reserva entity to create (modeloId required, jetskiId optional)
     * @return Created Reserva
     * @throws BusinessException if validation fails
     */
    @Transactional
    public Reserva createReserva(Reserva reserva) {
        log.info("Creating new reservation: modelo={}, jetski={}, cliente={}, periodo={} to {}, sinalPago={}",
                 reserva.getModeloId(), reserva.getJetskiId(), reserva.getClienteId(),
                 reserva.getDataInicio(), reserva.getDataFimPrevista(), reserva.getSinalPago());

        // Validate date range
        if (reserva.getDataInicio() == null || reserva.getDataFimPrevista() == null) {
            throw new BusinessException("Data de início e fim são obrigatórias");
        }

        if (reserva.getDataInicio().isAfter(reserva.getDataFimPrevista()) ||
            reserva.getDataInicio().isEqual(reserva.getDataFimPrevista())) {
            throw new BusinessException("Data de início deve ser anterior à data de fim");
        }

        if (reserva.getDataInicio().isBefore(tenantTimeService.now())) {
            throw new BusinessException("Não é possível criar reserva com data no passado");
        }

        // Validate modelo exists
        if (reserva.getModeloId() == null) {
            throw new BusinessException("Modelo é obrigatório para criar reserva");
        }

        Modelo modelo = modeloService.findById(reserva.getModeloId());
        if (!Boolean.TRUE.equals(modelo.getAtivo())) {
            throw new BusinessException("Modelo não está ativo");
        }

        // Validate cliente exists
        clienteService.findById(reserva.getClienteId());

        // Get tenant configuration
        ReservaConfig config = reservaConfigService.getConfigForCurrentTenant();

        // Count available jetskis for this modelo
        long totalJetskisDisponiveis = jetskiRepository.countByModeloIdAndAtivoAndStatus(
            reserva.getModeloId(),
            true,
            JetskiStatus.DISPONIVEL
        );

        // Reserva é por MODELO: só bloqueia por unidade quando o tenant
        // optou por controlar estoque (config; default = não controla)
        boolean controlarEstoque = Boolean.TRUE.equals(config.getControlarEstoque());
        if (controlarEstoque && totalJetskisDisponiveis == 0) {
            throw new BusinessException(
                String.format("Nenhum jetski disponível para o modelo %s", modelo.getNome())
            );
        }

        // Count existing reservations for this modelo in the period
        long reservasGarantidas = reservaRepository.countReservasGarantidasForModelo(
            reserva.getModeloId(),
            reserva.getDataInicio(),
            reserva.getDataFimPrevista()
        );

        long totalReservas = reservaRepository.countActiveReservasForModelo(
            reserva.getModeloId(),
            reserva.getDataInicio(),
            reserva.getDataFimPrevista()
        );

        // Determine priority and validate capacity
        boolean sinalPago = Boolean.TRUE.equals(reserva.getSinalPago());
        ReservaPrioridade prioridade = sinalPago ? ReservaPrioridade.ALTA : ReservaPrioridade.BAIXA;

        if (sinalPago) {
            // ALTA priority: capacidade física só quando o tenant controla estoque
            if (controlarEstoque && reservasGarantidas >= totalJetskisDisponiveis) {
                throw new BusinessException(
                    String.format("Capacidade esgotada: %d jetskis disponíveis, %d reservas garantidas já existentes",
                                  totalJetskisDisponiveis, reservasGarantidas)
                );
            }

            // Validate deposit amount and timestamp
            if (reserva.getValorSinal() == null || reserva.getValorSinal().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("Valor do sinal é obrigatório para reservas garantidas");
            }

            if (reserva.getSinalPagoEm() == null) {
                reserva.setSinalPagoEm(Instant.now());
            }
        } else {
            // BAIXA priority: allow creation but will expire if no deposit paid
            // Overbooking validation is enforced when upgrading to ALTA priority (confirmar-sinal)
            // This allows customers to create reservation first, then pay deposit
            log.info("Creating BAIXA priority reservation (no deposit): modelo={}, existing_reservas={}, existing_garantidas={}",
                     reserva.getModeloId(), totalReservas, reservasGarantidas);
        }

        // Calculate expiraEm (grace period after start time)
        LocalDateTime expiraEm = reserva.getDataInicio().plusMinutes(config.getGracePeriodMinutos());

        // If specific jetski provided, validate it
        if (reserva.getJetskiId() != null) {
            Jetski jetski = jetskiService.findById(reserva.getJetskiId());

            // Validate jetski belongs to this modelo
            if (!jetski.getModeloId().equals(reserva.getModeloId())) {
                throw new BusinessException(
                    String.format("Jetski %s não pertence ao modelo %s",
                                  jetski.getSerie(), modelo.getNome())
                );
            }

            if (!Boolean.TRUE.equals(jetski.getAtivo())) {
                throw new BusinessException("Jetski não está ativo");
            }

            if (jetski.getStatus() != JetskiStatus.DISPONIVEL) {
                throw new BusinessException(
                    String.format("Jetski não disponível (status: %s)", jetski.getStatus())
                );
            }

            // Check for conflicts on this specific jetski
            List<Reserva> conflicts = reservaRepository.findConflictingReservations(
                reserva.getJetskiId(),
                reserva.getDataInicio(),
                reserva.getDataFimPrevista()
            );

            if (!conflicts.isEmpty()) {
                Reserva conflict = conflicts.get(0);
                throw new BusinessException(
                    String.format("Conflito de agenda: jetski %s já alocado para período " +
                                  "sobreposto (%s a %s, reserva #%s)",
                                  jetski.getSerie(),
                                  conflict.getDataInicio(), conflict.getDataFimPrevista(),
                                  conflict.getId())
                );
            }
        }

        // Set defaults
        reserva.setPrioridade(prioridade);
        reserva.setExpiraEm(expiraEm);

        if (reserva.getStatus() == null) {
            reserva.setStatus(ReservaStatus.PENDENTE);
        }

        if (reserva.getAtivo() == null) {
            reserva.setAtivo(true);
        }

        Reserva saved = reservaRepository.save(reserva);
        log.info("Reservation created successfully: id={}, modelo={}, jetski={}, prioridade={}, expiraEm={}, status={}",
                 saved.getId(), saved.getModeloId(), saved.getJetskiId(),
                 saved.getPrioridade(), saved.getExpiraEm(), saved.getStatus());

        // Publish audit event
        eventPublisher.publishEvent(ReservationCreatedEvent.of(
            TenantContext.getTenantId(),
            saved.getId(),
            saved.getModeloId(),
            saved.getClienteId(),
            saved.getVendedorId(),
            TenantContext.getUsuarioId(),
            saved.getDataInicio(),
            saved.getDataFimPrevista(),
            saved.getSinalPago()
        ));
        log.debug("Published ReservationCreatedEvent for reservation: {}", saved.getId());

        return saved;
    }

    /**
     * Cria um RASCUNHO de reserva (atendimento de balcão em preenchimento).
     *
     * <p>Validação mínima (modelo ativo, cliente existe, datas coerentes). NÃO checa
     * capacidade/disponibilidade de jetski — o rascunho não bloqueia nada (só guarda
     * modelo/duração) e não é cobrado. A emissão dos documentos depois o transiciona
     * para PENDENTE/CONFIRMADA.
     */
    @Transactional
    public Reserva criarRascunho(Reserva reserva) {
        if (reserva.getDataInicio() == null || reserva.getDataFimPrevista() == null) {
            throw new BusinessException("Data de início e fim são obrigatórias");
        }
        if (!reserva.getDataInicio().isBefore(reserva.getDataFimPrevista())) {
            throw new BusinessException("Data de início deve ser anterior à data de fim");
        }
        if (reserva.getModeloId() == null) {
            throw new BusinessException("Modelo é obrigatório para criar reserva");
        }
        Modelo modelo = modeloService.findById(reserva.getModeloId());
        if (!Boolean.TRUE.equals(modelo.getAtivo())) {
            throw new BusinessException("Modelo não está ativo");
        }
        clienteService.findById(reserva.getClienteId());

        reserva.setStatus(ReservaStatus.RASCUNHO);
        reserva.setPrioridade(ReservaPrioridade.BAIXA);
        reserva.setSinalPago(false);
        if (reserva.getAtivo() == null) {
            reserva.setAtivo(true);
        }

        Reserva saved = reservaRepository.save(reserva);
        log.info("Rascunho de reserva criado: id={}, modelo={}, cliente={}",
                 saved.getId(), saved.getModeloId(), saved.getClienteId());
        return saved;
    }

    /**
     * Update existing reservation.
     *
     * Validations:
     * - Reservation must exist and be active
     * - Can only update PENDENTE or CONFIRMADA reservations
     * - If dates changed, validate no conflicts
     * - Start date must be before end date
     *
     * @param id Reserva UUID
     * @param updates Reserva with updated fields
     * @return Updated Reserva
     * @throws BusinessException if validation fails
     */
    @Transactional
    public Reserva updateReserva(UUID id, Reserva updates) {
        log.info("Updating reservation: id={}", id);

        Reserva existing = findById(id);

        // Can only update pending or confirmed reservations
        if (existing.getStatus() == ReservaStatus.CANCELADA ||
            existing.getStatus() == ReservaStatus.FINALIZADA) {
            throw new BusinessException(
                String.format("Não é possível atualizar reserva com status %s",
                              existing.getStatus())
            );
        }

        boolean rascunho = existing.getStatus() == ReservaStatus.RASCUNHO;

        // Modelo só pode ser trocado enquanto RASCUNHO (atendimento em preenchimento):
        // ainda estamos escolhendo o modelo, nada está cobrado nem bloqueado.
        if (updates.getModeloId() != null && !updates.getModeloId().equals(existing.getModeloId())) {
            if (!rascunho) {
                throw new BusinessException("Modelo só pode ser alterado enquanto a reserva é rascunho");
            }
            Modelo modelo = modeloService.findById(updates.getModeloId());
            if (!Boolean.TRUE.equals(modelo.getAtivo())) {
                throw new BusinessException("Modelo não está ativo");
            }
            existing.setModeloId(updates.getModeloId());
        }

        // Update dates if provided
        boolean datesChanged = false;
        LocalDateTime newDataInicio = existing.getDataInicio();
        LocalDateTime newDataFimPrevista = existing.getDataFimPrevista();

        if (updates.getDataInicio() != null) {
            newDataInicio = updates.getDataInicio();
            datesChanged = true;
        }

        if (updates.getDataFimPrevista() != null) {
            newDataFimPrevista = updates.getDataFimPrevista();
            datesChanged = true;
        }

        // Validate new date range
        if (newDataInicio.isAfter(newDataFimPrevista) ||
            newDataInicio.isEqual(newDataFimPrevista)) {
            throw new BusinessException("Data de início deve ser anterior à data de fim");
        }

        // Conflito de jetski não se aplica a RASCUNHO (sem jetski alocado / sem bloqueio).
        if (datesChanged && rascunho) {
            existing.setDataInicio(newDataInicio);
            existing.setDataFimPrevista(newDataFimPrevista);
        } else if (datesChanged) {
            List<Reserva> conflicts = reservaRepository.findConflictingReservationsExcluding(
                existing.getJetskiId(),
                newDataInicio,
                newDataFimPrevista,
                id
            );

            if (!conflicts.isEmpty()) {
                Reserva conflict = conflicts.get(0);
                throw new BusinessException(
                    String.format("Conflito de agenda: jetski já reservado para período " +
                                  "sobreposto (%s a %s, reserva #%s)",
                                  conflict.getDataInicio(), conflict.getDataFimPrevista(),
                                  conflict.getId())
                );
            }

            existing.setDataInicio(newDataInicio);
            existing.setDataFimPrevista(newDataFimPrevista);
        }

        // Update observacoes if provided
        if (updates.getObservacoes() != null) {
            existing.setObservacoes(updates.getObservacoes());
        }

        Reserva saved = reservaRepository.save(existing);
        log.info("Reservation updated successfully: id={}", saved.getId());
        return saved;
    }

    /**
     * Confirm pending reservation.
     * Transition: PENDENTE → CONFIRMADA
     *
     * @param id Reserva UUID
     * @return Confirmed Reserva
     * @throws BusinessException if cannot confirm
     */
    @Transactional
    public Reserva confirmReservation(UUID id) {
        log.info("Confirming reservation: id={}", id);

        Reserva reserva = findById(id);

        if (!reserva.canConfirm()) {
            throw new BusinessException(
                String.format("Não é possível confirmar reserva (status: %s, ativo: %s)",
                              reserva.getStatus(), reserva.getAtivo())
            );
        }

        // Only validate jetski if already allocated (modelo-based booking may have null jetskiId)
        if (reserva.getJetskiId() != null) {
            // Re-validate jetski availability at confirmation time
            Jetski jetski = jetskiService.findById(reserva.getJetskiId());

            if (jetski.getStatus() != JetskiStatus.DISPONIVEL) {
                throw new BusinessException(
                    String.format("Jetski não está mais disponível (status: %s)",
                                  jetski.getStatus())
                );
            }

            // Re-check for conflicts (in case new reservations were created)
            List<Reserva> conflicts = reservaRepository.findConflictingReservationsExcluding(
                reserva.getJetskiId(),
                reserva.getDataInicio(),
                reserva.getDataFimPrevista(),
                id
            );

            if (!conflicts.isEmpty()) {
                Reserva conflict = conflicts.get(0);
                throw new BusinessException(
                    String.format("Conflito de agenda detectado: jetski reservado para " +
                                  "período sobreposto (%s a %s, reserva #%s)",
                                  conflict.getDataInicio(), conflict.getDataFimPrevista(),
                                  conflict.getId())
                );
            }
        }
        // If jetskiId is null, skip jetski validation (will be allocated at check-in)

        reserva.setStatus(ReservaStatus.CONFIRMADA);
        Reserva saved = reservaRepository.save(reserva);

        log.info("Reservation confirmed successfully: id={}", saved.getId());

        // Publish audit event
        eventPublisher.publishEvent(ReservationConfirmedEvent.of(
            TenantContext.getTenantId(),
            saved.getId(),
            saved.getClienteId(),
            TenantContext.getUsuarioId()
        ));
        log.debug("Published ReservationConfirmedEvent for reservation: {}", saved.getId());

        return saved;
    }

    /**
     * Cancel reservation.
     * Can cancel PENDENTE or CONFIRMADA reservations.
     * Transition: PENDENTE/CONFIRMADA → CANCELADA
     *
     * @param id Reserva UUID
     * @return Cancelled Reserva
     * @throws BusinessException if cannot cancel
     */
    @Transactional
    public Reserva cancelReservation(UUID id) {
        log.info("Cancelling reservation: id={}", id);

        Reserva reserva = findById(id);

        if (!reserva.canCancel()) {
            throw new BusinessException(
                String.format("Não é possível cancelar reserva (status: %s, ativo: %s)",
                              reserva.getStatus(), reserva.getAtivo())
            );
        }

        reserva.setStatus(ReservaStatus.CANCELADA);
        Reserva saved = reservaRepository.save(reserva);

        log.info("Reservation cancelled successfully: id={}", saved.getId());

        // Publish audit event
        eventPublisher.publishEvent(ReservationCancelledEvent.of(
            TenantContext.getTenantId(),
            saved.getId(),
            saved.getClienteId(),
            TenantContext.getUsuarioId()
        ));
        log.debug("Published ReservationCancelledEvent for reservation: {}", saved.getId());

        return saved;
    }

    /**
     * Finalize reservation (mark as completed/expired).
     * Used when reservation is converted to rental or when it expires.
     * Transition: any status → FINALIZADA
     *
     * @param id Reserva UUID
     * @return Finalized Reserva
     * @throws BusinessException if not found
     */
    @Transactional
    public Reserva finalizeReservation(UUID id) {
        log.info("Finalizing reservation: id={}", id);

        Reserva reserva = findById(id);

        reserva.setStatus(ReservaStatus.FINALIZADA);
        Reserva saved = reservaRepository.save(reserva);

        log.info("Reservation finalized successfully: id={}", saved.getId());
        return saved;
    }

    /**
     * Check if jetski is available for a specific period.
     * Used for availability queries and calendar views.
     *
     * @param jetskiId Jetski UUID
     * @param dataInicio Start date/time
     * @param dataFimPrevista End date/time
     * @return true if available (no conflicts), false otherwise
     */
    @Transactional(readOnly = true)
    public boolean isJetskiAvailable(UUID jetskiId, LocalDateTime dataInicio, LocalDateTime dataFimPrevista) {
        log.debug("Checking jetski availability: id={}, periodo={} to {}",
                  jetskiId, dataInicio, dataFimPrevista);

        // Check jetski status
        Jetski jetski = jetskiService.findById(jetskiId);
        if (jetski.getStatus() != JetskiStatus.DISPONIVEL) {
            return false;
        }

        // Check for conflicts
        List<Reserva> conflicts = reservaRepository.findConflictingReservations(
            jetskiId, dataInicio, dataFimPrevista
        );

        return conflicts.isEmpty();
    }

    /**
     * Get reservations for a jetski within a date range.
     * Used for calendar view and availability planning.
     *
     * @param jetskiId Jetski UUID
     * @param dataInicio Start of period
     * @param dataFim End of period
     * @return List of reservations in period
     */
    @Transactional(readOnly = true)
    public List<Reserva> getReservationsInPeriod(UUID jetskiId, LocalDateTime dataInicio, LocalDateTime dataFim) {
        log.debug("Getting reservations in period: jetski={}, periodo={} to {}",
                  jetskiId, dataInicio, dataFim);
        return reservaRepository.findByJetskiAndPeriod(jetskiId, dataInicio, dataFim);
    }

    // =====================================================
    // New methods for modelo-based booking (v0.3.0)
    // =====================================================

    /**
     * Confirm deposit payment and upgrade to ALTA priority.
     * Transition: BAIXA → ALTA priority
     *
     * Validations:
     * - Reservation must be PENDENTE or CONFIRMADA
     * - Deposit not already paid
     * - Physical capacity available (guaranteed reservations cannot exceed jetski count)
     * - Valid deposit amount
     *
     * @param id Reserva UUID
     * @param valorSinal Deposit amount paid
     * @return Updated Reserva with ALTA priority
     * @throws BusinessException if cannot confirm deposit
     */
    @Transactional
    public Reserva confirmarSinal(UUID id, BigDecimal valorSinal) {
        // Retrocompatibilidade: confirmar como SINAL.
        return confirmarPagamento(id, PagamentoTipo.SINAL, valorSinal);
    }

    /**
     * Confirma o pagamento de uma reserva (SINAL ou TOTAL).
     *
     * <p>Sobe a reserva para ALTA (garantida), grava o estado do pagamento,
     * quem validou e quando, e publica {@link PagamentoConfirmadoEvent}.
     *
     * @param id        Reserva UUID
     * @param tipo      SINAL (parcial) ou TOTAL (integral)
     * @param valorPago valor confirmado pelo staff
     */
    @Transactional
    public Reserva confirmarPagamento(UUID id, PagamentoTipo tipo, BigDecimal valorPago) {
        PagamentoTipo tipoPagamento = (tipo != null) ? tipo : PagamentoTipo.SINAL;
        log.info("Confirming payment for reservation: id={}, tipo={}, valor={}", id, tipoPagamento, valorPago);

        Reserva reserva = findById(id);

        if (!reserva.podeConfirmarSinal()) {
            throw new BusinessException(
                String.format("Não é possível confirmar pagamento (sinalPago=%s, status=%s, ativo=%s)",
                              reserva.getSinalPago(), reserva.getStatus(), reserva.getAtivo())
            );
        }

        if (valorPago == null || valorPago.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Valor do pagamento deve ser maior que zero");
        }

        // Check physical capacity (guaranteed reservations cannot exceed jetski count)
        long totalJetskisDisponiveis = jetskiRepository.countByModeloIdAndAtivoAndStatus(
            reserva.getModeloId(),
            true,
            JetskiStatus.DISPONIVEL
        );

        long reservasGarantidas = reservaRepository.countReservasGarantidasForModelo(
            reserva.getModeloId(),
            reserva.getDataInicio(),
            reserva.getDataFimPrevista()
        );

        if (Boolean.TRUE.equals(reservaConfigService.getConfigForCurrentTenant().getControlarEstoque())
                && reservasGarantidas >= totalJetskisDisponiveis) {
            throw new BusinessException(
                String.format("Capacidade esgotada para reservas garantidas: %d jetskis disponíveis, " +
                              "%d reservas garantidas já existentes",
                              totalJetskisDisponiveis, reservasGarantidas)
            );
        }

        Instant agora = Instant.now();
        UUID usuarioId = TenantContext.getUsuarioId();

        // Upgrade to ALTA priority + estado de pagamento
        reserva.setSinalPago(true);
        reserva.setValorSinal(valorPago);
        reserva.setSinalPagoEm(agora);
        reserva.setPrioridade(ReservaPrioridade.ALTA);
        reserva.setPagamentoTipo(tipoPagamento);
        reserva.setPagamentoStatus(PagamentoStatus.CONFIRMADO);
        reserva.setPagamentoValidadoPor(usuarioId);
        reserva.setPagamentoValidadoEm(agora);
        if (tipoPagamento == PagamentoTipo.TOTAL) {
            reserva.setValorTotal(valorPago);
        }

        Reserva saved = reservaRepository.save(reserva);
        log.info("Payment confirmed successfully: id={}, tipo={}, prioridade={}, valor={}",
                 saved.getId(), tipoPagamento, saved.getPrioridade(), saved.getValorSinal());

        clienteNotificacaoService.notificar(saved.getTenantId(), saved.getClienteId(),
            com.jetski.locacoes.domain.ClienteNotificacao.PAGAMENTO_CONFIRMADO,
            "Pagamento confirmado 🎉",
            "Seu pagamento foi confirmado pela loja — a reserva está garantida.",
            "/conta/reservas/" + saved.getId());
        eventPublisher.publishEvent(PagamentoConfirmadoEvent.of(
            saved.getTenantId(), saved.getId(), tipoPagamento.name(), valorPago, usuarioId));

        return saved;
    }

    /**
     * Recusa o pagamento de uma reserva (comprovante inválido).
     *
     * <p>Mantém a reserva não garantida (BAIXA/PENDENTE), grava o motivo e
     * publica {@link PagamentoRecusadoEvent}. Cliente é notificado para reenviar.
     */
    @Transactional
    public Reserva recusarPagamento(UUID id, String motivo) {
        log.info("Rejecting payment for reservation: id={}", id);

        if (motivo == null || motivo.isBlank()) {
            throw new BusinessException("Motivo da recusa é obrigatório");
        }

        Reserva reserva = findById(id);

        if (!reserva.podeRecusarPagamento()) {
            throw new BusinessException(
                String.format("Não é possível recusar pagamento (sinalPago=%s, ativo=%s)",
                              reserva.getSinalPago(), reserva.getAtivo())
            );
        }

        UUID usuarioId = TenantContext.getUsuarioId();
        reserva.setPagamentoStatus(PagamentoStatus.RECUSADO);
        reserva.setPagamentoMotivoRecusa(motivo);
        reserva.setPagamentoValidadoPor(usuarioId);
        reserva.setPagamentoValidadoEm(Instant.now());

        Reserva saved = reservaRepository.save(reserva);
        log.info("Payment rejected: id={}, motivo={}", saved.getId(), motivo);

        clienteNotificacaoService.notificar(saved.getTenantId(), saved.getClienteId(),
            com.jetski.locacoes.domain.ClienteNotificacao.PAGAMENTO_RECUSADO,
            "Pagamento recusado",
            (motivo != null && !motivo.isBlank()
                ? "Motivo: " + motivo + ". "
                : "") + "Reenvie o comprovante para garantir sua reserva.",
            "/conta/reservas/" + saved.getId() + "/pagamento");
        eventPublisher.publishEvent(PagamentoRecusadoEvent.of(
            saved.getTenantId(), saved.getId(), motivo, usuarioId));

        return saved;
    }

    /**
     * Allocate specific jetski to a reservation.
     * Used by operators before check-in or during check-in process.
     *
     * Validations:
     * - Reservation must be CONFIRMADA
     * - No jetski allocated yet
     * - Jetski must belong to reserved modelo
     * - Jetski must be DISPONIVEL
     * - No conflicts with other reservations
     *
     * @param reservaId Reserva UUID
     * @param jetskiId Jetski UUID to allocate
     * @return Updated Reserva with jetski allocated
     * @throws BusinessException if cannot allocate
     */
    @Transactional
    public Reserva alocarJetski(UUID reservaId, UUID jetskiId) {
        log.info("Allocating jetski to reservation: reserva={}, jetski={}", reservaId, jetskiId);

        Reserva reserva = findById(reservaId);

        if (!reserva.podeSerAlocada()) {
            throw new BusinessException(
                String.format("Não é possível alocar jetski (jetskiId=%s, status=%s, ativo=%s)",
                              reserva.getJetskiId(), reserva.getStatus(), reserva.getAtivo())
            );
        }

        Jetski jetski = jetskiService.findById(jetskiId);

        // Validate jetski belongs to modelo
        if (!jetski.getModeloId().equals(reserva.getModeloId())) {
            Modelo modelo = modeloService.findById(reserva.getModeloId());
            throw new BusinessException(
                String.format("Jetski %s não pertence ao modelo %s",
                              jetski.getSerie(), modelo.getNome())
            );
        }

        if (!Boolean.TRUE.equals(jetski.getAtivo())) {
            throw new BusinessException("Jetski não está ativo");
        }

        if (jetski.getStatus() != JetskiStatus.DISPONIVEL) {
            throw new BusinessException(
                String.format("Jetski não disponível (status: %s)", jetski.getStatus())
            );
        }

        // Check for conflicts
        List<Reserva> conflicts = reservaRepository.findConflictingReservationsExcluding(
            jetskiId,
            reserva.getDataInicio(),
            reserva.getDataFimPrevista(),
            reservaId
        );

        if (!conflicts.isEmpty()) {
            Reserva conflict = conflicts.get(0);
            throw new BusinessException(
                String.format("Conflito de agenda: jetski %s já alocado para período " +
                              "sobreposto (%s a %s, reserva #%s)",
                              jetski.getSerie(),
                              conflict.getDataInicio(), conflict.getDataFimPrevista(),
                              conflict.getId())
            );
        }

        reserva.setJetskiId(jetskiId);
        Reserva saved = reservaRepository.save(reserva);

        log.info("Jetski allocated successfully: reserva={}, jetski={}",
                 saved.getId(), saved.getJetskiId());
        return saved;
    }

    /**
     * Check modelo availability for a specific period.
     * Returns true if there's capacity available (considering overbooking limits).
     *
     * Logic:
     * - If requesting guaranteed reservation (with deposit): check physical capacity
     * - If requesting regular reservation (no deposit): check overbooking limit
     *
     * @param modeloId Modelo UUID
     * @param dataInicio Start date/time
     * @param dataFimPrevista End date/time
     * @param comSinal Whether this is a guaranteed reservation (with deposit)
     * @return true if modelo is available for reservation
     */
    @Transactional(readOnly = true)
    public boolean verificarDisponibilidadeModelo(UUID modeloId, LocalDateTime dataInicio,
                                                    LocalDateTime dataFimPrevista, boolean comSinal) {
        log.debug("Checking modelo availability: id={}, periodo={} to {}, comSinal={}",
                  modeloId, dataInicio, dataFimPrevista, comSinal);

        // Count available jetskis for this modelo
        long totalJetskisDisponiveis = jetskiRepository.countByModeloIdAndAtivoAndStatus(
            modeloId,
            true,
            JetskiStatus.DISPONIVEL
        );

        if (totalJetskisDisponiveis == 0) {
            return false;
        }

        if (comSinal) {
            // For guaranteed reservations: check physical capacity
            long reservasGarantidas = reservaRepository.countReservasGarantidasForModelo(
                modeloId,
                dataInicio,
                dataFimPrevista
            );

            return reservasGarantidas < totalJetskisDisponiveis;
        } else {
            // For regular reservations: check overbooking limit
            ReservaConfig config = reservaConfigService.getConfigForCurrentTenant();
            long maxReservas = config.calcularMaximoReservas(totalJetskisDisponiveis);

            long totalReservas = reservaRepository.countActiveReservasForModelo(
                modeloId,
                dataInicio,
                dataFimPrevista
            );

            return totalReservas < maxReservas;
        }
    }

    /**
     * Expire a reservation (no-show handling).
     * Transition: PENDENTE/CONFIRMADA → EXPIRADA
     *
     * Used by scheduled job to automatically expire reservations after grace period.
     * Should only expire reservations WITHOUT deposit (BAIXA priority).
     *
     * @param id Reserva UUID
     * @return Expired Reserva
     * @throws BusinessException if cannot expire
     */
    @Transactional
    public Reserva expirarReserva(UUID id) {
        log.info("Expiring reservation: id={}", id);

        Reserva reserva = findById(id);

        if (!reserva.deveExpirar()) {
            throw new BusinessException(
                String.format("Não é possível expirar reserva (expirada=%s, sinalPago=%s, status=%s)",
                              reserva.isExpirada(), reserva.getSinalPago(), reserva.getStatus())
            );
        }

        reserva.setStatus(ReservaStatus.EXPIRADA);
        Reserva saved = reservaRepository.save(reserva);

        log.info("Reservation expired successfully: id={}, expiraEm={}",
                 saved.getId(), saved.getExpiraEm());
        return saved;
    }

    /**
     * Process expiration for all reservations that should expire.
     * Used by scheduled job.
     *
     * @return Count of expired reservations
     */
    @Transactional
    public int processarExpiracao() {
        log.debug("Processing reservation expirations");

        List<Reserva> paraExpirar = reservaRepository.findReservasParaExpirar(LocalDateTime.now());

        int count = 0;
        for (Reserva reserva : paraExpirar) {
            try {
                expirarReserva(reserva.getId());
                count++;
            } catch (Exception e) {
                log.error("Failed to expire reservation id={}: {}", reserva.getId(), e.getMessage());
            }
        }

        log.info("Processed expiration for {} reservations", count);
        return count;
    }

    /**
     * Get reservations for a modelo within a date range.
     * Used for calendar view and availability planning.
     *
     * @param modeloId Modelo UUID
     * @param dataInicio Start of period
     * @param dataFim End of period
     * @return List of reservations in period for this modelo
     */
    @Transactional(readOnly = true)
    public List<Reserva> getReservationsByModeloInPeriod(UUID modeloId, LocalDateTime dataInicio, LocalDateTime dataFim) {
        log.debug("Getting reservations by modelo in period: modelo={}, periodo={} to {}",
                  modeloId, dataInicio, dataFim);
        return reservaRepository.findByModeloAndPeriod(modeloId, dataInicio, dataFim);
    }

    /**
     * Get detailed availability information for a modelo in a period.
     * Returns metrics about capacity, reservations, and available slots.
     *
     * Used by:
     * - Operators to check availability before creating reservations
     * - Frontend calendar view
     * - Availability API endpoints
     *
     * @param modeloId Modelo UUID
     * @param dataInicio Start of period
     * @param dataFimPrevista End of period
     * @return Detailed availability metrics
     */
    @Transactional(readOnly = true)
    public DisponibilidadeDetalhada verificarDisponibilidadeDetalhada(
        UUID modeloId,
        LocalDateTime dataInicio,
        LocalDateTime dataFimPrevista
    ) {
        log.debug("Checking detailed availability: modelo={}, periodo={} to {}",
                  modeloId, dataInicio, dataFimPrevista);

        // Validate modelo exists
        Modelo modelo = modeloService.findById(modeloId);

        // Get tenant configuration
        ReservaConfig config = reservaConfigService.getConfigForCurrentTenant();

        // Count total available jetskis for this modelo
        long totalJetskis = jetskiRepository.countByModeloIdAndAtivoAndStatus(
            modeloId,
            true,
            JetskiStatus.DISPONIVEL
        );

        // Count guaranteed reservations (ALTA priority with deposit)
        long reservasGarantidas = reservaRepository.countReservasGarantidasForModelo(
            modeloId,
            dataInicio,
            dataFimPrevista
        );

        // Count total active reservations (both ALTA and BAIXA)
        long totalReservas = reservaRepository.countActiveReservasForModelo(
            modeloId,
            dataInicio,
            dataFimPrevista
        );

        // Calculate maximum allowed reservations based on overbooking config
        long maximoReservas = config.calcularMaximoReservas(totalJetskis);

        // Calculate remaining slots
        long vagasGarantidas = totalJetskis - reservasGarantidas;
        long vagasRegulares = maximoReservas - totalReservas;

        // Check if can accept new reservations
        boolean aceitaComSinal = vagasGarantidas > 0;
        boolean aceitaSemSinal = vagasRegulares > 0;

        return DisponibilidadeDetalhada.builder()
            .modeloId(modeloId)
            .modeloNome(modelo.getNome())
            .dataInicio(dataInicio)
            .dataFimPrevista(dataFimPrevista)
            .totalJetskis(totalJetskis)
            .reservasGarantidas(reservasGarantidas)
            .totalReservas(totalReservas)
            .maximoReservas(maximoReservas)
            .aceitaComSinal(aceitaComSinal)
            .aceitaSemSinal(aceitaSemSinal)
            .vagasGarantidas(Math.max(0, vagasGarantidas))
            .vagasRegulares(Math.max(0, vagasRegulares))
            .build();
    }

    /**
     * Internal DTO for detailed availability information.
     * Can be converted to DisponibilidadeResponse by controller.
     */
    public static class DisponibilidadeDetalhada {
        private final UUID modeloId;
        private final String modeloNome;
        private final LocalDateTime dataInicio;
        private final LocalDateTime dataFimPrevista;
        private final long totalJetskis;
        private final long reservasGarantidas;
        private final long totalReservas;
        private final long maximoReservas;
        private final boolean aceitaComSinal;
        private final boolean aceitaSemSinal;
        private final long vagasGarantidas;
        private final long vagasRegulares;

        @lombok.Builder
        public DisponibilidadeDetalhada(
            UUID modeloId,
            String modeloNome,
            LocalDateTime dataInicio,
            LocalDateTime dataFimPrevista,
            long totalJetskis,
            long reservasGarantidas,
            long totalReservas,
            long maximoReservas,
            boolean aceitaComSinal,
            boolean aceitaSemSinal,
            long vagasGarantidas,
            long vagasRegulares
        ) {
            this.modeloId = modeloId;
            this.modeloNome = modeloNome;
            this.dataInicio = dataInicio;
            this.dataFimPrevista = dataFimPrevista;
            this.totalJetskis = totalJetskis;
            this.reservasGarantidas = reservasGarantidas;
            this.totalReservas = totalReservas;
            this.maximoReservas = maximoReservas;
            this.aceitaComSinal = aceitaComSinal;
            this.aceitaSemSinal = aceitaSemSinal;
            this.vagasGarantidas = vagasGarantidas;
            this.vagasRegulares = vagasRegulares;
        }

        public UUID getModeloId() { return modeloId; }
        public String getModeloNome() { return modeloNome; }
        public LocalDateTime getDataInicio() { return dataInicio; }
        public LocalDateTime getDataFimPrevista() { return dataFimPrevista; }
        public long getTotalJetskis() { return totalJetskis; }
        public long getReservasGarantidas() { return reservasGarantidas; }
        public long getTotalReservas() { return totalReservas; }
        public long getMaximoReservas() { return maximoReservas; }
        public boolean isAceitaComSinal() { return aceitaComSinal; }
        public boolean isAceitaSemSinal() { return aceitaSemSinal; }
        public long getVagasGarantidas() { return vagasGarantidas; }
        public long getVagasRegulares() { return vagasRegulares; }
    }
}
