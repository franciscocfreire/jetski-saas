package com.jetski.locacoes.internal;

import com.jetski.combustivel.internal.LocacaoFuelData;
import com.jetski.locacoes.domain.*;
import com.jetski.locacoes.domain.event.CheckInEvent;
import com.jetski.locacoes.domain.event.CheckOutEvent;
import com.jetski.locacoes.domain.event.RentalCompletedEvent;
import com.jetski.shared.security.TenantContext;
import com.jetski.locacoes.internal.repository.LocacaoItemOpcionalRepository;
import com.jetski.locacoes.internal.repository.LocacaoRepository;
import com.jetski.locacoes.internal.repository.ReservaRepository;
import com.jetski.shared.exception.BusinessException;
import com.jetski.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Service: LocacaoService
 *
 * Business logic for rental operations (check-in and check-out).
 *
 * Key Operations:
 * - Check-in: Convert reservation → rental or create walk-in rental
 * - Check-out: Complete rental with value calculation (RN01)
 * - Query: List and filter rentals
 *
 * Business Rules:
 * - RN01: Billable time calculation with tolerance and rounding
 * - Jetski must be DISPONIVEL for check-in
 * - Only one active rental per jetski
 * - Reservation must be CONFIRMADA for check-in
 *
 * @author Jetski Team
 * @since 0.7.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocacaoService {

    private final LocacaoRepository locacaoRepository;
    private final ReservaRepository reservaRepository;
    private final LocacaoItemOpcionalRepository locacaoItemOpcionalRepository;
    private final JetskiService jetskiService;
    private final ModeloService modeloService;
    private final ClienteService clienteService;
    private final LocacaoCalculatorService calculatorService;
    private final com.jetski.locacoes.internal.PhotoValidationService photoValidationService;
    private final com.jetski.combustivel.internal.FuelPolicyService fuelPolicyService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Check-in: Create rental from reservation
     *
     * Process:
     * 1. Validate reservation (CONFIRMADA status)
     * 2. Validate jetski (DISPONIVEL status)
     * 3. Create Locacao with EM_CURSO status
     * 4. Update jetski status → EM_USO
     * 5. Update reserva status → FINALIZADA
     * 6. Link locacao to reserva
     *
     * @param tenantId Tenant ID
     * @param reservaId Reservation ID
     * @param horimetroInicio Hourmeter reading at check-in
     * @param observacoes Optional notes
     * @param checklistSaidaJson Check-in checklist JSON
     * @param valorNegociado Optional negotiated price (if set, used instead of calculated value)
     * @param motivoDesconto Optional reason for negotiated price
     * @param modalidadePreco Pricing mode (PRECO_FECHADO, DIARIA, MEIA_DIARIA)
     * @return Created Locacao
     */
    @Transactional
    public Locacao checkInFromReservation(UUID tenantId, UUID reservaId, BigDecimal horimetroInicio,
                                           String observacoes, String checklistSaidaJson,
                                           BigDecimal valorNegociado, String motivoDesconto,
                                           ModalidadePreco modalidadePreco) {
        log.info("Check-in from reservation: tenant={}, reserva={}", tenantId, reservaId);

        // 1. Find and validate reservation
        Reserva reserva = reservaRepository.findById(reservaId)
            .orElseThrow(() -> new NotFoundException("Reserva não encontrada: " + reservaId));

        if (!reserva.getTenantId().equals(tenantId)) {
            throw new NotFoundException("Reserva não encontrada: " + reservaId);
        }

        if (reserva.getStatus() != Reserva.ReservaStatus.CONFIRMADA) {
            throw new BusinessException(
                String.format("Reserva deve estar CONFIRMADA para check-in (status atual: %s)",
                              reserva.getStatus())
            );
        }

        if (reserva.getJetskiId() == null) {
            throw new BusinessException(
                "Reserva não tem jetski alocado. Aloque um jetski antes do check-in."
            );
        }

        // 2. Validate jetski availability
        Jetski jetski = jetskiService.findById(reserva.getJetskiId());
        validateJetskiForCheckIn(jetski);

        // 3. Calculate expected duration
        int duracaoPrevista = calculateExpectedDuration(reserva.getDataInicio(), reserva.getDataFimPrevista());

        // 4. Create Locacao
        Locacao locacao = Locacao.builder()
            .tenantId(tenantId)
            .reservaId(reservaId)
            .jetskiId(jetski.getId())
            .clienteId(reserva.getClienteId())
            .vendedorId(reserva.getVendedorId())
            .dataCheckIn(LocalDateTime.now())
            .horimetroInicio(horimetroInicio)
            .duracaoPrevista(duracaoPrevista)
            .status(LocacaoStatus.EM_CURSO)
            .observacoes(observacoes)
            .checklistSaidaJson(checklistSaidaJson)
            .valorNegociado(valorNegociado)
            .motivoDesconto(motivoDesconto)
            .modalidadePreco(modalidadePreco != null ? modalidadePreco : ModalidadePreco.PRECO_FECHADO)
            .build();

        locacao = locacaoRepository.save(locacao);
        log.info("Locacao created: id={}, jetski={}, valorNegociado={}, modalidade={}", locacao.getId(), jetski.getId(), valorNegociado, locacao.getModalidadePreco());

        // 5. Validate 4 mandatory check-in photos
        // TODO FASE 2: Photo validation temporarily disabled during check-in
        // Photos cannot exist before locacao creation (they need locacao_id), but this validation
        // expects them to exist immediately after creation. This is a design flaw that needs to be
        // addressed in a future sprint. Photos should either be:
        // 1) Uploaded AFTER check-in completes, or
        // 2) Sent as part of the check-in request payload (requires API redesign)
        // For now, photo validation is only enforced at check-out.
        // photoValidationService.validateCheckInPhotos(tenantId, locacao.getId());

        // 6. Update jetski status
        jetskiService.updateStatus(jetski.getId(), JetskiStatus.LOCADO);

        // 6. Update reserva
        reserva.setStatus(Reserva.ReservaStatus.FINALIZADA);
        reserva.setLocacaoId(locacao.getId());
        reservaRepository.save(reserva);

        log.info("Check-in completed: locacao={}, reserva={}", locacao.getId(), reservaId);

        // Publish check-in event for audit logging
        UUID operadorId = TenantContext.getUsuarioId();
        eventPublisher.publishEvent(CheckInEvent.fromReservation(
            tenantId,
            locacao.getId(),
            jetski.getId(),
            reservaId,
            reserva.getClienteId(),
            operadorId,
            horimetroInicio != null ? horimetroInicio.intValue() : null,
            locacao.getDataCheckIn()
        ));
        log.debug("Published CheckInEvent (from reservation) for locacao={}", locacao.getId());

        return locacao;
    }

    /**
     * Check-in: Walk-in customer (without reservation)
     *
     * Process:
     * 1. Validate jetski (DISPONIVEL status)
     * 2. Validate cliente exists
     * 3. Create Locacao with EM_CURSO status
     * 4. Update jetski status → EM_USO
     *
     * @param tenantId Tenant ID
     * @param jetskiId Jetski ID
     * @param clienteId Cliente ID
     * @param vendedorId Optional Vendedor ID
     * @param horimetroInicio Hourmeter reading at check-in
     * @param duracaoPrevista Expected duration in minutes
     * @param observacoes Optional notes
     * @param checklistSaidaJson Check-in checklist JSON
     * @param valorNegociado Optional negotiated price (if set, used instead of calculated value)
     * @param motivoDesconto Optional reason for negotiated price
     * @param modalidadePreco Pricing mode (PRECO_FECHADO, DIARIA, MEIA_DIARIA)
     * @param dataCheckIn Custom check-in time (if null, uses current time)
     * @return Created Locacao
     */
    @Transactional
    public Locacao checkInWalkIn(UUID tenantId, UUID jetskiId, UUID clienteId, UUID vendedorId,
                                  BigDecimal horimetroInicio, Integer duracaoPrevista, String observacoes,
                                  String checklistSaidaJson, BigDecimal valorNegociado, String motivoDesconto,
                                  ModalidadePreco modalidadePreco, LocalDateTime dataCheckIn) {
        log.info("Walk-in check-in: tenant={}, jetski={}, cliente={}", tenantId, jetskiId, clienteId);

        // 1. Validate jetski
        Jetski jetski = jetskiService.findById(jetskiId);
        validateJetskiForCheckIn(jetski);

        // 2. Validate cliente exists (only if provided - check-in rápido allows null)
        if (clienteId != null) {
            clienteService.findById(clienteId);
        }

        // 3. Create Locacao
        Locacao locacao = Locacao.builder()
            .tenantId(tenantId)
            .reservaId(null)  // Walk-in has no reservation
            .jetskiId(jetskiId)
            .clienteId(clienteId)
            .vendedorId(vendedorId)
            .dataCheckIn(dataCheckIn != null ? dataCheckIn : LocalDateTime.now())
            .horimetroInicio(horimetroInicio)
            .duracaoPrevista(duracaoPrevista)
            .status(LocacaoStatus.EM_CURSO)
            .observacoes(observacoes)
            .checklistSaidaJson(checklistSaidaJson)
            .valorNegociado(valorNegociado)
            .motivoDesconto(motivoDesconto)
            .modalidadePreco(modalidadePreco != null ? modalidadePreco : ModalidadePreco.PRECO_FECHADO)
            .build();

        locacao = locacaoRepository.save(locacao);
        log.info("Locacao created (walk-in): id={}, valorNegociado={}, modalidade={}", locacao.getId(), valorNegociado, locacao.getModalidadePreco());

        // 4. Validate 4 mandatory check-in photos
        // TODO FASE 2: Photo validation temporarily disabled during check-in (same reason as above)
        // photoValidationService.validateCheckInPhotos(tenantId, locacao.getId());

        // 5. Update jetski status
        jetskiService.updateStatus(jetskiId, JetskiStatus.LOCADO);

        log.info("Walk-in check-in completed: locacao={}", locacao.getId());

        // Publish check-in event for audit logging
        UUID operadorId = TenantContext.getUsuarioId();
        eventPublisher.publishEvent(CheckInEvent.walkIn(
            tenantId,
            locacao.getId(),
            jetskiId,
            clienteId,
            operadorId,
            horimetroInicio != null ? horimetroInicio.intValue() : null,
            locacao.getDataCheckIn()
        ));
        log.debug("Published CheckInEvent (walk-in) for locacao={}", locacao.getId());

        return locacao;
    }

    /**
     * Check-out: Complete rental with value calculation
     *
     * Process:
     * 1. Validate locacao exists and is EM_CURSO
     * 2. Validate horimetro readings
     * 3. Calculate used minutes from horimeters
     * 4. Apply RN01: billable minutes (tolerance + rounding)
     * 5. Calculate valor_base from billable minutes
     * 6. Update locacao with calculated values
     * 7. Update jetski status → DISPONIVEL
     * 8. Update locacao status → FINALIZADA
     *
     * @param tenantId Tenant ID
     * @param locacaoId Locacao ID
     * @param horimetroFim Hourmeter reading at check-out
     * @param observacoes Optional notes
     * @param skipPhotos If true, skip photo validation (photos can be added later)
     * @return Updated Locacao with calculated values
     */
    @Transactional
    public Locacao checkOut(UUID tenantId, UUID locacaoId, BigDecimal horimetroFim, String observacoes, String checklistEntradaJson, Boolean skipPhotos) {
        log.info("Check-out: tenant={}, locacao={}", tenantId, locacaoId);

        // 1. Find and validate locacao
        Locacao locacao = locacaoRepository.findByIdAndTenantId(locacaoId, tenantId)
            .orElseThrow(() -> new NotFoundException("Locação não encontrada: " + locacaoId));

        if (!locacao.isEmCurso()) {
            throw new BusinessException(
                String.format("Locação deve estar EM_CURSO para check-out (status atual: %s)",
                              locacao.getStatus())
            );
        }

        // 2. Validate horimetro readings
        calculatorService.validateHorimetroReadings(locacao.getHorimetroInicio(), horimetroFim);

        // 3. Calculate used minutes from actual rental time (not hourmeter)
        // IMPORTANT: We charge for the time the customer had the jetski, not the engine runtime
        LocalDateTime dataCheckOut = LocalDateTime.now();
        int minutosUsados = calculatorService.calculateUsedMinutes(
            locacao.getDataCheckIn(),
            dataCheckOut
        );

        // Also calculate engine minutes for maintenance tracking (not used for billing)
        int minutosMotor = calculatorService.calculateEngineMinutes(
            locacao.getHorimetroInicio(),
            horimetroFim
        );
        log.debug("Rental duration: {}min (billing), Engine runtime: {}min (maintenance tracking)",
                  minutosUsados, minutosMotor);

        // 4. Get modelo to retrieve tolerance and price
        Jetski jetski = jetskiService.findById(locacao.getJetskiId());
        Modelo modelo = modeloService.findById(jetski.getModeloId());

        int toleranciaMinutos = modelo.getToleranciaMin() != null ? modelo.getToleranciaMin() : 0;

        // 5. Apply RN01: Calculate billable minutes
        int minutosFaturaveis = calculatorService.calculateBillableMinutes(
            minutosUsados,
            toleranciaMinutos
        );

        // 6. Determine base value based on pricing modality
        BigDecimal valorBase;
        ModalidadePreco modalidade = locacao.getModalidadePreco() != null
            ? locacao.getModalidadePreco()
            : ModalidadePreco.PRECO_FECHADO;  // Default

        if (locacao.getValorNegociado() != null) {
            // Use negotiated price set at check-in
            valorBase = locacao.getValorNegociado();
            log.info("Using negotiated price for locacao {}: {}", locacaoId, valorBase);
        } else if (modalidade == ModalidadePreco.PRECO_FECHADO && locacao.getDuracaoPrevista() != null) {
            // PRECO_FECHADO: Calculate based on predicted duration, not actual time used
            // This ensures customer pays for reserved time regardless of early return
            int duracaoPrevistaMinutos = locacao.getDuracaoPrevista();
            int minutosFaturaveisPrevisto = calculatorService.calculateBillableMinutes(
                duracaoPrevistaMinutos,
                toleranciaMinutos
            );
            valorBase = calculatorService.calculateBaseValue(
                minutosFaturaveisPrevisto,
                modelo.getPrecoBaseHora()
            );
            log.info("PRECO_FECHADO: Using predicted duration for locacao {}: {}min → {}",
                     locacaoId, duracaoPrevistaMinutos, valorBase);
        } else {
            // DIARIA/MEIA_DIARIA or other: Calculate base value from actual billable minutes
            valorBase = calculatorService.calculateBaseValue(
                minutosFaturaveis,
                modelo.getPrecoBaseHora()
            );
        }

        // 7. Update locacao with intermediate values (needed for fuel cost calculation)
        locacao.setDataCheckOut(dataCheckOut);
        locacao.setHorimetroFim(horimetroFim);
        locacao.setMinutosUsados(minutosUsados);
        locacao.setMinutosFaturaveis(minutosFaturaveis);

        // 8. RN03: Calculate fuel cost based on policy hierarchy (JETSKI → MODELO → GLOBAL)
        LocacaoFuelData fuelData = LocacaoFuelData.builder()
            .id(locacao.getId())
            .tenantId(locacao.getTenantId())
            .jetskiId(locacao.getJetskiId())
            .dataCheckOut(locacao.getDataCheckOut().toInstant(java.time.ZoneOffset.UTC))
            .minutosFaturaveis(locacao.getMinutosFaturaveis())
            .build();

        BigDecimal combustivelCusto = fuelPolicyService.calcularCustoCombustivel(fuelData, modelo.getId());

        log.info("Fuel cost calculated: locacao={}, cost={}",
                 locacaoId, combustivelCusto);

        // 9. Calculate optional items cost
        BigDecimal valorItensOpcionais = locacaoItemOpcionalRepository
            .sumValorCobradoByLocacaoId(locacaoId);
        long countItensOpcionais = locacaoItemOpcionalRepository.countByLocacaoId(locacaoId);

        log.info("Optional items cost calculated: locacao={}, count={}, total={}",
                 locacaoId, countItensOpcionais, valorItensOpcionais);

        // 10. Calculate total value: base + fuel + optional items
        BigDecimal valorTotal = valorBase.add(combustivelCusto).add(valorItensOpcionais);

        // Store policy ID for audit (will be null if INCLUSO mode, as no charge applied)
        Long fuelPolicyId = null;  // TODO: Return policy ID from calcularCustoCombustivel

        // 10. RN05: Validate checklist is present
        if (checklistEntradaJson == null || checklistEntradaJson.isBlank()) {
            throw new BusinessException("Check-out requer checklist obrigatório (RN05)");
        }

        // 11. RN05: Validate 4 mandatory check-out photos (unless skipPhotos is true)
        if (skipPhotos == null || !skipPhotos) {
            photoValidationService.validateCheckOutPhotos(tenantId, locacaoId);
        } else {
            log.info("Skipping photo validation for check-out: locacao={}", locacaoId);
        }

        // 12. Update locacao with final calculated values
        locacao.setValorBase(valorBase);
        locacao.setCombustivelCusto(combustivelCusto);
        locacao.setFuelPolicyId(fuelPolicyId);
        locacao.setValorTotal(valorTotal);  // valorBase + combustivel
        locacao.setChecklistEntradaJson(checklistEntradaJson);
        locacao.setStatus(LocacaoStatus.FINALIZADA);

        if (observacoes != null && !observacoes.isBlank()) {
            String currentObs = locacao.getObservacoes();
            locacao.setObservacoes(currentObs == null ? observacoes : currentObs + "\n" + observacoes);
        }

        locacao = locacaoRepository.save(locacao);

        log.info("Check-out completed: locacao={}, used={}min, billable={}min, base={}, fuel={}, optional_items={}, total={}",
                 locacaoId, minutosUsados, minutosFaturaveis, valorBase, combustivelCusto, valorItensOpcionais, valorTotal);

        // 10. RN07: Update jetski odometer and check for maintenance alerts
        jetski.setHorimetroAtual(horimetroFim);
        Jetski updatedJetski = jetskiService.updateJetski(jetski.getId(), jetski);

        if (updatedJetski.requiresMaintenanceAlert()) {
            log.warn("RN07: Jetski {} atingiu marco de manutenção: {} horas. " +
                    "Favor criar OS de manutenção preventiva.",
                    updatedJetski.getSerie(), updatedJetski.getHorimetroAtual().intValue());
        }

        // 11. Update jetski status
        jetskiService.updateStatus(jetski.getId(), JetskiStatus.DISPONIVEL);

        // 12. Publish rental completed event (for cache invalidation, metrics, etc.)
        eventPublisher.publishEvent(RentalCompletedEvent.of(
            tenantId,
            locacao.getId(),
            locacao.getValorTotal(),
            locacao.getDataCheckOut()
        ));
        log.debug("Published RentalCompletedEvent for locacao={}", locacaoId);

        // 13. Publish check-out event for audit logging
        UUID operadorId = TenantContext.getUsuarioId();
        eventPublisher.publishEvent(CheckOutEvent.of(
            tenantId,
            locacao.getId(),
            locacao.getJetskiId(),
            locacao.getClienteId(),
            operadorId,
            horimetroFim != null ? horimetroFim.intValue() : null,
            minutosUsados,
            valorTotal,
            dataCheckOut
        ));
        log.debug("Published CheckOutEvent for locacao={}", locacaoId);

        log.info("Check-out completed: locacao={}", locacaoId);

        return locacao;
    }

    /**
     * Find locacao by ID
     */
    @Transactional(readOnly = true)
    public Locacao findById(UUID locacaoId) {
        return locacaoRepository.findById(locacaoId)
            .orElseThrow(() -> new NotFoundException("Locação não encontrada: " + locacaoId));
    }

    /**
     * List all locacoes for a tenant
     */
    @Transactional(readOnly = true)
    public List<Locacao> listByTenant(UUID tenantId) {
        return locacaoRepository.findByTenantId(tenantId);
    }

    /**
     * List locacoes by status
     */
    @Transactional(readOnly = true)
    public List<Locacao> listByStatus(UUID tenantId, LocacaoStatus status) {
        return locacaoRepository.findByTenantIdAndStatusOrderByDataCheckInDesc(tenantId, status);
    }

    /**
     * List locacoes for a jetski
     */
    @Transactional(readOnly = true)
    public List<Locacao> listByJetski(UUID tenantId, UUID jetskiId) {
        return locacaoRepository.findByTenantIdAndJetskiIdOrderByDataCheckInDesc(tenantId, jetskiId);
    }

    /**
     * List locacoes for a cliente
     */
    @Transactional(readOnly = true)
    public List<Locacao> listByCliente(UUID tenantId, UUID clienteId) {
        return locacaoRepository.findByTenantIdAndClienteIdOrderByDataCheckInDesc(tenantId, clienteId);
    }

    /**
     * Associar cliente a uma locação existente
     *
     * Usado quando o check-in foi feito sem cliente (check-in rápido)
     * e o operador deseja associar o cliente posteriormente.
     *
     * Validações:
     * - Locação deve existir
     * - Locação deve estar EM_CURSO (não finalizada)
     * - Locação não deve ter cliente já associado
     * - Cliente deve existir
     *
     * @param tenantId Tenant ID
     * @param locacaoId Locacao ID
     * @param clienteId Cliente ID a associar
     * @return Locacao atualizada
     */
    @Transactional
    public Locacao associarCliente(UUID tenantId, UUID locacaoId, UUID clienteId) {
        log.info("Associando cliente à locação: tenant={}, locacao={}, cliente={}",
                 tenantId, locacaoId, clienteId);

        // 1. Find locacao
        Locacao locacao = locacaoRepository.findById(locacaoId)
            .orElseThrow(() -> new NotFoundException("Locação não encontrada: " + locacaoId));

        // 2. Validate tenant
        if (!locacao.getTenantId().equals(tenantId)) {
            throw new NotFoundException("Locação não encontrada: " + locacaoId);
        }

        // 3. Validate status - must be EM_CURSO
        if (locacao.getStatus() != LocacaoStatus.EM_CURSO) {
            throw new BusinessException(
                String.format("Só é possível associar cliente a locações em curso (status atual: %s)",
                              locacao.getStatus())
            );
        }

        // 4. Validate no client already associated
        if (locacao.getClienteId() != null) {
            throw new BusinessException("Esta locação já possui um cliente associado");
        }

        // 5. Validate cliente exists
        clienteService.findById(clienteId);

        // 6. Associate cliente
        locacao.setClienteId(clienteId);
        locacao = locacaoRepository.save(locacao);

        log.info("Cliente associado com sucesso: locacao={}, cliente={}", locacaoId, clienteId);

        return locacao;
    }

    /**
     * Update check-in date/time of an existing rental.
     *
     * Allows operator to correct the start time if check-in was registered
     * at a different time than the actual start.
     *
     * Validations:
     * - Rental must exist
     * - Rental must be EM_CURSO (not finished)
     *
     * @param tenantId Tenant ID
     * @param locacaoId Locacao ID
     * @param dataCheckIn New check-in date/time
     * @return Updated Locacao
     */
    @Transactional
    public Locacao updateDataCheckIn(UUID tenantId, UUID locacaoId, LocalDateTime dataCheckIn) {
        log.info("Atualizando data de check-in: tenant={}, locacao={}, novaData={}",
                 tenantId, locacaoId, dataCheckIn);

        // 1. Find locacao
        Locacao locacao = locacaoRepository.findById(locacaoId)
            .orElseThrow(() -> new NotFoundException("Locação não encontrada: " + locacaoId));

        // 2. Validate tenant
        if (!locacao.getTenantId().equals(tenantId)) {
            throw new NotFoundException("Locação não encontrada: " + locacaoId);
        }

        // 3. Validate status - must be EM_CURSO
        if (locacao.getStatus() != LocacaoStatus.EM_CURSO) {
            throw new BusinessException(
                String.format("Só é possível alterar o horário de locações em curso (status atual: %s)",
                              locacao.getStatus())
            );
        }

        // 4. Update dataCheckIn
        LocalDateTime oldDataCheckIn = locacao.getDataCheckIn();
        locacao.setDataCheckIn(dataCheckIn);
        locacao = locacaoRepository.save(locacao);

        log.info("Data de check-in atualizada: locacao={}, de {} para {}",
                 locacaoId, oldDataCheckIn, dataCheckIn);

        return locacao;
    }

    // ===================================================================
    // Helper Methods
    // ===================================================================

    private void validateJetskiForCheckIn(Jetski jetski) {
        if (jetski.getStatus() != JetskiStatus.DISPONIVEL) {
            throw new BusinessException(
                String.format("Jetski %s não está disponível para check-in (status: %s)",
                              jetski.getSerie(), jetski.getStatus())
            );
        }

        // Check if jetski already has active rental
        boolean hasActiveRental = locacaoRepository.existsByTenantIdAndJetskiIdAndStatus(
            jetski.getTenantId(),
            jetski.getId(),
            LocacaoStatus.EM_CURSO
        );

        if (hasActiveRental) {
            throw new BusinessException(
                String.format("Jetski %s já possui locação ativa", jetski.getSerie())
            );
        }
    }

    private int calculateExpectedDuration(LocalDateTime dataInicio, LocalDateTime dataFimPrevista) {
        return (int) java.time.Duration.between(dataInicio, dataFimPrevista).toMinutes();
    }
}
