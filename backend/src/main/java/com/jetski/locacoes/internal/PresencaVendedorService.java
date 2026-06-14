package com.jetski.locacoes.internal;

import com.jetski.fechamento.domain.FechamentoDiario;
import com.jetski.fechamento.internal.repository.FechamentoDiarioRepository;
import com.jetski.locacoes.api.dto.*;
import com.jetski.locacoes.domain.PresencaVendedor;
import com.jetski.locacoes.domain.TipoPresenca;
import com.jetski.locacoes.domain.Vendedor;
import com.jetski.locacoes.internal.repository.PresencaVendedorRepository;
import com.jetski.locacoes.internal.repository.VendedorRepository;
import com.jetski.shared.exception.BusinessException;
import com.jetski.shared.security.TenantContext;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Service: PresencaVendedor Management
 *
 * Handles seller daily attendance (diária):
 * - Register daily attendance (batch)
 * - Calculate attendance values (full/half day)
 * - Manual value adjustments
 * - Summary for daily closing
 *
 * Business Rules:
 * - Attendance can only be registered for OPEN closings
 * - Manual adjustments require justification
 * - AUSENTE type does not create records
 * - Total attendance is included in daily closing
 *
 * @author Jetski Team
 * @since 0.10.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PresencaVendedorService {

    private final PresencaVendedorRepository presencaRepository;
    private final VendedorRepository vendedorRepository;
    private final FechamentoDiarioRepository fechamentoRepository;
    private final EntityManager entityManager;

    /**
     * Register daily attendance for multiple sellers (batch).
     *
     * @param tenantId Tenant UUID
     * @param request Request with date and attendance list
     * @param registradoPor User who is registering
     * @return Summary response with totals and details
     */
    @Transactional
    public ResumoDiariasResponse registrarPresencas(UUID tenantId, RegistrarPresencasRequest request, UUID registradoPor) {
        log.info("Registering attendance for date: {}, sellers: {}", request.getDtReferencia(), request.getPresencas().size());

        LocalDate dtReferencia = request.getDtReferencia();

        // Validate: closing must be OPEN or not exist yet
        validateFechamentoAberto(tenantId, dtReferencia);

        // Delete existing records for the date (re-registration)
        presencaRepository.deleteAllByDtReferencia(dtReferencia);
        // Force flush to execute DELETE before INSERTs (avoids unique constraint violation)
        entityManager.flush();
        entityManager.clear();

        List<PresencaVendedor> savedRecords = new ArrayList<>();

        for (PresencaVendedorRequest presencaReq : request.getPresencas()) {
            // Skip AUSENTE type - don't create records for absent sellers
            if (presencaReq.getTipo() == null) {
                continue;
            }

            // Validate seller exists
            Vendedor vendedor = vendedorRepository.findById(presencaReq.getVendedorId())
                    .orElseThrow(() -> new BusinessException("Vendedor não encontrado: " + presencaReq.getVendedorId()));

            // Validate adjustment reason if value is adjusted
            if (presencaReq.getValorAjustado() != null &&
                (presencaReq.getMotivoAjuste() == null || presencaReq.getMotivoAjuste().isBlank())) {
                throw new BusinessException("Motivo do ajuste é obrigatório quando há valor ajustado para vendedor: " + vendedor.getNome());
            }

            // Calculate base value
            BigDecimal valorDiaria = calculateValorDiaria(vendedor.getDiariaBase(), presencaReq.getTipo());

            // Create attendance record
            PresencaVendedor presenca = PresencaVendedor.builder()
                    .tenantId(tenantId)
                    .vendedor(vendedor)
                    .dtReferencia(dtReferencia)
                    .tipo(presencaReq.getTipo())
                    .valorDiaria(valorDiaria)
                    .valorAjustado(presencaReq.getValorAjustado())
                    .motivoAjuste(presencaReq.getMotivoAjuste())
                    .registradoPor(registradoPor)
                    .build();

            savedRecords.add(presencaRepository.save(presenca));
        }

        log.info("Registered {} attendance records for date: {}", savedRecords.size(), dtReferencia);

        // Update daily closing total if it exists
        updateFechamentoDiario(tenantId, dtReferencia);

        // Build and return summary
        return buildResumoDiarias(tenantId, dtReferencia, savedRecords);
    }

    /**
     * Get attendance summary for a specific date.
     *
     * @param tenantId Tenant UUID
     * @param dtReferencia Reference date
     * @return Summary response with totals and details
     */
    @Transactional(readOnly = true)
    public ResumoDiariasResponse getResumoDiarias(UUID tenantId, LocalDate dtReferencia) {
        log.debug("Getting attendance summary for date: {}", dtReferencia);

        List<PresencaVendedor> presencas = presencaRepository.findAllByDtReferencia(dtReferencia);
        return buildResumoDiarias(tenantId, dtReferencia, presencas);
    }

    /**
     * Get all active sellers with their daily rate for attendance registration.
     *
     * @return List of active sellers with daily rate
     */
    @Transactional(readOnly = true)
    public List<VendedorResponse> getVendedoresParaPresenca() {
        log.debug("Getting active sellers for attendance registration");

        List<Vendedor> vendedores = vendedorRepository.findAllActive();

        return vendedores.stream()
                .map(this::mapToVendedorResponse)
                .toList();
    }

    /**
     * Get attendance records for a seller in a date range.
     *
     * @param vendedorId Seller UUID
     * @param dtInicio Start date
     * @param dtFim End date
     * @return List of attendance responses
     */
    @Transactional(readOnly = true)
    public List<PresencaVendedorResponse> getPresencasByVendedor(UUID vendedorId, LocalDate dtInicio, LocalDate dtFim) {
        log.debug("Getting attendance for seller: {} from {} to {}", vendedorId, dtInicio, dtFim);

        List<PresencaVendedor> presencas = presencaRepository.findByVendedorIdAndPeriodo(vendedorId, dtInicio, dtFim);

        return presencas.stream()
                .map(this::mapToPresencaResponse)
                .toList();
    }

    /**
     * Get total attendance value for a date.
     *
     * @param tenantId Tenant UUID
     * @param dtReferencia Reference date
     * @return Total value
     */
    @Transactional(readOnly = true)
    public BigDecimal getTotalDiarias(UUID tenantId, LocalDate dtReferencia) {
        return presencaRepository.sumTotalDiariasByDate(tenantId, dtReferencia);
    }

    /**
     * Update a single attendance record.
     *
     * @param presencaId Attendance UUID
     * @param request Update request
     * @return Updated attendance response
     */
    @Transactional
    public PresencaVendedorResponse updatePresenca(UUID presencaId, PresencaVendedorRequest request) {
        log.info("Updating attendance: {}", presencaId);

        PresencaVendedor presenca = presencaRepository.findById(presencaId)
                .orElseThrow(() -> new BusinessException("Presença não encontrada"));

        // Validate closing is still open
        validateFechamentoAberto(presenca.getTenantId(), presenca.getDtReferencia());

        // Update fields
        if (request.getTipo() != null) {
            presenca.setTipo(request.getTipo());
            // Recalculate base value
            Vendedor vendedor = vendedorRepository.findById(presenca.getVendedorId())
                    .orElseThrow(() -> new BusinessException("Vendedor não encontrado"));
            presenca.setValorDiaria(calculateValorDiaria(vendedor.getDiariaBase(), request.getTipo()));
        }

        if (request.getValorAjustado() != null) {
            if (request.getMotivoAjuste() == null || request.getMotivoAjuste().isBlank()) {
                throw new BusinessException("Motivo do ajuste é obrigatório quando há valor ajustado");
            }
            presenca.setValorAjustado(request.getValorAjustado());
            presenca.setMotivoAjuste(request.getMotivoAjuste());
        }

        PresencaVendedor saved = presencaRepository.save(presenca);

        // Update daily closing total
        updateFechamentoDiario(presenca.getTenantId(), presenca.getDtReferencia());

        return mapToPresencaResponse(saved);
    }

    /**
     * Delete an attendance record.
     *
     * @param presencaId Attendance UUID
     */
    @Transactional
    public void deletePresenca(UUID presencaId) {
        log.info("Deleting attendance: {}", presencaId);

        PresencaVendedor presenca = presencaRepository.findById(presencaId)
                .orElseThrow(() -> new BusinessException("Presença não encontrada"));

        // Validate closing is still open
        validateFechamentoAberto(presenca.getTenantId(), presenca.getDtReferencia());

        presencaRepository.delete(presenca);

        // Update daily closing total
        updateFechamentoDiario(presenca.getTenantId(), presenca.getDtReferencia());
    }

    // ========== Private Helper Methods ==========

    /**
     * Calculate daily value based on base rate and attendance type.
     */
    private BigDecimal calculateValorDiaria(BigDecimal diariaBase, TipoPresenca tipo) {
        if (diariaBase == null || tipo == null) {
            return BigDecimal.ZERO;
        }
        return diariaBase.multiply(BigDecimal.valueOf(tipo.getFator()));
    }

    /**
     * Validate that daily closing is open or doesn't exist yet.
     */
    private void validateFechamentoAberto(UUID tenantId, LocalDate dtReferencia) {
        fechamentoRepository.findByTenantIdAndDtReferencia(tenantId, dtReferencia)
                .ifPresent(fechamento -> {
                    if (Boolean.TRUE.equals(fechamento.getBloqueado())) {
                        throw new BusinessException("Fechamento bloqueado para edição: " + dtReferencia);
                    }
                    if ("FECHADO".equals(fechamento.getStatus())) {
                        throw new BusinessException("Fechamento já está fechado: " + dtReferencia);
                    }
                });
    }

    /**
     * Update daily closing total attendance value.
     */
    private void updateFechamentoDiario(UUID tenantId, LocalDate dtReferencia) {
        fechamentoRepository.findByTenantIdAndDtReferencia(tenantId, dtReferencia)
                .ifPresent(fechamento -> {
                    BigDecimal totalDiarias = presencaRepository.sumTotalDiariasByDate(tenantId, dtReferencia);
                    fechamento.setTotalDiariasVendedores(totalDiarias);
                    fechamentoRepository.save(fechamento);
                    log.debug("Updated fechamento total diarias: {}", totalDiarias);
                });
    }

    /**
     * Build summary response from attendance records.
     */
    private ResumoDiariasResponse buildResumoDiarias(UUID tenantId, LocalDate dtReferencia, List<PresencaVendedor> presencas) {
        int totalIntegral = (int) presencas.stream()
                .filter(p -> p.getTipo() == TipoPresenca.INTEGRAL)
                .count();

        int totalMeiaDiaria = (int) presencas.stream()
                .filter(p -> p.getTipo() == TipoPresenca.MEIA_DIARIA)
                .count();

        BigDecimal totalDiarias = presencas.stream()
                .map(PresencaVendedor::getValorEfetivo)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<PresencaVendedorResponse> detalhes = presencas.stream()
                .map(this::mapToPresencaResponse)
                .toList();

        return ResumoDiariasResponse.builder()
                .dtReferencia(dtReferencia)
                .totalVendedoresPresentes(presencas.size())
                .totalIntegral(totalIntegral)
                .totalMeiaDiaria(totalMeiaDiaria)
                .totalDiarias(totalDiarias)
                .detalhes(detalhes)
                .build();
    }

    /**
     * Map PresencaVendedor entity to response DTO.
     */
    private PresencaVendedorResponse mapToPresencaResponse(PresencaVendedor presenca) {
        Vendedor vendedor = presenca.getVendedor();
        if (vendedor == null) {
            vendedor = vendedorRepository.findById(presenca.getVendedorId()).orElse(null);
        }

        return PresencaVendedorResponse.builder()
                .id(presenca.getId())
                .vendedorId(presenca.getVendedorId())
                .vendedorNome(vendedor != null ? vendedor.getNome() : null)
                .dtReferencia(presenca.getDtReferencia())
                .tipo(presenca.getTipo())
                .valorDiariaBase(vendedor != null ? vendedor.getDiariaBase() : null)
                .valorDiariaCalculado(presenca.getValorDiaria())
                .valorAjustado(presenca.getValorAjustado())
                .valorEfetivo(presenca.getValorEfetivo())
                .motivoAjuste(presenca.getMotivoAjuste())
                .createdAt(presenca.getCreatedAt())
                .build();
    }

    /**
     * Map Vendedor entity to response DTO.
     */
    private VendedorResponse mapToVendedorResponse(Vendedor vendedor) {
        return VendedorResponse.builder()
                .id(vendedor.getId())
                .tenantId(vendedor.getTenantId())
                .nome(vendedor.getNome())
                .documento(vendedor.getDocumento())
                .email(vendedor.getEmail())
                .telefone(vendedor.getTelefone())
                .tipo(vendedor.getTipo())
                .diariaBase(vendedor.getDiariaBase())
                .ativo(vendedor.getAtivo())
                .createdAt(vendedor.getCreatedAt())
                .updatedAt(vendedor.getUpdatedAt())
                .build();
    }
}
