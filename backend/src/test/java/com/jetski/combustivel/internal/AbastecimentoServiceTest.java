package com.jetski.combustivel.internal;

import com.jetski.combustivel.domain.Abastecimento;
import com.jetski.combustivel.domain.TipoAbastecimento;
import com.jetski.combustivel.internal.repository.AbastecimentoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AbastecimentoService
 *
 * Tests fuel refill registration and price tracking
 *
 * @author Jetski Team
 * @since 0.8.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AbastecimentoService - Fuel Refill Management")
class AbastecimentoServiceTest {

    @Mock
    private AbastecimentoRepository abastecimentoRepository;

    @Mock
    private FuelPriceDayService fuelPriceDayService;

    @InjectMocks
    private AbastecimentoService service;

    private UUID tenantId;
    private UUID jetskiId;
    private UUID locacaoId;
    private UUID responsavelId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        jetskiId = UUID.randomUUID();
        locacaoId = UUID.randomUUID();
        responsavelId = UUID.randomUUID();
    }

    // ===================================================================
    // Register refill: PRE_LOCACAO type
    // ===================================================================

    @Test
    @DisplayName("PRE_LOCACAO: Should register refill and update daily price")
    void testRegistrar_PreLocacao() {
        // Given: PRE_LOCACAO refill (50L × R$ 7.00 = R$ 350)
        Abastecimento abastecimento = Abastecimento.builder()
            .jetskiId(jetskiId)
            .locacaoId(locacaoId)
            .tipo(TipoAbastecimento.PRE_LOCACAO)
            .litros(new BigDecimal("50"))
            .precoLitro(new BigDecimal("7.00"))
            .custoTotal(new BigDecimal("350"))
            .dataHora(Instant.now())
            .observacoes("Abastecimento antes da locação")
            .build();

        when(abastecimentoRepository.save(any(Abastecimento.class)))
            .thenAnswer(invocation -> {
                Abastecimento saved = invocation.getArgument(0);
                saved.setId(1L);
                return saved;
            });

        // When: Register refill
        Abastecimento saved = service.registrar(tenantId, responsavelId, abastecimento);

        // Then: Should set tenant and responsavel
        assertThat(saved.getTenantId()).isEqualTo(tenantId);
        assertThat(saved.getResponsavelId()).isEqualTo(responsavelId);

        // Then: Should save abastecimento
        verify(abastecimentoRepository).save(argThat(abast ->
            abast.getTenantId().equals(tenantId) &&
            abast.getResponsavelId().equals(responsavelId) &&
            abast.getJetskiId().equals(jetskiId) &&
            abast.getLocacaoId().equals(locacaoId) &&
            abast.getTipo() == TipoAbastecimento.PRE_LOCACAO &&
            abast.getLitros().compareTo(new BigDecimal("50")) == 0 &&
            abast.getPrecoLitro().compareTo(new BigDecimal("7.00")) == 0 &&
            abast.getCustoTotal().compareTo(new BigDecimal("350")) == 0
        ));

        // Then: Should update daily average price
        verify(fuelPriceDayService).atualizarPrecoMedioDia(
            eq(tenantId),
            any(), // data extracted from dataHora
            eq(new BigDecimal("50")),
            eq(new BigDecimal("350"))
        );
    }

    // ===================================================================
    // Register refill: POS_LOCACAO type
    // ===================================================================

    @Test
    @DisplayName("POS_LOCACAO: Should register refill and update daily price")
    void testRegistrar_PosLocacao() {
        // Given: POS_LOCACAO refill (30L × R$ 6.50 = R$ 195)
        Abastecimento abastecimento = Abastecimento.builder()
            .jetskiId(jetskiId)
            .locacaoId(locacaoId)
            .tipo(TipoAbastecimento.POS_LOCACAO)
            .litros(new BigDecimal("30"))
            .precoLitro(new BigDecimal("6.50"))
            .custoTotal(new BigDecimal("195"))
            .dataHora(Instant.now())
            .observacoes("Reabastecimento após devolução")
            .build();

        when(abastecimentoRepository.save(any(Abastecimento.class)))
            .thenAnswer(invocation -> {
                Abastecimento saved = invocation.getArgument(0);
                saved.setId(2L);
                return saved;
            });

        // When: Register refill
        Abastecimento saved = service.registrar(tenantId, responsavelId, abastecimento);

        // Then: Should save with correct type
        assertThat(saved.getTipo()).isEqualTo(TipoAbastecimento.POS_LOCACAO);

        // Then: Should update daily price
        verify(fuelPriceDayService).atualizarPrecoMedioDia(
            eq(tenantId),
            any(),
            eq(new BigDecimal("30")),
            eq(new BigDecimal("195"))
        );
    }

    // ===================================================================
    // Register refill: FROTA type (no locacao_id)
    // ===================================================================

    @Test
    @DisplayName("FROTA: Should register fleet refill without locacao_id")
    void testRegistrar_Frota() {
        // Given: FROTA refill (no rental association)
        Abastecimento abastecimento = Abastecimento.builder()
            .jetskiId(jetskiId)
            .locacaoId(null) // FROTA has no locacao
            .tipo(TipoAbastecimento.FROTA)
            .litros(new BigDecimal("40"))
            .precoLitro(new BigDecimal("7.20"))
            .custoTotal(new BigDecimal("288"))
            .dataHora(Instant.now())
            .observacoes("Abastecimento geral da frota")
            .build();

        when(abastecimentoRepository.save(any(Abastecimento.class)))
            .thenAnswer(invocation -> {
                Abastecimento saved = invocation.getArgument(0);
                saved.setId(3L);
                return saved;
            });

        // When: Register FROTA refill
        Abastecimento saved = service.registrar(tenantId, responsavelId, abastecimento);

        // Then: Should accept null locacao_id
        assertThat(saved.getLocacaoId()).isNull();
        assertThat(saved.getTipo()).isEqualTo(TipoAbastecimento.FROTA);

        // Then: Should still update daily price
        verify(fuelPriceDayService).atualizarPrecoMedioDia(
            eq(tenantId),
            any(),
            eq(new BigDecimal("40")),
            eq(new BigDecimal("288"))
        );
    }

    // ===================================================================
    // Query methods: listarPorLocacao
    // ===================================================================

    @Test
    @DisplayName("Should list refills by locacao_id")
    void testListarPorLocacao() {
        // Given: Multiple refills for the same locacao
        Abastecimento pre = Abastecimento.builder()
            .id(1L)
            .tipo(TipoAbastecimento.PRE_LOCACAO)
            .litros(new BigDecimal("50"))
            .custoTotal(new BigDecimal("350"))
            .build();

        Abastecimento pos = Abastecimento.builder()
            .id(2L)
            .tipo(TipoAbastecimento.POS_LOCACAO)
            .litros(new BigDecimal("30"))
            .custoTotal(new BigDecimal("195"))
            .build();

        when(abastecimentoRepository.findByTenantIdAndLocacaoIdOrderByDataHoraAsc(tenantId, locacaoId))
            .thenReturn(List.of(pre, pos));

        // When: List refills by locacao
        List<Abastecimento> refills = service.listarPorLocacao(tenantId, locacaoId);

        // Then: Should return all refills for the locacao
        assertThat(refills).hasSize(2);
        assertThat(refills.get(0).getTipo()).isEqualTo(TipoAbastecimento.PRE_LOCACAO);
        assertThat(refills.get(1).getTipo()).isEqualTo(TipoAbastecimento.POS_LOCACAO);
    }

    // ===================================================================
    // Query methods: buscarPorId
    // ===================================================================

    @Test
    @DisplayName("Should find refill by ID")
    void testBuscarPorId_Found() {
        // Given: Existing refill
        Abastecimento abastecimento = Abastecimento.builder()
            .id(1L)
            .tenantId(tenantId)
            .tipo(TipoAbastecimento.PRE_LOCACAO)
            .litros(new BigDecimal("50"))
            .build();

        when(abastecimentoRepository.findById(1L))
            .thenReturn(Optional.of(abastecimento));

        // When: Search by ID
        Optional<Abastecimento> found = service.buscarPorId(tenantId, 1L);

        // Then: Should return the refill
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(1L);
        assertThat(found.get().getTenantId()).isEqualTo(tenantId);
    }

    @Test
    @DisplayName("Should return empty when refill belongs to different tenant")
    void testBuscarPorId_DifferentTenant() {
        // Given: Refill exists but belongs to different tenant
        UUID otherTenantId = UUID.randomUUID();
        Abastecimento abastecimento = Abastecimento.builder()
            .id(1L)
            .tenantId(otherTenantId)
            .tipo(TipoAbastecimento.PRE_LOCACAO)
            .build();

        when(abastecimentoRepository.findById(1L))
            .thenReturn(Optional.of(abastecimento));

        // When: Search by ID with different tenant
        Optional<Abastecimento> found = service.buscarPorId(tenantId, 1L);

        // Then: Should return empty (tenant isolation)
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("Should return empty when refill not found")
    void testBuscarPorId_NotFound() {
        // Given: Refill does not exist
        when(abastecimentoRepository.findById(999L))
            .thenReturn(Optional.empty());

        // When: Search by ID
        Optional<Abastecimento> found = service.buscarPorId(tenantId, 999L);

        // Then: Should return empty
        assertThat(found).isEmpty();
    }

    // ===================================================================
    // Query methods: listarPorJetski (without date filters)
    // ===================================================================

    @Test
    @DisplayName("Should list refills by jetski without date filter")
    void testListarPorJetski_SemFiltroData() {
        // Given: Multiple refills for the jetski
        Abastecimento a1 = Abastecimento.builder().id(1L).litros(new BigDecimal("50")).build();
        Abastecimento a2 = Abastecimento.builder().id(2L).litros(new BigDecimal("30")).build();

        when(abastecimentoRepository.findByTenantIdAndJetskiIdOrderByDataHoraDesc(tenantId, jetskiId))
            .thenReturn(List.of(a1, a2));

        // When: List without date filter
        List<Abastecimento> refills = service.listarPorJetski(tenantId, jetskiId, null, null);

        // Then: Should return all refills
        assertThat(refills).hasSize(2);
        verify(abastecimentoRepository).findByTenantIdAndJetskiIdOrderByDataHoraDesc(tenantId, jetskiId);
    }

    @Test
    @DisplayName("Should list refills by jetski with date filter")
    void testListarPorJetski_ComFiltroData() {
        // Given: Date range filter
        java.time.LocalDate dataInicio = java.time.LocalDate.now().minusDays(7);
        java.time.LocalDate dataFim = java.time.LocalDate.now();

        Abastecimento a1 = Abastecimento.builder().id(1L).litros(new BigDecimal("50")).build();

        when(abastecimentoRepository.findByTenantIdAndJetskiIdAndDataHoraBetweenOrderByDataHoraDesc(
            eq(tenantId), eq(jetskiId), any(Instant.class), any(Instant.class)))
            .thenReturn(List.of(a1));

        // When: List with date filter
        List<Abastecimento> refills = service.listarPorJetski(tenantId, jetskiId, dataInicio, dataFim);

        // Then: Should return filtered refills
        assertThat(refills).hasSize(1);
        verify(abastecimentoRepository).findByTenantIdAndJetskiIdAndDataHoraBetweenOrderByDataHoraDesc(
            eq(tenantId), eq(jetskiId), any(Instant.class), any(Instant.class));
    }

    // ===================================================================
    // Query methods: listarTodos (without/with date filters)
    // ===================================================================

    @Test
    @DisplayName("Should list all refills without date filter")
    void testListarTodos_SemFiltroData() {
        // Given: Multiple refills
        Abastecimento a1 = Abastecimento.builder().id(1L).litros(new BigDecimal("50")).build();
        Abastecimento a2 = Abastecimento.builder().id(2L).litros(new BigDecimal("30")).build();

        when(abastecimentoRepository.findByTenantIdOrderByDataHoraDesc(tenantId))
            .thenReturn(List.of(a1, a2));

        // When: List all without date filter
        List<Abastecimento> refills = service.listarTodos(tenantId, null, null);

        // Then: Should return all refills
        assertThat(refills).hasSize(2);
        verify(abastecimentoRepository).findByTenantIdOrderByDataHoraDesc(tenantId);
    }

    @Test
    @DisplayName("Should list all refills with date filter")
    void testListarTodos_ComFiltroData() {
        // Given: Date range filter
        java.time.LocalDate dataInicio = java.time.LocalDate.now().minusDays(7);
        java.time.LocalDate dataFim = java.time.LocalDate.now();

        Abastecimento a1 = Abastecimento.builder().id(1L).litros(new BigDecimal("50")).build();

        when(abastecimentoRepository.findByTenantIdAndDataHoraBetweenOrderByDataHoraDesc(
            eq(tenantId), any(Instant.class), any(Instant.class)))
            .thenReturn(List.of(a1));

        // When: List with date filter
        List<Abastecimento> refills = service.listarTodos(tenantId, dataInicio, dataFim);

        // Then: Should return filtered refills
        assertThat(refills).hasSize(1);
        verify(abastecimentoRepository).findByTenantIdAndDataHoraBetweenOrderByDataHoraDesc(
            eq(tenantId), any(Instant.class), any(Instant.class));
    }

    // ===================================================================
    // Query methods: listarPorTipo (without/with date filters)
    // ===================================================================

    @Test
    @DisplayName("Should list refills by type without date filter")
    void testListarPorTipo_SemFiltroData() {
        // Given: PRE_LOCACAO refills
        Abastecimento a1 = Abastecimento.builder().id(1L).tipo(TipoAbastecimento.PRE_LOCACAO).build();

        when(abastecimentoRepository.findByTenantIdAndTipoOrderByDataHoraDesc(tenantId, TipoAbastecimento.PRE_LOCACAO))
            .thenReturn(List.of(a1));

        // When: List by type without date filter
        List<Abastecimento> refills = service.listarPorTipo(tenantId, TipoAbastecimento.PRE_LOCACAO, null, null);

        // Then: Should return refills of that type
        assertThat(refills).hasSize(1);
        assertThat(refills.get(0).getTipo()).isEqualTo(TipoAbastecimento.PRE_LOCACAO);
        verify(abastecimentoRepository).findByTenantIdAndTipoOrderByDataHoraDesc(tenantId, TipoAbastecimento.PRE_LOCACAO);
    }

    @Test
    @DisplayName("Should list refills by type with date filter")
    void testListarPorTipo_ComFiltroData() {
        // Given: Date range filter and type
        java.time.LocalDate dataInicio = java.time.LocalDate.now().minusDays(7);
        java.time.LocalDate dataFim = java.time.LocalDate.now();

        Abastecimento a1 = Abastecimento.builder().id(1L).tipo(TipoAbastecimento.FROTA).build();

        when(abastecimentoRepository.findByTenantIdAndTipoAndDataHoraBetweenOrderByDataHoraDesc(
            eq(tenantId), eq(TipoAbastecimento.FROTA), any(Instant.class), any(Instant.class)))
            .thenReturn(List.of(a1));

        // When: List by type with date filter
        List<Abastecimento> refills = service.listarPorTipo(tenantId, TipoAbastecimento.FROTA, dataInicio, dataFim);

        // Then: Should return filtered refills
        assertThat(refills).hasSize(1);
        assertThat(refills.get(0).getTipo()).isEqualTo(TipoAbastecimento.FROTA);
        verify(abastecimentoRepository).findByTenantIdAndTipoAndDataHoraBetweenOrderByDataHoraDesc(
            eq(tenantId), eq(TipoAbastecimento.FROTA), any(Instant.class), any(Instant.class));
    }

    // ===================================================================
    // Register refill: Auto-calculate custoTotal when null
    // ===================================================================

    @Test
    @DisplayName("Should auto-calculate custoTotal when null")
    void testRegistrar_AutoCalculateCustoTotal() {
        // Given: Refill without custoTotal (will be auto-calculated)
        Abastecimento abastecimento = Abastecimento.builder()
            .jetskiId(jetskiId)
            .tipo(TipoAbastecimento.FROTA)
            .litros(new BigDecimal("50"))
            .precoLitro(new BigDecimal("7.00"))
            .custoTotal(null) // Will trigger recalculation
            .dataHora(Instant.now())
            .build();

        when(abastecimentoRepository.save(any(Abastecimento.class)))
            .thenAnswer(invocation -> {
                Abastecimento saved = invocation.getArgument(0);
                saved.setId(10L);
                return saved;
            });

        // When: Register refill
        Abastecimento saved = service.registrar(tenantId, responsavelId, abastecimento);

        // Then: custoTotal should be calculated (50L × R$7.00 = R$350)
        assertThat(saved.getCustoTotal()).isEqualByComparingTo(new BigDecimal("350.00"));

        // Then: Should save with calculated cost
        verify(abastecimentoRepository).save(argThat(abast ->
            abast.getCustoTotal() != null &&
            abast.getCustoTotal().compareTo(new BigDecimal("350.00")) == 0
        ));
    }
}
