package com.jetski.fechamento.internal;

import com.jetski.comissoes.api.ComissaoQueryService;
import com.jetski.comissoes.domain.Comissao;
import com.jetski.fechamento.domain.FechamentoDiario;
import com.jetski.fechamento.domain.FechamentoMensal;
import com.jetski.fechamento.internal.repository.FechamentoDiarioRepository;
import com.jetski.fechamento.internal.repository.FechamentoMensalRepository;
import com.jetski.locacoes.api.LocacaoQueryService;
import com.jetski.locacoes.domain.Locacao;
import com.jetski.locacoes.domain.LocacaoStatus;
import com.jetski.shared.exception.BusinessException;
import com.jetski.shared.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for FechamentoService
 *
 * Tests daily and monthly closure logic:
 * - Consolidation of rentals
 * - Status transitions (aberto → fechado → aprovado)
 * - Blocking logic (bloqueado prevents edits)
 * - Reopening rules (only non-approved closures)
 *
 * @author Jetski Team
 * @since 0.8.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FechamentoService - Financial Closure Logic")
class FechamentoServiceTest {

    @Mock
    private FechamentoDiarioRepository fechamentoDiarioRepository;

    @Mock
    private FechamentoMensalRepository fechamentoMensalRepository;

    @Mock
    private LocacaoQueryService locacaoQueryService;

    @Mock
    private ComissaoQueryService comissaoQueryService;

    @InjectMocks
    private FechamentoService fechamentoService;

