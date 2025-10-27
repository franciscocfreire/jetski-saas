package com.jetski.locacoes.internal;

import com.jetski.locacoes.domain.*;
import com.jetski.locacoes.internal.repository.LocacaoRepository;
import com.jetski.locacoes.internal.repository.ReservaRepository;
import com.jetski.shared.exception.BusinessException;
import com.jetski.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final JetskiService jetskiService;
    private final ModeloService modeloService;
    private final ClienteService clienteService;
    private final LocacaoCalculatorService calculatorService;
    private final com.jetski.shared.storage.PhotoValidationService photoValidationService;

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
     * @return Created Locacao
     */
    @Transactional
    public Locacao checkInFromReservation(UUID tenantId, UUID reservaId, BigDecimal horimetroInicio, String observacoes) {
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
            .build();

        locacao = locacaoRepository.save(locacao);
        log.info("Locacao created: id={}, jetski={}", locacao.getId(), jetski.getId());

        // 5. Validate 4 mandatory check-in photos
        photoValidationService.validateCheckInPhotos(tenantId, locacao.getId());

        // 6. Update jetski status
        jetskiService.updateStatus(jetski.getId(), JetskiStatus.LOCADO);

        // 6. Update reserva
        reserva.setStatus(Reserva.ReservaStatus.FINALIZADA);
        reserva.setLocacaoId(locacao.getId());
        reservaRepository.save(reserva);

        log.info("Check-in completed: locacao={}, reserva={}", locacao.getId(), reservaId);

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
     * @return Created Locacao
     */
    @Transactional
    public Locacao checkInWalkIn(UUID tenantId, UUID jetskiId, UUID clienteId, UUID vendedorId,
                                  BigDecimal horimetroInicio, Integer duracaoPrevista, String observacoes) {
        log.info("Walk-in check-in: tenant={}, jetski={}, cliente={}", tenantId, jetskiId, clienteId);

        // 1. Validate jetski
        Jetski jetski = jetskiService.findById(jetskiId);
        validateJetskiForCheckIn(jetski);

        // 2. Validate cliente exists
        clienteService.findById(clienteId);

        // 3. Create Locacao
        Locacao locacao = Locacao.builder()
            .tenantId(tenantId)
            .reservaId(null)  // Walk-in has no reservation
            .jetskiId(jetskiId)
            .clienteId(clienteId)
            .vendedorId(vendedorId)
            .dataCheckIn(LocalDateTime.now())
            .horimetroInicio(horimetroInicio)
            .duracaoPrevista(duracaoPrevista)
            .status(LocacaoStatus.EM_CURSO)
            .observacoes(observacoes)
            .build();

        locacao = locacaoRepository.save(locacao);
        log.info("Locacao created (walk-in): id={}", locacao.getId());

        // 4. Validate 4 mandatory check-in photos
        photoValidationService.validateCheckInPhotos(tenantId, locacao.getId());

        // 5. Update jetski status
        jetskiService.updateStatus(jetskiId, JetskiStatus.LOCADO);

        log.info("Walk-in check-in completed: locacao={}", locacao.getId());

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
     * @return Updated Locacao with calculated values
     */
    @Transactional
    public Locacao checkOut(UUID tenantId, UUID locacaoId, BigDecimal horimetroFim, String observacoes) {
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

        // 3. Calculate used minutes
        int minutosUsados = calculatorService.calculateUsedMinutes(
            locacao.getHorimetroInicio(),
            horimetroFim
        );

        // 4. Get modelo to retrieve tolerance and price
        Jetski jetski = jetskiService.findById(locacao.getJetskiId());
        Modelo modelo = modeloService.findById(jetski.getModeloId());

        int toleranciaMinutos = modelo.getToleranciaMin() != null ? modelo.getToleranciaMin() : 0;

        // 5. Apply RN01: Calculate billable minutes
        int minutosFaturaveis = calculatorService.calculateBillableMinutes(
            minutosUsados,
            toleranciaMinutos
        );

        // 6. Calculate base value
        BigDecimal valorBase = calculatorService.calculateBaseValue(
            minutosFaturaveis,
            modelo.getPrecoBaseHora()
        );

        // 7. Validate 4 mandatory check-out photos
        photoValidationService.validateCheckOutPhotos(tenantId, locacaoId);

        // 8. Update locacao
        locacao.setDataCheckOut(LocalDateTime.now());
        locacao.setHorimetroFim(horimetroFim);
        locacao.setMinutosUsados(minutosUsados);
        locacao.setMinutosFaturaveis(minutosFaturaveis);
        locacao.setValorBase(valorBase);
        locacao.setValorTotal(valorBase);  // Sprint 2: no fuel/taxes yet
        locacao.setStatus(LocacaoStatus.FINALIZADA);

        if (observacoes != null && !observacoes.isBlank()) {
            String currentObs = locacao.getObservacoes();
            locacao.setObservacoes(currentObs == null ? observacoes : currentObs + "\n" + observacoes);
        }

        locacao = locacaoRepository.save(locacao);

        log.info("Check-out calculated: locacao={}, used={}min, billable={}min, value={}",
                 locacaoId, minutosUsados, minutosFaturaveis, valorBase);

        // 8. Update jetski status
        jetskiService.updateStatus(jetski.getId(), JetskiStatus.DISPONIVEL);

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
