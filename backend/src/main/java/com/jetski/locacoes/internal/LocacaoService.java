package com.jetski.locacoes.internal;

import com.jetski.locacoes.api.ModeloService;
import com.jetski.combustivel.internal.LocacaoFuelData;
import com.jetski.locacoes.api.FechamentoLockChecker;
import com.jetski.locacoes.api.dto.EditFinalizadaLocacaoRequest;
import com.jetski.locacoes.domain.*;
import com.jetski.locacoes.event.CheckInEvent;
import com.jetski.locacoes.event.CheckOutEvent;
import com.jetski.locacoes.event.DataCheckInAlteradaEvent;
import com.jetski.locacoes.event.LocacaoEditadaEvent;
import com.jetski.locacoes.event.RentalCompletedEvent;
import com.jetski.shared.security.TenantContext;
import com.jetski.tenant.TenantTimeService;
import com.jetski.comissoes.api.CommissionService;
import com.jetski.locacoes.internal.repository.LocacaoItemOpcionalRepository;
import com.jetski.locacoes.internal.repository.LocacaoRepository;
import com.jetski.locacoes.internal.repository.PresencaVendedorRepository;
import com.jetski.locacoes.internal.repository.ReservaLancamentoRepository;
import com.jetski.locacoes.internal.repository.ReservaRepository;
import com.jetski.locacoes.internal.repository.VendedorRepository;
import com.jetski.reservas.domain.event.PagamentoLocacaoRegistradoEvent;
import com.jetski.shared.exception.BusinessException;
import com.jetski.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private final com.jetski.tenant.PlanoLimiteService planoLimiteService;
    private final ReservaRepository reservaRepository;
    private final ReservaLancamentoRepository reservaLancamentoRepository;
    private final LocacaoItemOpcionalRepository locacaoItemOpcionalRepository;
    private final FechamentoLockChecker fechamentoLockChecker;
    private final PresencaVendedorRepository presencaVendedorRepository;
    private final VendedorRepository vendedorRepository;
    private final JetskiService jetskiService;
    private final ModeloService modeloService;
    private final ClienteService clienteService;
    private final LocacaoCalculatorService calculatorService;
    private final com.jetski.locacoes.internal.PhotoValidationService photoValidationService;
    private final com.jetski.combustivel.internal.FuelPolicyService fuelPolicyService;
    private final ApplicationEventPublisher eventPublisher;
    private final TenantTimeService tenantTimeService;
    private final CommissionService commissionService;

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

        verificarLimiteLocacoesDoMes(tenantId);

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
            .dataCheckIn(tenantTimeService.now())
            .horimetroInicio(horimetroInicio)
            .duracaoPrevista(duracaoPrevista)
            .status(LocacaoStatus.EM_CURSO)
            .observacoes(observacoes)
            .checklistSaidaJson(checklistSaidaJson)
            .valorNegociado(valorNegociado)
            .motivoDesconto(motivoDesconto)
            .modalidadePreco(modalidadePreco != null ? modalidadePreco : ModalidadePreco.PRECO_FECHADO)
            .build();

        // 4.1 Calculate estimated valorBase for display while rental is in progress
        // IMPORTANTE: valorBase é sempre o preço tabelado (calculado a partir do modelo)
        // O valorNegociado é armazenado separadamente para permitir comparação
        if (duracaoPrevista > 0) {
            Modelo modelo = modeloService.findById(jetski.getModeloId());
            if (modelo != null && modelo.getPrecoBaseHora() != null) {
                BigDecimal estimatedValue = calculatorService.calculateBaseValue(
                    duracaoPrevista,
                    modelo.getPrecoBaseHora()
                );
                locacao.setValorBase(estimatedValue);
            }
        }

        locacao = locacaoRepository.save(locacao);
        log.info("Locacao created: id={}, jetski={}, valorNegociado={}, valorBase={}, modalidade={}",
                 locacao.getId(), jetski.getId(), valorNegociado, locacao.getValorBase(), locacao.getModalidadePreco());

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

        verificarLimiteLocacoesDoMes(tenantId);

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
            .dataCheckIn(dataCheckIn != null ? dataCheckIn : tenantTimeService.now())
            .horimetroInicio(horimetroInicio)
            .duracaoPrevista(duracaoPrevista)
            .status(LocacaoStatus.EM_CURSO)
            .observacoes(observacoes)
            .checklistSaidaJson(checklistSaidaJson)
            .valorNegociado(valorNegociado)
            .motivoDesconto(motivoDesconto)
            .modalidadePreco(modalidadePreco != null ? modalidadePreco : ModalidadePreco.PRECO_FECHADO)
            .build();

        // 3.1 Calculate estimated valorBase for display while rental is in progress
        // IMPORTANTE: valorBase é sempre o preço tabelado (calculado a partir do modelo)
        // O valorNegociado é armazenado separadamente para permitir comparação
        if (duracaoPrevista != null && duracaoPrevista > 0) {
            Modelo modelo = modeloService.findById(jetski.getModeloId());
            if (modelo != null && modelo.getPrecoBaseHora() != null) {
                BigDecimal estimatedValue = calculatorService.calculateBaseValue(
                    duracaoPrevista,
                    modelo.getPrecoBaseHora()
                );
                locacao.setValorBase(estimatedValue);
            }
        }

        locacao = locacaoRepository.save(locacao);
        log.info("Locacao created (walk-in): id={}, valorNegociado={}, valorBase={}, modalidade={}",
                 locacao.getId(), valorNegociado, locacao.getValorBase(), locacao.getModalidadePreco());

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
        LocalDateTime dataCheckOut = tenantTimeService.now();
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
        // IMPORTANTE: valorBase é sempre calculado a partir do preço do modelo (preço tabelado)
        // Isso permite comparar com valorTotal para determinar se houve desconto
        BigDecimal valorBase;
        ModalidadePreco modalidade = locacao.getModalidadePreco() != null
            ? locacao.getModalidadePreco()
            : ModalidadePreco.PRECO_FECHADO;  // Default

        if (modalidade == ModalidadePreco.PRECO_FECHADO && locacao.getDuracaoPrevista() != null) {
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

        // 10. Calculate total value: (negotiated OR base) + fuel + optional items
        // Se há valor negociado, usa ele; senão usa valor base (preço tabelado)
        BigDecimal valorParaCobranca = locacao.getValorNegociado() != null
            ? locacao.getValorNegociado()
            : valorBase;
        BigDecimal valorTotal = valorParaCobranca.add(combustivelCusto).add(valorItensOpcionais);

        if (locacao.getValorNegociado() != null) {
            log.info("Using negotiated price for locacao {}: valorNegociado={}, valorBase={} (diferença={})",
                     locacaoId, locacao.getValorNegociado(), valorBase,
                     valorBase.subtract(locacao.getValorNegociado()));
        }

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

        // Folio (fase 3): as cobranças apuradas viram lançamentos derivados
        // (sem forma), ancorados na locação e — quando houver — na reserva.
        lancarCobrancas(locacao, valorParaCobranca, combustivelCusto, valorItensOpcionais);

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

        // 12.1 RN04: Calculate commission if a seller is associated
        if (locacao.getVendedorId() != null) {
            try {
                commissionService.calcularComissao(
                    tenantId,
                    locacao.getId(),
                    locacao.getVendedorId(),
                    modelo.getId(),
                    minutosFaturaveis,
                    valorTotal,
                    combustivelCusto,
                    BigDecimal.ZERO,  // multas
                    BigDecimal.ZERO,  // taxas
                    null,  // codigoCampanha
                    locacao.getValorBase()  // valorBase para determinar comissão diferenciada
                );
                log.info("Commission calculated for locacao={}, vendedor={}", locacaoId, locacao.getVendedorId());
            } catch (Exception e) {
                // Log error but don't fail checkout - commission can be calculated later
                log.warn("Failed to calculate commission for locacao={}: {}", locacaoId, e.getMessage());
            }

            // 12.2 Auto-register seller attendance if not already registered for the day
            try {
                LocalDate dataVenda = dataCheckOut.toLocalDate();
                boolean presencaExiste = presencaVendedorRepository.existsByVendedorIdAndDtReferencia(
                    locacao.getVendedorId(), dataVenda
                );

                if (!presencaExiste) {
                    Vendedor vendedor = vendedorRepository.findById(locacao.getVendedorId()).orElse(null);
                    if (vendedor != null) {
                        BigDecimal valorDiaria = vendedor.getDiariaBase() != null
                            ? vendedor.getDiariaBase().multiply(BigDecimal.valueOf(TipoPresenca.INTEGRAL.getFator()))
                            : BigDecimal.ZERO;

                        PresencaVendedor presenca = PresencaVendedor.builder()
                            .tenantId(tenantId)
                            .vendedor(vendedor)
                            .dtReferencia(dataVenda)
                            .tipo(TipoPresenca.INTEGRAL)
                            .valorDiaria(valorDiaria)
                            .motivoAjuste("Presença registrada automaticamente por venda")
                            .registradoPor(TenantContext.getUsuarioId())
                            .build();

                        presencaVendedorRepository.save(presenca);
                        log.info("Auto-registered attendance for vendedor={} on date={}",
                                 locacao.getVendedorId(), dataVenda);
                    }
                } else {
                    log.debug("Attendance already exists for vendedor={} on date={}",
                              locacao.getVendedorId(), dataVenda);
                }
            } catch (Exception e) {
                // Log error but don't fail checkout - attendance can be registered manually
                log.warn("Failed to auto-register attendance for locacao={}: {}", locacaoId, e.getMessage());
            }
        }

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

        // 5. Publish audit event
        UUID operadorId = TenantContext.getUsuarioId();
        eventPublisher.publishEvent(DataCheckInAlteradaEvent.of(
            tenantId, locacaoId, operadorId, oldDataCheckIn, dataCheckIn
        ));

        log.info("Data de check-in atualizada: locacao={}, de {} para {}, operador={}",
                 locacaoId, oldDataCheckIn, dataCheckIn, operadorId);

        return locacao;
    }

    /**
     * PRORROGAR: altera a duração prevista de uma locação EM_CURSO.
     *
     * <p>A volta prevista é derivada (dataCheckIn + duracaoPrevista); alterar a
     * duração é a forma de estender/encurtar o passeio na prancheta do dia.
     *
     * <p>Guards (BusinessException → 400, deny de negócio):
     * <ul>
     *   <li>locação deve existir no tenant (NotFoundException → 404)</li>
     *   <li>status deve ser EM_CURSO</li>
     *   <li>nova duração mínima de 5 minutos</li>
     * </ul>
     *
     * @param tenantId Tenant ID
     * @param locacaoId Locacao ID
     * @param duracaoPrevista Nova duração prevista TOTAL, em minutos (substitui a atual)
     * @return Locacao atualizada
     */
    @Transactional
    public Locacao alterarDuracao(UUID tenantId, UUID locacaoId, Integer duracaoPrevista) {
        if (duracaoPrevista == null || duracaoPrevista < 5) {
            throw new BusinessException("Duração prevista mínima é de 5 minutos");
        }

        Locacao locacao = locacaoRepository.findByIdAndTenantId(locacaoId, tenantId)
            .orElseThrow(() -> new NotFoundException("Locação não encontrada: " + locacaoId));

        if (locacao.getStatus() != LocacaoStatus.EM_CURSO) {
            throw new BusinessException(
                String.format("Só é possível prorrogar locações em curso (status atual: %s)",
                              locacao.getStatus())
            );
        }

        Integer duracaoAnterior = locacao.getDuracaoPrevista();
        locacao.setDuracaoPrevista(duracaoPrevista);
        locacao = locacaoRepository.save(locacao);

        log.info("Duração prevista alterada (prorrogação): locacao={}, de {}min para {}min, operador={}",
                 locacaoId, duracaoAnterior, duracaoPrevista, TenantContext.getUsuarioId());

        return locacao;
    }

    /**
     * Altera (ou remove) o vendedor de uma locação EM_CURSO.
     *
     * <p>Correção operacional na prancheta do dia: o operador esqueceu de
     * apontar o vendedor no check-in ou apontou o errado. A comissão é
     * calculada só no check-out/fechamento, então trocar o vendedor durante o
     * passeio é seguro — locação FINALIZADA deve usar editar-finalizada (que
     * recalcula comissão e audita).
     *
     * <p>Guards:
     * <ul>
     *   <li>locação deve existir no tenant (NotFoundException → 404)</li>
     *   <li>status deve ser EM_CURSO (BusinessException → 400)</li>
     *   <li>vendedor, quando informado, deve existir NO TENANT (filtro
     *       explícito — testes bypassam RLS)</li>
     * </ul>
     *
     * @param tenantId Tenant ID
     * @param locacaoId Locacao ID
     * @param vendedorId Novo vendedor; null desassocia (locação sem vendedor)
     * @return Locacao atualizada
     */
    @Transactional
    public Locacao alterarVendedor(UUID tenantId, UUID locacaoId, UUID vendedorId) {
        Locacao locacao = locacaoRepository.findByIdAndTenantId(locacaoId, tenantId)
            .orElseThrow(() -> new NotFoundException("Locação não encontrada: " + locacaoId));

        if (locacao.getStatus() != LocacaoStatus.EM_CURSO) {
            throw new BusinessException(
                String.format("Só é possível alterar o vendedor de locações em curso " +
                              "(status atual: %s); para locação FINALIZADA use editar-finalizada",
                              locacao.getStatus())
            );
        }

        // Vendedor deve existir no tenant (filtro explícito, nunca só RLS)
        if (vendedorId != null) {
            vendedorRepository.findById(vendedorId)
                .filter(v -> tenantId.equals(v.getTenantId()))
                .orElseThrow(() -> new BusinessException("Vendedor não encontrado: " + vendedorId));
        }

        UUID vendedorAnterior = locacao.getVendedorId();
        locacao.setVendedorId(vendedorId);
        locacao = locacaoRepository.save(locacao);

        log.info("Vendedor da locação alterado: locacao={}, de {} para {}, operador={}",
                 locacaoId, vendedorAnterior, vendedorId, TenantContext.getUsuarioId());

        return locacao;
    }

    /**
     * Edit a finalized rental (FINALIZADA status).
     *
     * <p>Business Rules:
     * <ul>
     *   <li>Locação must be in FINALIZADA status</li>
     *   <li>FechamentoDiario for the rental's checkout date must NOT be bloqueado</li>
     *   <li>Only GERENTE or ADMIN_TENANT can perform this action (checked via ABAC)</li>
     *   <li>All changes are logged to audit trail</li>
     * </ul>
     *
     * @param tenantId      Tenant ID
     * @param locacaoId     Locacao ID
     * @param request       Fields to update (only non-null fields are applied)
     * @param recalcular    If true, recalculate derived values (minutosFaturaveis, valorBase, valorTotal)
     *                      If false, use values provided in request as-is (manual override)
     * @return Updated Locacao
     * @throws NotFoundException if locacao not found
     * @throws BusinessException if validation fails
     *
     * @since 0.11.0
     */
    @Transactional
    public Locacao editLocacaoFinalizada(UUID tenantId, UUID locacaoId,
                                          EditFinalizadaLocacaoRequest request,
                                          boolean recalcular) {
        log.info("Editing finalized locacao: tenant={}, locacao={}, recalcular={}",
                 tenantId, locacaoId, recalcular);

        // 1. Find and validate locacao
        Locacao locacao = locacaoRepository.findByIdAndTenantId(locacaoId, tenantId)
            .orElseThrow(() -> new NotFoundException("Locação não encontrada: " + locacaoId));

        // 2. Validate status - must be FINALIZADA
        if (!locacao.isFinalizada()) {
            throw new BusinessException(
                String.format("Apenas locações FINALIZADA podem ser editadas por este endpoint (status atual: %s)",
                              locacao.getStatus())
            );
        }

        // 3. Determine reference date for fechamento check
        LocalDate dataReferencia = determineReferenceDate(locacao, request);

        // 4. Check if FechamentoDiario for reference date is bloqueado
        if (fechamentoLockChecker.isDataBloqueada(tenantId, dataReferencia)) {
            throw new BusinessException(
                String.format("Não é possível editar locação: fechamento do dia %s está bloqueado",
                              dataReferencia)
            );
        }

        // 5. Capture previous state for audit and commission recalculation
        Map<String, Object> dadosAnteriores = captureLocacaoState(locacao);
        UUID vendedorIdAntigo = locacao.getVendedorId();

        // 6. Apply updates
        applyEdits(locacao, request);

        // 7. Recalculate derived values if requested
        if (recalcular) {
            recalculateValues(locacao, request);
        }

        // 8. Save
        locacao = locacaoRepository.save(locacao);

        // 8.1 Folio: cobranças são DERIVADAS — relança para refletir os novos
        // valores (PAGAMENTO/ESTORNO são fatos de caixa e nunca são tocados).
        relancarCobrancas(locacao);

        // 9. Capture new state for audit
        Map<String, Object> dadosNovos = captureLocacaoState(locacao);

        // 10. Publish audit event
        UUID operadorId = TenantContext.getUsuarioId();
        eventPublisher.publishEvent(LocacaoEditadaEvent.of(
            tenantId, locacaoId, operadorId,
            dadosAnteriores, dadosNovos,
            request.getMotivoEdicao()
        ));

        // 11. Recalculate commission if vendedor or values changed
        UUID vendedorIdNovo = locacao.getVendedorId();
        boolean vendedorMudou = (vendedorIdAntigo == null && vendedorIdNovo != null) ||
                                (vendedorIdAntigo != null && !vendedorIdAntigo.equals(vendedorIdNovo));
        boolean valoresMudaram = request.getValorTotal() != null || request.getCombustivelCusto() != null;

        if (vendedorMudou || (valoresMudaram && vendedorIdNovo != null)) {
            try {
                // Get modelo from jetski
                Jetski jetski = jetskiService.findById(locacao.getJetskiId());
                Modelo modelo = modeloService.findById(jetski.getModeloId());

                commissionService.recalcularComissaoLocacao(
                    tenantId,
                    locacaoId,
                    vendedorIdAntigo,
                    vendedorIdNovo,
                    modelo.getId(),
                    locacao.getMinutosFaturaveis() != null ? locacao.getMinutosFaturaveis() : 0,
                    locacao.getValorTotal(),
                    locacao.getCombustivelCusto(),
                    locacao.getValorBase()  // valorBase para determinar comissão diferenciada
                );
                log.info("Commission recalculated for edited locacao: {}", locacaoId);
            } catch (Exception e) {
                // Log error but don't fail the edit - commission can be fixed manually
                log.warn("Failed to recalculate commission for locacao {}: {}", locacaoId, e.getMessage());
            }
        }

        log.info("Finalized locacao edited successfully: locacao={}, operator={}, motivo={}",
                 locacaoId, operadorId, request.getMotivoEdicao());

        return locacao;
    }

    /**
     * Determine the reference date for fechamento validation.
     * Uses checkout date from request if provided, otherwise uses existing checkout date.
     */
    private LocalDate determineReferenceDate(Locacao locacao, EditFinalizadaLocacaoRequest request) {
        if (request.getDataCheckOut() != null) {
            return request.getDataCheckOut().toLocalDate();
        }
        return locacao.getDataCheckOut().toLocalDate();
    }

    /**
     * Capture current locacao state for audit trail.
     */
    private Map<String, Object> captureLocacaoState(Locacao locacao) {
        Map<String, Object> state = new HashMap<>();
        state.put("dataCheckIn", locacao.getDataCheckIn());
        state.put("dataCheckOut", locacao.getDataCheckOut());
        state.put("horimetroInicio", locacao.getHorimetroInicio());
        state.put("horimetroFim", locacao.getHorimetroFim());
        state.put("minutosUsados", locacao.getMinutosUsados());
        state.put("minutosFaturaveis", locacao.getMinutosFaturaveis());
        state.put("valorBase", locacao.getValorBase());
        state.put("valorNegociado", locacao.getValorNegociado());
        state.put("valorTotal", locacao.getValorTotal());
        state.put("combustivelCusto", locacao.getCombustivelCusto());
        state.put("motivoDesconto", locacao.getMotivoDesconto());
        state.put("observacoes", locacao.getObservacoes());
        state.put("vendedorId", locacao.getVendedorId());
        return state;
    }

    /**
     * Apply edits from request to locacao entity.
     * Only non-null values are applied.
     */
    private void applyEdits(Locacao locacao, EditFinalizadaLocacaoRequest request) {
        if (request.getDataCheckIn() != null) {
            locacao.setDataCheckIn(request.getDataCheckIn());
        }
        if (request.getDataCheckOut() != null) {
            locacao.setDataCheckOut(request.getDataCheckOut());
        }
        if (request.getHorimetroInicio() != null) {
            locacao.setHorimetroInicio(request.getHorimetroInicio());
        }
        if (request.getHorimetroFim() != null) {
            locacao.setHorimetroFim(request.getHorimetroFim());
        }
        if (request.getMinutosUsados() != null) {
            locacao.setMinutosUsados(request.getMinutosUsados());
        }
        if (request.getMinutosFaturaveis() != null) {
            locacao.setMinutosFaturaveis(request.getMinutosFaturaveis());
        }
        if (request.getValorBase() != null) {
            locacao.setValorBase(request.getValorBase());
        }
        if (request.getValorNegociado() != null) {
            locacao.setValorNegociado(request.getValorNegociado());
        }
        if (request.getValorTotal() != null) {
            locacao.setValorTotal(request.getValorTotal());
        }
        if (request.getCombustivelCusto() != null) {
            locacao.setCombustivelCusto(request.getCombustivelCusto());
        }
        if (request.getMotivoDesconto() != null) {
            locacao.setMotivoDesconto(request.getMotivoDesconto());
        }
        if (request.getVendedorId() != null) {
            locacao.setVendedorId(request.getVendedorId());
        }
        if (request.getObservacoes() != null) {
            // Append to existing observations with edit marker
            String currentObs = locacao.getObservacoes();
            String editNote = String.format("[EDIÇÃO] %s", request.getObservacoes());
            locacao.setObservacoes(currentObs == null ? editNote : currentObs + "\n" + editNote);
        }
    }

    /**
     * Recalculate derived values based on updated base values.
     * Only recalculates values that were NOT explicitly provided in the request.
     *
     * @param locacao The locacao entity with applied edits
     * @param request The original request (used to check which values were explicitly provided)
     */
    private void recalculateValues(Locacao locacao, EditFinalizadaLocacaoRequest request) {
        // Recalculate minutosUsados from check-in/check-out (only if NOT provided by user)
        if (request.getMinutosUsados() == null &&
            locacao.getDataCheckIn() != null && locacao.getDataCheckOut() != null) {
            int minutosUsados = calculatorService.calculateUsedMinutes(
                locacao.getDataCheckIn(),
                locacao.getDataCheckOut()
            );
            locacao.setMinutosUsados(minutosUsados);
        }

        // Recalculate minutosFaturaveis with tolerance (only if NOT provided by user)
        if (locacao.getMinutosUsados() != null && locacao.getJetskiId() != null) {
            Jetski jetski = jetskiService.findById(locacao.getJetskiId());
            Modelo modelo = modeloService.findById(jetski.getModeloId());
            int toleranciaMinutos = modelo.getToleranciaMin() != null ? modelo.getToleranciaMin() : 0;

            // Only recalculate minutosFaturaveis if NOT provided by user
            if (request.getMinutosFaturaveis() == null) {
                int minutosFaturaveis = calculatorService.calculateBillableMinutes(
                    locacao.getMinutosUsados(),
                    toleranciaMinutos
                );
                locacao.setMinutosFaturaveis(minutosFaturaveis);
            }

            // Recalculate valorBase (only if NOT provided by user AND no valorNegociado)
            if (request.getValorBase() == null) {
                if (locacao.getValorNegociado() == null) {
                    BigDecimal valorBase = calculatorService.calculateBaseValue(
                        locacao.getMinutosFaturaveis(),
                        modelo.getPrecoBaseHora()
                    );
                    locacao.setValorBase(valorBase);
                } else {
                    locacao.setValorBase(locacao.getValorNegociado());
                }
            }
            // If user provided valorBase, it's already set by applyEdits() - respect it!
        }

        // Recalculate valorTotal = valorBase + combustivelCusto + itensOpcionais (only if NOT provided by user)
        if (request.getValorTotal() == null && locacao.getValorBase() != null) {
            BigDecimal valorTotal = locacao.getValorBase();
            if (locacao.getCombustivelCusto() != null) {
                valorTotal = valorTotal.add(locacao.getCombustivelCusto());
            }
            BigDecimal valorItensOpcionais = locacaoItemOpcionalRepository
                .sumValorCobradoByLocacaoId(locacao.getId());
            if (valorItensOpcionais != null) {
                valorTotal = valorTotal.add(valorItensOpcionais);
            }
            locacao.setValorTotal(valorTotal);
        }
        // If user provided valorTotal, it's already set by applyEdits() - respect it!
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

    // ======================= Folio (fase 3) =======================

    private static final List<ReservaLancamento.Tipo> TIPOS_COBRANCA = List.of(
        ReservaLancamento.Tipo.COBRANCA_ALUGUEL,
        ReservaLancamento.Tipo.COBRANCA_COMBUSTIVEL,
        ReservaLancamento.Tipo.COBRANCA_EXTRAS);

    /**
     * Lança as cobranças apuradas no check-out como lançamentos DERIVADOS
     * (sem forma), ancorados na locação e — quando houver — na reserva.
     */
    private void lancarCobrancas(Locacao locacao, BigDecimal aluguel,
                                 BigDecimal combustivel, BigDecimal extras) {
        UUID operadorId = TenantContext.getUsuarioId();
        cobranca(locacao, ReservaLancamento.Tipo.COBRANCA_ALUGUEL, aluguel, operadorId);
        cobranca(locacao, ReservaLancamento.Tipo.COBRANCA_COMBUSTIVEL, combustivel, operadorId);
        cobranca(locacao, ReservaLancamento.Tipo.COBRANCA_EXTRAS, extras, operadorId);
    }

    private void cobranca(Locacao locacao, ReservaLancamento.Tipo tipo,
                          BigDecimal valor, UUID operadorId) {
        if (valor == null || valor.signum() <= 0) {
            return; // CHECK valor > 0 — cobrança zerada não vira lançamento
        }
        reservaLancamentoRepository.save(ReservaLancamento.builder()
            .tenantId(locacao.getTenantId())
            .locacaoId(locacao.getId())
            .reservaId(locacao.getReservaId())
            .tipo(tipo)
            .valor(valor)
            .registradoPor(operadorId)
            .build());
    }

    /**
     * Relança as cobranças derivadas após edição de locação FINALIZADA — os
     * valores mudaram e o extrato/saldo devem refletir o estado atual.
     * PAGAMENTO/ESTORNO são fatos de caixa e nunca são tocados.
     */
    private void relancarCobrancas(Locacao locacao) {
        reservaLancamentoRepository.deleteByLocacaoIdAndTipoIn(locacao.getId(), TIPOS_COBRANCA);
        BigDecimal extras = locacaoItemOpcionalRepository.sumValorCobradoByLocacaoId(locacao.getId());
        BigDecimal aluguel = locacao.getValorNegociado() != null
            ? locacao.getValorNegociado()
            : locacao.getValorBase();
        lancarCobrancas(locacao, aluguel, locacao.getCombustivelCusto(), extras);
    }

    /**
     * Registra um recebimento da locação (acerto do check-out / walk-in):
     * fato de caixa com forma obrigatória, ancorado na locação e na reserva
     * (quando houver). Complementa o pagamento antecipado da reserva.
     */
    @Transactional
    public Locacao registrarPagamento(UUID tenantId, UUID locacaoId,
                                      ReservaLancamento.Forma forma,
                                      BigDecimal valor, String observacao) {
        if (forma == null) {
            throw new BusinessException("Forma de pagamento é obrigatória");
        }
        if (valor == null || valor.signum() <= 0) {
            throw new BusinessException("Valor do pagamento deve ser maior que zero");
        }

        Locacao locacao = locacaoRepository.findByIdAndTenantId(locacaoId, tenantId)
            .orElseThrow(() -> new NotFoundException("Locação não encontrada: " + locacaoId));
        if (!locacao.isFinalizada()) {
            throw new BusinessException(
                "Recebimento é registrado no check-out — a locação ainda está em curso");
        }

        UUID usuarioId = TenantContext.getUsuarioId();
        reservaLancamentoRepository.save(ReservaLancamento.builder()
            .tenantId(locacao.getTenantId())
            .locacaoId(locacao.getId())
            .reservaId(locacao.getReservaId())
            .tipo(ReservaLancamento.Tipo.PAGAMENTO)
            .forma(forma)
            .valor(valor)
            .observacao(observacao)
            .registradoPor(usuarioId)
            .build());

        eventPublisher.publishEvent(PagamentoLocacaoRegistradoEvent.of(
            locacao.getTenantId(), locacao.getId(), locacao.getReservaId(),
            forma.name(), valor, observacao, usuarioId));

        log.info("Pagamento de locação registrado: locacao={}, forma={}, valor={}",
            locacaoId, forma, valor);
        return locacao;
    }

    /**
     * Extrato do folio da locação: lançamentos da locação + os da reserva de
     * origem (pagamento antecipado do balcão/portal), deduplicados por id —
     * as cobranças do check-out carregam as duas âncoras.
     */
    @Transactional(readOnly = true)
    public List<ReservaLancamento> extrato(UUID tenantId, UUID locacaoId) {
        Locacao locacao = locacaoRepository.findByIdAndTenantId(locacaoId, tenantId)
            .orElseThrow(() -> new NotFoundException("Locação não encontrada: " + locacaoId));

        Map<UUID, ReservaLancamento> porId = new java.util.LinkedHashMap<>();
        for (ReservaLancamento l : reservaLancamentoRepository.findByLocacaoIdOrderByCreatedAtAsc(locacaoId)) {
            porId.put(l.getId(), l);
        }
        if (locacao.getReservaId() != null) {
            for (ReservaLancamento l : reservaLancamentoRepository
                    .findByReservaIdOrderByCreatedAtAsc(locacao.getReservaId())) {
                porId.putIfAbsent(l.getId(), l);
            }
        }
        return porId.values().stream()
            .sorted(java.util.Comparator.comparing(ReservaLancamento::getCreatedAt))
            .toList();
    }

    /** Limite mensal de locações do plano (locacoes_mes) — v2 item 2. */
    private void verificarLimiteLocacoesDoMes(UUID tenantId) {
        LocalDateTime inicioMes = java.time.LocalDate.now(
                java.time.ZoneId.of("America/Sao_Paulo"))
            .withDayOfMonth(1).atStartOfDay();
        long usadas = locacaoRepository.countByTenantIdAndDataCheckInBetween(
            tenantId, inicioMes, inicioMes.plusMonths(1));
        planoLimiteService.verificar(tenantId, "locacoes_mes", usadas, "locações no mês");
    }
}