    private UUID tenantId;
    private UUID operadorId;
    private LocalDate dataReferencia;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        operadorId = UUID.randomUUID();
        dataReferencia = LocalDate.of(2025, 10, 29);
    }

    // ====================
    // Fechamento Diário - Consolidation
    // ====================

    @Test
    @DisplayName("Should consolidate daily rentals successfully")
    void shouldConsolidateDailyRentals() {
        // Given: 3 finalized rentals on the same day
        Locacao locacao1 = createLocacao(new BigDecimal("300.00"), dataReferencia);
        Locacao locacao2 = createLocacao(new BigDecimal("450.00"), dataReferencia);
        Locacao locacao3 = createLocacao(new BigDecimal("250.00"), dataReferencia);

        when(locacaoQueryService.findByTenantIdAndDateRange(any(), any(), any()))
                .thenReturn(Arrays.asList(locacao1, locacao2, locacao3));
        when(comissaoQueryService.findByPeriodo(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(fechamentoDiarioRepository.findByTenantIdAndDtReferencia(tenantId, dataReferencia))
                .thenReturn(Optional.empty());
        when(fechamentoDiarioRepository.save(any(FechamentoDiario.class))).thenAnswer(i -> i.getArgument(0));

        // When
        FechamentoDiario fechamento = fechamentoService.consolidarDia(tenantId, dataReferencia, operadorId);

        // Then
        assertThat(fechamento).isNotNull();
        assertThat(fechamento.getTotalLocacoes()).isEqualTo(3);
        assertThat(fechamento.getTotalFaturado()).isEqualByComparingTo(new BigDecimal("1000.00")); // 300 + 450 + 250
        assertThat(fechamento.getStatus()).isEqualTo("aberto");
        assertThat(fechamento.getBloqueado()).isFalse();

        verify(fechamentoDiarioRepository).save(any(FechamentoDiario.class));
    }

    @Test
    @DisplayName("Should throw exception when trying to consolidate locked day")
    void shouldThrowExceptionWhenDayIsLocked() {
        // Given: Day already locked
        FechamentoDiario fechamentoExistente = FechamentoDiario.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .dtReferencia(dataReferencia)
                .bloqueado(true)
                .status("fechado")
                .build();

        when(fechamentoDiarioRepository.findByTenantIdAndDtReferencia(tenantId, dataReferencia))
                .thenReturn(Optional.of(fechamentoExistente));

        // When/Then
        assertThatThrownBy(() -> fechamentoService.consolidarDia(tenantId, dataReferencia, operadorId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("já está bloqueado");

        verify(fechamentoDiarioRepository, never()).save(any());
    }

    // ====================
    // Fechamento Diário - Status Transitions
    // ====================

    @Test
    @DisplayName("Should close and lock daily closure")
    void shouldCloseAndLockDailyClosure() {
        // Given: Open closure
        UUID fechamentoId = UUID.randomUUID();
        FechamentoDiario fechamento = FechamentoDiario.builder()
                .id(fechamentoId)
                .tenantId(tenantId)
                .dtReferencia(dataReferencia)
                .status("aberto")
                .bloqueado(false)
                .build();

        when(fechamentoDiarioRepository.findById(fechamentoId)).thenReturn(Optional.of(fechamento));
        when(fechamentoDiarioRepository.save(any(FechamentoDiario.class))).thenAnswer(i -> i.getArgument(0));

        // When
        FechamentoDiario fechado = fechamentoService.fecharDia(tenantId, fechamentoId, operadorId);

        // Then
        assertThat(fechado.getStatus()).isEqualTo("fechado");
        assertThat(fechado.getBloqueado()).isTrue();
        assertThat(fechado.getDtFechamento()).isNotNull();

        verify(fechamentoDiarioRepository).save(fechamento);
    }

    @Test
    @DisplayName("Should approve daily closure")
    void shouldApproveDailyClosure() {
        // Given: Closed closure
        UUID fechamentoId = UUID.randomUUID();
        FechamentoDiario fechamento = FechamentoDiario.builder()
                .id(fechamentoId)
                .tenantId(tenantId)
                .dtReferencia(dataReferencia)
                .status("fechado")
                .bloqueado(true)
                .build();

        when(fechamentoDiarioRepository.findById(fechamentoId)).thenReturn(Optional.of(fechamento));
        when(fechamentoDiarioRepository.save(any(FechamentoDiario.class))).thenAnswer(i -> i.getArgument(0));

        // When
        FechamentoDiario aprovado = fechamentoService.aprovarFechamentoDiario(tenantId, fechamentoId);

        // Then
        assertThat(aprovado.getStatus()).isEqualTo("aprovado");
        verify(fechamentoDiarioRepository).save(fechamento);
    }

    @Test
    @DisplayName("Should reopen daily closure when not approved")
    void shouldReopenDailyClosure() {
        // Given: Closed (but not approved) closure
        UUID fechamentoId = UUID.randomUUID();
        FechamentoDiario fechamento = FechamentoDiario.builder()
                .id(fechamentoId)
                .tenantId(tenantId)
                .dtReferencia(dataReferencia)
                .status("fechado")
                .bloqueado(true)
                .build();

        when(fechamentoDiarioRepository.findById(fechamentoId)).thenReturn(Optional.of(fechamento));
        when(fechamentoDiarioRepository.save(any(FechamentoDiario.class))).thenAnswer(i -> i.getArgument(0));

        // When
        FechamentoDiario reaberto = fechamentoService.reabrirFechamentoDiario(tenantId, fechamentoId);

        // Then
        assertThat(reaberto.getStatus()).isEqualTo("aberto");
        assertThat(reaberto.getBloqueado()).isFalse();
        assertThat(reaberto.getDtFechamento()).isNull();
    }

    @Test
    @DisplayName("Should NOT reopen approved closure")
    void shouldNotReopenApprovedClosure() {
        // Given: Approved closure
        UUID fechamentoId = UUID.randomUUID();
        FechamentoDiario fechamento = FechamentoDiario.builder()
                .id(fechamentoId)
                .tenantId(tenantId)
                .dtReferencia(dataReferencia)
                .status("aprovado")
                .bloqueado(true)
                .build();

        when(fechamentoDiarioRepository.findById(fechamentoId)).thenReturn(Optional.of(fechamento));

        // When/Then
        assertThatThrownBy(() -> fechamentoService.reabrirFechamentoDiario(tenantId, fechamentoId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("aprovado não pode ser reaberto");
    }

    // ====================
    // Fechamento Mensal - Consolidation
    // ====================

    @Test
    @DisplayName("Should consolidate monthly closure from daily closures")
    void shouldConsolidateMonthlyClosure() {
        // Given: 3 daily closures in October 2025
        FechamentoDiario dia1 = createFechamentoDiario(LocalDate.of(2025, 10, 1), 5, new BigDecimal("1000.00"));
        FechamentoDiario dia2 = createFechamentoDiario(LocalDate.of(2025, 10, 2), 8, new BigDecimal("1500.00"));
        FechamentoDiario dia3 = createFechamentoDiario(LocalDate.of(2025, 10, 3), 3, new BigDecimal("500.00"));

        when(fechamentoDiarioRepository.findByTenantIdAndDtReferenciaBetweenOrderByDtReferenciaDesc(any(), any(), any()))
                .thenReturn(Arrays.asList(dia1, dia2, dia3));
        when(fechamentoMensalRepository.findByTenantIdAndAnoAndMes(tenantId, 2025, 10))
                .thenReturn(Optional.empty());
        when(fechamentoMensalRepository.save(any(FechamentoMensal.class))).thenAnswer(i -> i.getArgument(0));

        // When
        FechamentoMensal fechamento = fechamentoService.consolidarMes(tenantId, 2025, 10, operadorId);

        // Then
        assertThat(fechamento).isNotNull();
        assertThat(fechamento.getTotalLocacoes()).isEqualTo(16); // 5 + 8 + 3
        assertThat(fechamento.getTotalFaturado()).isEqualByComparingTo(new BigDecimal("3000.00")); // 1000 + 1500 + 500
        assertThat(fechamento.getStatus()).isEqualTo("aberto");
        assertThat(fechamento.getResultadoLiquido()).isNotNull();

        verify(fechamentoMensalRepository).save(any(FechamentoMensal.class));
    }

    @Test
    @DisplayName("Should close and lock monthly closure")
    void shouldCloseAndLockMonthlyClosure() {
        // Given: Open monthly closure
        UUID fechamentoId = UUID.randomUUID();
        FechamentoMensal fechamento = FechamentoMensal.builder()
                .id(fechamentoId)
                .tenantId(tenantId)
                .ano(2025)
                .mes(10)
                .status("aberto")
                .bloqueado(false)
                .build();

        when(fechamentoMensalRepository.findById(fechamentoId)).thenReturn(Optional.of(fechamento));
        when(fechamentoMensalRepository.save(any(FechamentoMensal.class))).thenAnswer(i -> i.getArgument(0));

        // When
        FechamentoMensal fechado = fechamentoService.fecharMes(tenantId, fechamentoId);

        // Then
        assertThat(fechado.getStatus()).isEqualTo("fechado");
        assertThat(fechado.getBloqueado()).isTrue();
        assertThat(fechado.getDtFechamento()).isNotNull();
    }

    // ====================
    // Error Cases
    // ====================

    @Test
    @DisplayName("Should throw NotFoundException when daily closure not found")
    void shouldThrowNotFoundExceptionWhenDailyClosureNotFound() {
        // Given
        UUID fechamentoId = UUID.randomUUID();
        when(fechamentoDiarioRepository.findById(fechamentoId)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> fechamentoService.buscarFechamentoDiario(tenantId, fechamentoId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Fechamento diário não encontrado");
    }

    @Test
    @DisplayName("Should throw NotFoundException when monthly closure not found")
    void shouldThrowNotFoundExceptionWhenMonthlyClosureNotFound() {
        // Given
        UUID fechamentoId = UUID.randomUUID();
        when(fechamentoMensalRepository.findById(fechamentoId)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> fechamentoService.buscarFechamentoMensal(tenantId, fechamentoId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Fechamento mensal não encontrado");
    }

    // ====================
    // Helper Methods
    // ====================

    private Locacao createLocacao(BigDecimal valorTotal, LocalDate dataCheckOut) {
        return Locacao.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .vendedorId(UUID.randomUUID())
                .jetskiId(UUID.randomUUID())
                .clienteId(UUID.randomUUID())
                .dataCheckIn(dataCheckOut.atStartOfDay())
                .dataCheckOut(dataCheckOut.atTime(18, 0))
                .valorTotal(valorTotal)
                .status(LocacaoStatus.FINALIZADA)
                .build();
    }

    private FechamentoDiario createFechamentoDiario(LocalDate data, int totalLocacoes, BigDecimal totalFaturado) {
        return FechamentoDiario.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .dtReferencia(data)
                .operadorId(operadorId)
                .totalLocacoes(totalLocacoes)
                .totalFaturado(totalFaturado)
                .totalCombustivel(BigDecimal.ZERO)
                .totalComissoes(BigDecimal.ZERO)
                .status("fechado")
                .bloqueado(false)
                .build();
    }
}
