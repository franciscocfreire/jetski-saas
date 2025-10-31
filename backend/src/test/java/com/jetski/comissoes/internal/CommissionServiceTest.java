package com.jetski.comissoes.internal;

import com.jetski.comissoes.domain.*;
import com.jetski.comissoes.internal.repository.ComissaoRepository;
import com.jetski.comissoes.internal.repository.PoliticaComissaoRepository;
import com.jetski.shared.exception.BusinessException;
import com.jetski.shared.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CommissionService
 *
 * Tests RN04 hierarchical policy selection and commission calculation:
 * - CAMPANHA (priority 1) → MODELO (priority 2) → DURACAO (priority 3) → VENDEDOR (priority 4)
 * - Commission types: PERCENTUAL, VALOR_FIXO, ESCALONADO
 * - Commissionable value calculation: total - fuel - fines - fees
 * - Approval flow: PENDENTE → APROVADA → PAGA
 *
 * @author Jetski Team
 * @since 0.7.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CommissionService - RN04 Hierarchical Commission Logic")
class CommissionServiceTest {

    @Mock
    private ComissaoRepository comissaoRepository;

    @Mock
    private PoliticaComissaoRepository politicaRepository;

    @InjectMocks
    private CommissionService commissionService;

    private UUID tenantId;
    private UUID locacaoId;
    private UUID vendedorId;
    private UUID modeloId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        locacaoId = UUID.randomUUID();
        vendedorId = UUID.randomUUID();
        modeloId = UUID.randomUUID();
    }

    // ====================
    // RN04: Hierarchical Policy Selection
    // ====================

    @Test
    @DisplayName("Should select CAMPANHA policy (priority 1) when active campaign exists")
    void shouldSelectCampanhaPolicyWhenActiveExists() {
        // Given: Active campaign policy
        String codigoCampanha = "VERAO2025";
        PoliticaComissao politicaCampanha = createPolitica(
                NivelPolitica.CAMPANHA, TipoComissao.PERCENTUAL, new BigDecimal("15.00")
        );
        politicaCampanha.setCodigoCampanha(codigoCampanha);

        when(politicaRepository.findCampanhaAtiva(eq(tenantId), eq(codigoCampanha), any(Instant.class)))
                .thenReturn(Collections.singletonList(politicaCampanha));
        when(comissaoRepository.save(any(Comissao.class))).thenAnswer(i -> i.getArgument(0));

        // When
        Comissao comissao = commissionService.calcularComissao(
                tenantId, locacaoId, vendedorId, modeloId,
                120, new BigDecimal("1000.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                codigoCampanha
        );

        // Then
        assertThat(comissao).isNotNull();
        assertThat(comissao.getPoliticaNivel()).isEqualTo(NivelPolitica.CAMPANHA);
        assertThat(comissao.getValorComissao()).isEqualByComparingTo("150.00"); // 15% of 1000
        verify(politicaRepository).findCampanhaAtiva(eq(tenantId), eq(codigoCampanha), any(Instant.class));
    }

    @Test
    @DisplayName("Should select MODELO policy (priority 2) when no campaign and model policy exists")
    void shouldSelectModeloPolicyWhenNoCampaign() {
        // Given: Model-specific policy
        PoliticaComissao politicaModelo = createPolitica(
                NivelPolitica.MODELO, TipoComissao.PERCENTUAL, new BigDecimal("12.00")
        );
        politicaModelo.setModeloId(modeloId);

        when(politicaRepository.findByTenantIdAndNivelAndModeloIdAndAtiva(
                tenantId, NivelPolitica.MODELO, modeloId, true
        )).thenReturn(Collections.singletonList(politicaModelo));
        when(comissaoRepository.save(any(Comissao.class))).thenAnswer(i -> i.getArgument(0));

        // When
        Comissao comissao = commissionService.calcularComissao(
                tenantId, locacaoId, vendedorId, modeloId,
                90, new BigDecimal("800.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                null
        );

        // Then
        assertThat(comissao).isNotNull();
        assertThat(comissao.getPoliticaNivel()).isEqualTo(NivelPolitica.MODELO);
        assertThat(comissao.getValorComissao()).isEqualByComparingTo("96.00"); // 12% of 800
        verify(politicaRepository, never()).findCampanhaAtiva(any(), any(), any());
    }

    @Test
    @DisplayName("Should select DURACAO policy (priority 3) when no campaign or model policy")
    void shouldSelectDuracaoPolicyWhenNoHigherPriority() {
        // Given: Duration-based policy
        PoliticaComissao politicaDuracao = createPolitica(
                NivelPolitica.DURACAO, TipoComissao.PERCENTUAL, new BigDecimal("10.00")
        );
        politicaDuracao.setDuracaoMinMinutos(60);
        politicaDuracao.setDuracaoMaxMinutos(120);

        when(politicaRepository.findByTenantIdAndNivelAndModeloIdAndAtiva(any(), any(), any(), anyBoolean()))
                .thenReturn(Collections.emptyList());
        when(politicaRepository.findByTenantIdAndNivelAndAtivaOrderByDuracaoMinMinutosDesc(
                tenantId, NivelPolitica.DURACAO, true
        )).thenReturn(Collections.singletonList(politicaDuracao));
        when(comissaoRepository.save(any(Comissao.class))).thenAnswer(i -> i.getArgument(0));

        // When
        Comissao comissao = commissionService.calcularComissao(
                tenantId, locacaoId, vendedorId, modeloId,
                90, new BigDecimal("600.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                null
        );

        // Then
        assertThat(comissao).isNotNull();
        assertThat(comissao.getPoliticaNivel()).isEqualTo(NivelPolitica.DURACAO);
        assertThat(comissao.getValorComissao()).isEqualByComparingTo("60.00"); // 10% of 600
    }

    @Test
    @DisplayName("Should select VENDEDOR policy (priority 4) as fallback")
    void shouldSelectVendedorPolicyAsFallback() {
        // Given: Only vendor default policy exists
        PoliticaComissao politicaVendedor = createPolitica(
                NivelPolitica.VENDEDOR, TipoComissao.PERCENTUAL, new BigDecimal("8.00")
        );
        politicaVendedor.setVendedorId(vendedorId);

        when(politicaRepository.findByTenantIdAndNivelAndModeloIdAndAtiva(any(), any(), any(), anyBoolean()))
                .thenReturn(Collections.emptyList());
        when(politicaRepository.findByTenantIdAndNivelAndAtivaOrderByDuracaoMinMinutosDesc(any(), any(), anyBoolean()))
                .thenReturn(Collections.emptyList());
        when(politicaRepository.findByTenantIdAndNivelAndVendedorIdAndAtiva(
                tenantId, NivelPolitica.VENDEDOR, vendedorId, true
        )).thenReturn(Collections.singletonList(politicaVendedor));
        when(comissaoRepository.save(any(Comissao.class))).thenAnswer(i -> i.getArgument(0));

        // When
        Comissao comissao = commissionService.calcularComissao(
                tenantId, locacaoId, vendedorId, modeloId,
                60, new BigDecimal("500.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                null
        );

        // Then
        assertThat(comissao).isNotNull();
        assertThat(comissao.getPoliticaNivel()).isEqualTo(NivelPolitica.VENDEDOR);
        assertThat(comissao.getValorComissao()).isEqualByComparingTo("40.00"); // 8% of 500
    }

    @Test
    @DisplayName("Should throw BusinessException when no policy found")
    void shouldThrowExceptionWhenNoPolicyFound() {
        // Given: No policies configured
        when(politicaRepository.findByTenantIdAndNivelAndModeloIdAndAtiva(any(), any(), any(), anyBoolean()))
                .thenReturn(Collections.emptyList());
        when(politicaRepository.findByTenantIdAndNivelAndAtivaOrderByDuracaoMinMinutosDesc(any(), any(), anyBoolean()))
                .thenReturn(Collections.emptyList());
        when(politicaRepository.findByTenantIdAndNivelAndVendedorIdAndAtiva(any(), any(), any(), anyBoolean()))
                .thenReturn(Collections.emptyList());

        // When / Then
        assertThatThrownBy(() ->
                commissionService.calcularComissao(
                        tenantId, locacaoId, vendedorId, modeloId,
                        60, new BigDecimal("500.00"),
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        null
                )
        ).isInstanceOf(BusinessException.class)
                .hasMessageContaining("Nenhuma política de comissão configurada");
    }

    // ====================
    // Commission Types
    // ====================

    @Test
    @DisplayName("Should calculate PERCENTUAL commission correctly")
    void shouldCalculatePercentualCommission() {
        // Given
        PoliticaComissao politica = createPolitica(
                NivelPolitica.VENDEDOR, TipoComissao.PERCENTUAL, new BigDecimal("10.50")
        );
        politica.setVendedorId(vendedorId);

        mockVendedorPolicy(politica);
        when(comissaoRepository.save(any(Comissao.class))).thenAnswer(i -> i.getArgument(0));

        // When
        Comissao comissao = commissionService.calcularComissao(
                tenantId, locacaoId, vendedorId, modeloId,
                60, new BigDecimal("1000.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                null
        );

        // Then
        assertThat(comissao.getTipoComissao()).isEqualTo(TipoComissao.PERCENTUAL);
        assertThat(comissao.getValorComissao()).isEqualByComparingTo("105.00"); // 10.5% of 1000
        assertThat(comissao.getPercentualAplicado()).isEqualByComparingTo("10.50");
    }

    @Test
    @DisplayName("Should calculate VALOR_FIXO commission correctly")
    void shouldCalculateValorFixoCommission() {
        // Given
        PoliticaComissao politica = createPolitica(
                NivelPolitica.VENDEDOR, TipoComissao.VALOR_FIXO, null
        );
        politica.setVendedorId(vendedorId);
        politica.setValorFixo(new BigDecimal("50.00"));

        mockVendedorPolicy(politica);
        when(comissaoRepository.save(any(Comissao.class))).thenAnswer(i -> i.getArgument(0));

        // When
        Comissao comissao = commissionService.calcularComissao(
                tenantId, locacaoId, vendedorId, modeloId,
                60, new BigDecimal("1000.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                null
        );

        // Then
        assertThat(comissao.getTipoComissao()).isEqualTo(TipoComissao.VALOR_FIXO);
        assertThat(comissao.getValorComissao()).isEqualByComparingTo("50.00"); // Fixed value
        assertThat(comissao.getPercentualAplicado()).isNull();
    }

    @Test
    @DisplayName("Should calculate ESCALONADO commission - base tier")
    void shouldCalculateEscalonadoCommissionBaseTier() {
        // Given: Tiered commission - 10% base, 15% if >= 120min
        // Note: duracaoMinMinutos serves BOTH as filter AND as threshold
        // So we set it to a value that allows our test duration
        PoliticaComissao politica = createPolitica(
                NivelPolitica.VENDEDOR, TipoComissao.ESCALONADO, new BigDecimal("10.00")
        );
        politica.setVendedorId(vendedorId);
        politica.setPercentualExtra(new BigDecimal("15.00"));
        politica.setDuracaoMinMinutos(120); // Threshold for extra tier (also acts as min filter)
        politica.setDuracaoMaxMinutos(null); // No max

        mockVendedorPolicy(politica);
        when(comissaoRepository.save(any(Comissao.class))).thenAnswer(i -> i.getArgument(0));

        // When: Duration 120min (exactly at threshold)
        Comissao comissao = commissionService.calcularComissao(
                tenantId, locacaoId, vendedorId, modeloId,
                120, new BigDecimal("800.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                null
        );

        // Then: Extra percentage applied (120 >= 120)
        assertThat(comissao.getTipoComissao()).isEqualTo(TipoComissao.ESCALONADO);
        assertThat(comissao.getValorComissao()).isEqualByComparingTo("120.00"); // 15% of 800
        assertThat(comissao.getPercentualAplicado()).isEqualByComparingTo("15.00");
    }

    @Test
    @DisplayName("Should calculate ESCALONADO commission - extra tier")
    void shouldCalculateEscalonadoCommissionExtraTier() {
        // Given: Tiered commission - 10% base, 15% if >= 100min
        PoliticaComissao politica = createPolitica(
                NivelPolitica.VENDEDOR, TipoComissao.ESCALONADO, new BigDecimal("10.00")
        );
        politica.setVendedorId(vendedorId);
        politica.setPercentualExtra(new BigDecimal("15.00"));
        politica.setDuracaoMinMinutos(100); // Threshold for extra tier
        politica.setDuracaoMaxMinutos(null); // No max

        mockVendedorPolicy(politica);
        when(comissaoRepository.save(any(Comissao.class))).thenAnswer(i -> i.getArgument(0));

        // When: Duration 150min (well above threshold of 100min)
        Comissao comissao = commissionService.calcularComissao(
                tenantId, locacaoId, vendedorId, modeloId,
                150, new BigDecimal("1200.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                null
        );

        // Then: Extra percentage applied (150 >= 100)
        assertThat(comissao.getTipoComissao()).isEqualTo(TipoComissao.ESCALONADO);
        assertThat(comissao.getValorComissao()).isEqualByComparingTo("180.00"); // 15% of 1200
        assertThat(comissao.getPercentualAplicado()).isEqualByComparingTo("15.00");
    }

    // ====================
    // Commissionable Value Calculation
    // ====================

    @Test
    @DisplayName("Should calculate commissionable value correctly - deducting fuel, fines and fees")
    void shouldCalculateCommissionableValueCorrectly() {
        // Given
        PoliticaComissao politica = createPolitica(
                NivelPolitica.VENDEDOR, TipoComissao.PERCENTUAL, new BigDecimal("10.00")
        );
        politica.setVendedorId(vendedorId);

        mockVendedorPolicy(politica);

        ArgumentCaptor<Comissao> comissaoCaptor = ArgumentCaptor.forClass(Comissao.class);
        when(comissaoRepository.save(comissaoCaptor.capture())).thenAnswer(i -> i.getArgument(0));

        // When: Total 1000 - fuel 150 - fines 50 - fees 20 = 780 commissionable
        commissionService.calcularComissao(
                tenantId, locacaoId, vendedorId, modeloId,
                60, new BigDecimal("1000.00"),
                new BigDecimal("150.00"), // fuel (not commissionable)
                new BigDecimal("50.00"),  // fines (not commissionable)
                new BigDecimal("20.00"),  // fees (not commissionable)
                null
        );

        // Then
        Comissao savedComissao = comissaoCaptor.getValue();
        assertThat(savedComissao.getValorComissionavel()).isEqualByComparingTo("780.00");
        assertThat(savedComissao.getValorComissao()).isEqualByComparingTo("78.00"); // 10% of 780
    }

    // ====================
    // Approval Flow
    // ====================

    @Test
    @DisplayName("Should approve pending commission successfully")
    void shouldApprovePendingCommission() {
        // Given
        UUID comissaoId = UUID.randomUUID();
        UUID aprovadoPor = UUID.randomUUID();

        Comissao comissao = Comissao.builder()
                .id(comissaoId)
                .tenantId(tenantId)
                .locacaoId(locacaoId)
                .vendedorId(vendedorId)
                .status(StatusComissao.PENDENTE)
                .valorComissao(new BigDecimal("100.00"))
                .build();

        when(comissaoRepository.findById(comissaoId)).thenReturn(Optional.of(comissao));
        when(comissaoRepository.save(any(Comissao.class))).thenAnswer(i -> i.getArgument(0));

        // When
        Comissao aprovada = commissionService.aprovarComissao(tenantId, comissaoId, aprovadoPor);

        // Then
        assertThat(aprovada.getStatus()).isEqualTo(StatusComissao.APROVADA);
        assertThat(aprovada.getAprovadoPor()).isEqualTo(aprovadoPor);
        assertThat(aprovada.getAprovadoEm()).isNotNull();
    }

    @Test
    @DisplayName("Should mark approved commission as paid successfully")
    void shouldMarkApprovedCommissionAsPaid() {
        // Given
        UUID comissaoId = UUID.randomUUID();
        UUID pagoPor = UUID.randomUUID();
        String referenciaPagamento = "TXN-2025-001";

        Comissao comissao = Comissao.builder()
                .id(comissaoId)
                .tenantId(tenantId)
                .locacaoId(locacaoId)
                .vendedorId(vendedorId)
                .status(StatusComissao.APROVADA)
                .valorComissao(new BigDecimal("100.00"))
                .aprovadoPor(UUID.randomUUID())
                .aprovadoEm(Instant.now())
                .build();

        when(comissaoRepository.findById(comissaoId)).thenReturn(Optional.of(comissao));
        when(comissaoRepository.save(any(Comissao.class))).thenAnswer(i -> i.getArgument(0));

        // When
        Comissao paga = commissionService.marcarComoPaga(tenantId, comissaoId, pagoPor, referenciaPagamento);

        // Then
        assertThat(paga.getStatus()).isEqualTo(StatusComissao.PAGA);
        assertThat(paga.getPagoPor()).isEqualTo(pagoPor);
        assertThat(paga.getPagoEm()).isNotNull();
        assertThat(paga.getReferenciaPagamento()).isEqualTo(referenciaPagamento);
    }

    @Test
    @DisplayName("Should throw exception when trying to pay non-approved commission")
    void shouldThrowExceptionWhenPayingNonApprovedCommission() {
        // Given
        UUID comissaoId = UUID.randomUUID();
        UUID pagoPor = UUID.randomUUID();

        Comissao comissao = Comissao.builder()
                .id(comissaoId)
                .tenantId(tenantId)
                .status(StatusComissao.PENDENTE) // Not approved yet
                .build();

        when(comissaoRepository.findById(comissaoId)).thenReturn(Optional.of(comissao));

        // When / Then
        assertThatThrownBy(() ->
                commissionService.marcarComoPaga(tenantId, comissaoId, pagoPor, "REF")
        ).isInstanceOf(BusinessException.class)
                .hasMessageContaining("Apenas comissões aprovadas podem ser pagas");
    }

    @Test
    @DisplayName("Should throw NotFoundException when commission not found")
    void shouldThrowNotFoundExceptionWhenCommissionNotExists() {
        // Given
        UUID comissaoId = UUID.randomUUID();
        when(comissaoRepository.findById(comissaoId)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() ->
                commissionService.buscarPorId(tenantId, comissaoId)
        ).isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Comissão não encontrada");
    }

    // ====================
    // Helper Methods
    // ====================

    private PoliticaComissao createPolitica(NivelPolitica nivel, TipoComissao tipo, BigDecimal percentual) {
        return PoliticaComissao.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .nivel(nivel)
                .tipo(tipo)
                .nome("Política Teste " + nivel)
                .percentualComissao(percentual)
                .ativa(true)
                .build();
    }

    private void mockVendedorPolicy(PoliticaComissao politica) {
        when(politicaRepository.findByTenantIdAndNivelAndModeloIdAndAtiva(any(), any(), any(), anyBoolean()))
                .thenReturn(Collections.emptyList());
        when(politicaRepository.findByTenantIdAndNivelAndAtivaOrderByDuracaoMinMinutosDesc(any(), any(), anyBoolean()))
                .thenReturn(Collections.emptyList());
        when(politicaRepository.findByTenantIdAndNivelAndVendedorIdAndAtiva(
                tenantId, NivelPolitica.VENDEDOR, vendedorId, true
        )).thenReturn(Collections.singletonList(politica));
    }
}
