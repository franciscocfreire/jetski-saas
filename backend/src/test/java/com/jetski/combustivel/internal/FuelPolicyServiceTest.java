package com.jetski.combustivel.internal;

import com.jetski.combustivel.domain.*;
import com.jetski.combustivel.internal.repository.AbastecimentoRepository;
import com.jetski.combustivel.internal.repository.FuelPolicyRepository;
import com.jetski.shared.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for FuelPolicyService
 *
 * Tests RN03: Hierarchical fuel policy lookup
 * Order: JETSKI → MODELO → GLOBAL
 *
 * @author Jetski Team
 * @since 0.8.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FuelPolicyService - RN03: Hierarchical Policy Lookup")
class FuelPolicyServiceTest {

    @Mock
    private FuelPolicyRepository fuelPolicyRepository;

    @Mock
    private FuelPriceDayService fuelPriceDayService;

    @Mock
    private AbastecimentoRepository abastecimentoRepository;

    @InjectMocks
    private FuelPolicyService service;

    private UUID tenantId;
    private UUID jetskiId;
    private UUID modeloId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        jetskiId = UUID.randomUUID();
        modeloId = UUID.randomUUID();
    }

    /**
     * Helper method to create LocacaoFuelData for tests.
     */
    private LocacaoFuelData createLocacaoFuelData(int minutosFaturaveis) {
        return LocacaoFuelData.builder()
            .id(UUID.randomUUID())
            .tenantId(tenantId)
            .jetskiId(jetskiId)
            .dataCheckOut(Instant.now())
            .minutosFaturaveis(minutosFaturaveis)
            .build();
    }

    // ===================================================================
    // RN03.1: JETSKI specific policy (highest priority)
    // ===================================================================

    @Test
    @DisplayName("RN03.1: Should return JETSKI-specific policy when exists")
    void testBuscarPoliticaAplicavel_JetskiPolicy() {
        // Given: JETSKI-specific policy exists
        FuelPolicy jetskiPolicy = FuelPolicy.builder()
            .id(1L)
            .tenantId(tenantId)
            .nome("Taxa Fixa Jetski 123")
            .tipo(FuelChargeMode.TAXA_FIXA)
            .aplicavelA(FuelPolicyType.JETSKI)
            .referenciaId(jetskiId)
            .valorTaxaPorHora(new BigDecimal("10.00"))
            .ativo(true)
            .build();

        when(fuelPolicyRepository.findByTenantIdAndAplicavelAAndReferenciaIdAndAtivoTrue(
            tenantId, FuelPolicyType.JETSKI, jetskiId
        )).thenReturn(Optional.of(jetskiPolicy));

        // When: Search for applicable policy
        FuelPolicy result = service.buscarPoliticaAplicavel(tenantId, jetskiId, modeloId);

        // Then: Should return JETSKI policy (highest priority)
        assertThat(result).isNotNull();
        assertThat(result.getAplicavelA()).isEqualTo(FuelPolicyType.JETSKI);
        assertThat(result.getReferenciaId()).isEqualTo(jetskiId);
        assertThat(result.getTipo()).isEqualTo(FuelChargeMode.TAXA_FIXA);

        // Should NOT search for MODELO or GLOBAL
        verify(fuelPolicyRepository, never()).findByTenantIdAndAplicavelAAndReferenciaIdAndAtivoTrue(
            any(), eq(FuelPolicyType.MODELO), any()
        );
        verify(fuelPolicyRepository, never()).findByTenantIdAndAplicavelAAndAtivoTrue(
            any(), eq(FuelPolicyType.GLOBAL)
        );
    }

    // ===================================================================
    // RN03.2: MODELO policy (second priority)
    // ===================================================================

    @Test
    @DisplayName("RN03.2: Should return MODELO policy when JETSKI not found")
    void testBuscarPoliticaAplicavel_ModeloPolicy() {
        // Given: No JETSKI policy, but MODELO policy exists
        when(fuelPolicyRepository.findByTenantIdAndAplicavelAAndReferenciaIdAndAtivoTrue(
            tenantId, FuelPolicyType.JETSKI, jetskiId
        )).thenReturn(Optional.empty());

        FuelPolicy modeloPolicy = FuelPolicy.builder()
            .id(2L)
            .tenantId(tenantId)
            .nome("Medido para Modelo SeaDoo")
            .tipo(FuelChargeMode.MEDIDO)
            .aplicavelA(FuelPolicyType.MODELO)
            .referenciaId(modeloId)
            .ativo(true)
            .build();

        when(fuelPolicyRepository.findByTenantIdAndAplicavelAAndReferenciaIdAndAtivoTrue(
            tenantId, FuelPolicyType.MODELO, modeloId
        )).thenReturn(Optional.of(modeloPolicy));

        // When: Search for applicable policy
        FuelPolicy result = service.buscarPoliticaAplicavel(tenantId, jetskiId, modeloId);

        // Then: Should return MODELO policy
        assertThat(result).isNotNull();
        assertThat(result.getAplicavelA()).isEqualTo(FuelPolicyType.MODELO);
        assertThat(result.getReferenciaId()).isEqualTo(modeloId);
        assertThat(result.getTipo()).isEqualTo(FuelChargeMode.MEDIDO);

        // Should search for JETSKI first, then MODELO
        verify(fuelPolicyRepository).findByTenantIdAndAplicavelAAndReferenciaIdAndAtivoTrue(
            tenantId, FuelPolicyType.JETSKI, jetskiId
        );
        verify(fuelPolicyRepository).findByTenantIdAndAplicavelAAndReferenciaIdAndAtivoTrue(
            tenantId, FuelPolicyType.MODELO, modeloId
        );

        // Should NOT search for GLOBAL
        verify(fuelPolicyRepository, never()).findByTenantIdAndAplicavelAAndAtivoTrue(
            any(), eq(FuelPolicyType.GLOBAL)
        );
    }

    // ===================================================================
    // RN03.3: GLOBAL policy (fallback)
    // ===================================================================

    @Test
    @DisplayName("RN03.3: Should return GLOBAL policy when JETSKI and MODELO not found")
    void testBuscarPoliticaAplicavel_GlobalPolicy() {
        // Given: No JETSKI or MODELO policy, but GLOBAL exists
        when(fuelPolicyRepository.findByTenantIdAndAplicavelAAndReferenciaIdAndAtivoTrue(
            tenantId, FuelPolicyType.JETSKI, jetskiId
        )).thenReturn(Optional.empty());

        when(fuelPolicyRepository.findByTenantIdAndAplicavelAAndReferenciaIdAndAtivoTrue(
            tenantId, FuelPolicyType.MODELO, modeloId
        )).thenReturn(Optional.empty());

        FuelPolicy globalPolicy = FuelPolicy.builder()
            .id(3L)
            .tenantId(tenantId)
            .nome("Incluso Global")
            .tipo(FuelChargeMode.INCLUSO)
            .aplicavelA(FuelPolicyType.GLOBAL)
            .referenciaId(null)
            .ativo(true)
            .build();

        when(fuelPolicyRepository.findByTenantIdAndAplicavelAAndAtivoTrue(
            tenantId, FuelPolicyType.GLOBAL
        )).thenReturn(Optional.of(globalPolicy));

        // When: Search for applicable policy
        FuelPolicy result = service.buscarPoliticaAplicavel(tenantId, jetskiId, modeloId);

        // Then: Should return GLOBAL policy (fallback)
        assertThat(result).isNotNull();
        assertThat(result.getAplicavelA()).isEqualTo(FuelPolicyType.GLOBAL);
        assertThat(result.getReferenciaId()).isNull();
        assertThat(result.getTipo()).isEqualTo(FuelChargeMode.INCLUSO);

        // Should search all three levels
        verify(fuelPolicyRepository).findByTenantIdAndAplicavelAAndReferenciaIdAndAtivoTrue(
            tenantId, FuelPolicyType.JETSKI, jetskiId
        );
        verify(fuelPolicyRepository).findByTenantIdAndAplicavelAAndReferenciaIdAndAtivoTrue(
            tenantId, FuelPolicyType.MODELO, modeloId
        );
        verify(fuelPolicyRepository).findByTenantIdAndAplicavelAAndAtivoTrue(
            tenantId, FuelPolicyType.GLOBAL
        );
    }

    // ===================================================================
    // RN03.4: No policy found (error case)
    // ===================================================================

    @Test
    @DisplayName("RN03.4: Should throw NotFoundException when no policy found")
    void testBuscarPoliticaAplicavel_NoPolicy() {
        // Given: No policies exist at any level
        when(fuelPolicyRepository.findByTenantIdAndAplicavelAAndReferenciaIdAndAtivoTrue(
            any(), any(), any()
        )).thenReturn(Optional.empty());

        when(fuelPolicyRepository.findByTenantIdAndAplicavelAAndAtivoTrue(
            any(), any()
        )).thenReturn(Optional.empty());

        // When/Then: Should throw NotFoundException
        assertThatThrownBy(() ->
            service.buscarPoliticaAplicavel(tenantId, jetskiId, modeloId)
        )
            .isInstanceOf(NotFoundException.class)
            .hasMessageContaining("Nenhuma política de combustível ativa encontrada");
    }

    // ===================================================================
    // Fuel Cost Calculation: INCLUSO mode
    // ===================================================================

    @Test
    @DisplayName("INCLUSO mode: Should return ZERO cost")
    void testCalcularCustoCombustivel_Incluso() {
        // Given: INCLUSO policy
        FuelPolicy policy = FuelPolicy.builder()
            .tipo(FuelChargeMode.INCLUSO)
            .aplicavelA(FuelPolicyType.GLOBAL)
            .ativo(true)
            .build();

        when(fuelPolicyRepository.findByTenantIdAndAplicavelAAndAtivoTrue(
            tenantId, FuelPolicyType.GLOBAL
        )).thenReturn(Optional.of(policy));

        LocacaoFuelData locacaoData = createLocacaoFuelData(60);

        // When: Calculate fuel cost
        BigDecimal custo = service.calcularCustoCombustivel(locacaoData, modeloId);

        // Then: Should return ZERO
        assertThat(custo).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ===================================================================
    // Fuel Cost Calculation: TAXA_FIXA mode
    // ===================================================================

    @Test
    @DisplayName("TAXA_FIXA mode: Should calculate fixed rate per billable hour")
    void testCalcularCustoCombustivel_TaxaFixa() {
        // Given: TAXA_FIXA policy with R$ 10/hour
        FuelPolicy policy = FuelPolicy.builder()
            .tipo(FuelChargeMode.TAXA_FIXA)
            .aplicavelA(FuelPolicyType.GLOBAL)
            .valorTaxaPorHora(new BigDecimal("10.00"))
            .ativo(true)
            .build();

        when(fuelPolicyRepository.findByTenantIdAndAplicavelAAndAtivoTrue(
            tenantId, FuelPolicyType.GLOBAL
        )).thenReturn(Optional.of(policy));

        LocacaoFuelData locacaoData = createLocacaoFuelData(90); // 1.5 hours billable after tolerance

        // When: Calculate fuel cost
        BigDecimal custo = service.calcularCustoCombustivel(locacaoData, modeloId);

        // Then: Should be R$ 15.00 (1.5 hours × R$ 10/hour)
        assertThat(custo).isEqualByComparingTo(new BigDecimal("15.00"));
    }

    // ===================================================================
    // Fuel Cost Calculation: MEDIDO mode
    // ===================================================================

    @Test
    @DisplayName("MEDIDO mode: Should calculate cost from abastecimentos (PRE + POS)")
    void testCalcularCustoCombustivel_Medido() {
        // Given: MEDIDO policy
        FuelPolicy policy = FuelPolicy.builder()
            .tipo(FuelChargeMode.MEDIDO)
            .aplicavelA(FuelPolicyType.GLOBAL)
            .ativo(true)
            .build();

        when(fuelPolicyRepository.findByTenantIdAndAplicavelAAndAtivoTrue(
            tenantId, FuelPolicyType.GLOBAL
        )).thenReturn(Optional.of(policy));

        // Given: Locacao with check-out date
        UUID locacaoId = UUID.randomUUID();
        LocalDate dataCheckOut = LocalDate.now();
        LocacaoFuelData locacaoData = LocacaoFuelData.builder()
            .id(locacaoId)
            .tenantId(tenantId)
            .jetskiId(jetskiId)
            .dataCheckOut(dataCheckOut.atStartOfDay().toInstant(ZoneOffset.UTC))
            .minutosFaturaveis(60)
            .build();

        // Given: Abastecimentos PRE (50L) and POS (30L) = 20L consumidos
        List<Abastecimento> abastecimentos = List.of(
            Abastecimento.builder()
                .tenantId(tenantId)
                .locacaoId(locacaoId)
                .tipo(TipoAbastecimento.PRE_LOCACAO)
                .litros(new BigDecimal("50.00"))
                .build(),
            Abastecimento.builder()
                .tenantId(tenantId)
                .locacaoId(locacaoId)
                .tipo(TipoAbastecimento.POS_LOCACAO)
                .litros(new BigDecimal("30.00"))
                .build()
        );

        when(abastecimentoRepository.findByTenantIdAndLocacaoIdOrderByDataHoraAsc(
            tenantId, locacaoId
        )).thenReturn(abastecimentos);

        // Given: Fuel price = R$ 7.00/L
        when(fuelPriceDayService.obterPrecoMedioDia(eq(tenantId), any(LocalDate.class)))
            .thenReturn(new BigDecimal("7.00"));

        // When: Calculate fuel cost
        BigDecimal custo = service.calcularCustoCombustivel(locacaoData, modeloId);

        // Then: Should be 20L × R$ 7.00 = R$ 140.00
        assertThat(custo).isEqualByComparingTo(new BigDecimal("140.00"));
    }

    @Test
    @DisplayName("MEDIDO mode: Should return ZERO when no abastecimentos found")
    void testCalcularCustoCombustivel_Medido_NoAbastecimentos() {
        // Given: MEDIDO policy
        FuelPolicy policy = FuelPolicy.builder()
            .tipo(FuelChargeMode.MEDIDO)
            .aplicavelA(FuelPolicyType.GLOBAL)
            .ativo(true)
            .build();

        when(fuelPolicyRepository.findByTenantIdAndAplicavelAAndAtivoTrue(
            tenantId, FuelPolicyType.GLOBAL
        )).thenReturn(Optional.of(policy));

        // Given: Locacao with no abastecimentos
        UUID locacaoId = UUID.randomUUID();
        LocacaoFuelData locacaoData = LocacaoFuelData.builder()
            .id(locacaoId)
            .tenantId(tenantId)
            .jetskiId(jetskiId)
            .dataCheckOut(Instant.now())
            .minutosFaturaveis(60)
            .build();

        when(abastecimentoRepository.findByTenantIdAndLocacaoIdOrderByDataHoraAsc(
            tenantId, locacaoId
        )).thenReturn(Collections.emptyList());

        // When: Calculate fuel cost
        BigDecimal custo = service.calcularCustoCombustivel(locacaoData, modeloId);

        // Then: Should return ZERO
        assertThat(custo).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("MEDIDO mode: Should return ZERO when litros consumidos = 0")
    void testCalcularCustoCombustivel_Medido_ZeroLitrosConsumidos() {
        // Given: MEDIDO policy
        FuelPolicy policy = FuelPolicy.builder()
            .tipo(FuelChargeMode.MEDIDO)
            .aplicavelA(FuelPolicyType.GLOBAL)
            .ativo(true)
            .build();

        when(fuelPolicyRepository.findByTenantIdAndAplicavelAAndAtivoTrue(
            tenantId, FuelPolicyType.GLOBAL
        )).thenReturn(Optional.of(policy));

        // Given: Locacao with PRE = POS (same fuel level)
        UUID locacaoId = UUID.randomUUID();
        LocacaoFuelData locacaoData = LocacaoFuelData.builder()
            .id(locacaoId)
            .tenantId(tenantId)
            .jetskiId(jetskiId)
            .dataCheckOut(Instant.now())
            .minutosFaturaveis(60)
            .build();

        // Given: PRE and POS both 50L (no consumption)
        List<Abastecimento> abastecimentos = List.of(
            Abastecimento.builder()
                .tenantId(tenantId)
                .locacaoId(locacaoId)
                .tipo(TipoAbastecimento.PRE_LOCACAO)
                .litros(new BigDecimal("50.00"))
                .build(),
            Abastecimento.builder()
                .tenantId(tenantId)
                .locacaoId(locacaoId)
                .tipo(TipoAbastecimento.POS_LOCACAO)
                .litros(new BigDecimal("50.00"))
                .build()
        );

        when(abastecimentoRepository.findByTenantIdAndLocacaoIdOrderByDataHoraAsc(
            tenantId, locacaoId
        )).thenReturn(abastecimentos);

        // When: Calculate fuel cost
        BigDecimal custo = service.calcularCustoCombustivel(locacaoData, modeloId);

        // Then: Should return ZERO
        assertThat(custo).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ===================================================================
    // TAXA_FIXA validation
    // ===================================================================

    @Test
    @DisplayName("TAXA_FIXA mode: Should throw error when valorTaxaPorHora is null")
    void testCalcularCustoCombustivel_TaxaFixa_MissingValor() {
        // Given: TAXA_FIXA policy WITHOUT valorTaxaPorHora
        FuelPolicy policy = FuelPolicy.builder()
            .tipo(FuelChargeMode.TAXA_FIXA)
            .aplicavelA(FuelPolicyType.GLOBAL)
            .valorTaxaPorHora(null) // MISSING!
            .ativo(true)
            .build();

        when(fuelPolicyRepository.findByTenantIdAndAplicavelAAndAtivoTrue(
            tenantId, FuelPolicyType.GLOBAL
        )).thenReturn(Optional.of(policy));

        LocacaoFuelData locacaoData = createLocacaoFuelData(60);

        // When/Then: Should throw IllegalStateException
        assertThatThrownBy(() ->
            service.calcularCustoCombustivel(locacaoData, modeloId)
        )
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Política TAXA_FIXA sem valor configurado");
    }

    // ===================================================================
    // Policy Validation Tests
    // ===================================================================

    @Test
    @DisplayName("validatePolicy: Should reject TAXA_FIXA without valorTaxaPorHora")
    void testCriar_ValidacaoTaxaFixaSemValor() {
        // Given: TAXA_FIXA policy without valorTaxaPorHora
        FuelPolicy policy = FuelPolicy.builder()
            .tenantId(tenantId)
            .nome("Taxa Fixa Sem Valor")
            .tipo(FuelChargeMode.TAXA_FIXA)
            .aplicavelA(FuelPolicyType.GLOBAL)
            .valorTaxaPorHora(null) // MISSING!
            .ativo(true)
            .build();

        // When/Then: Should throw IllegalArgumentException
        assertThatThrownBy(() ->
            service.criar(tenantId, policy)
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Política TAXA_FIXA requer valor_taxa_por_hora configurado");
    }

    @Test
    @DisplayName("validatePolicy: Should reject GLOBAL with referenciaId")
    void testCriar_ValidacaoGlobalComReferencia() {
        // Given: GLOBAL policy WITH referenciaId (should be null)
        FuelPolicy policy = FuelPolicy.builder()
            .tenantId(tenantId)
            .nome("Global Inválido")
            .tipo(FuelChargeMode.INCLUSO)
            .aplicavelA(FuelPolicyType.GLOBAL)
            .referenciaId(UUID.randomUUID()) // INVALID for GLOBAL!
            .ativo(true)
            .build();

        // When/Then: Should throw IllegalArgumentException
        assertThatThrownBy(() ->
            service.criar(tenantId, policy)
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Política GLOBAL não pode ter referencia_id");
    }

    @Test
    @DisplayName("validatePolicy: Should reject MODELO without referenciaId")
    void testCriar_ValidacaoModeloSemReferencia() {
        // Given: MODELO policy WITHOUT referenciaId
        FuelPolicy policy = FuelPolicy.builder()
            .tenantId(tenantId)
            .nome("Modelo Sem Referência")
            .tipo(FuelChargeMode.MEDIDO)
            .aplicavelA(FuelPolicyType.MODELO)
            .referenciaId(null) // MISSING!
            .ativo(true)
            .build();

        // When/Then: Should throw IllegalArgumentException
        assertThatThrownBy(() ->
            service.criar(tenantId, policy)
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Política MODELO requer referencia_id");
    }

    @Test
    @DisplayName("validatePolicy: Should reject JETSKI without referenciaId")
    void testCriar_ValidacaoJetskiSemReferencia() {
        // Given: JETSKI policy WITHOUT referenciaId
        FuelPolicy policy = FuelPolicy.builder()
            .tenantId(tenantId)
            .nome("Jetski Sem Referência")
            .tipo(FuelChargeMode.TAXA_FIXA)
            .aplicavelA(FuelPolicyType.JETSKI)
            .referenciaId(null) // MISSING!
            .valorTaxaPorHora(new BigDecimal("10.00"))
            .ativo(true)
            .build();

        // When/Then: Should throw IllegalArgumentException
        assertThatThrownBy(() ->
            service.criar(tenantId, policy)
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Política JETSKI requer referencia_id");
    }

    // ===================================================================
    // CRUD Tests: atualizar
    // ===================================================================

    @Test
    @DisplayName("atualizar: Should update existing policy successfully")
    void testAtualizar_Success() {
        // Given: Existing policy
        Long policyId = 1L;
        FuelPolicy existing = FuelPolicy.builder()
            .id(policyId)
            .tenantId(tenantId)
            .nome("Nome Original")
            .tipo(FuelChargeMode.INCLUSO)
            .aplicavelA(FuelPolicyType.GLOBAL)
            .ativo(true)
            .prioridade(10)
            .build();

        when(fuelPolicyRepository.findById(policyId))
            .thenReturn(Optional.of(existing));

        // Given: Updates
        FuelPolicy updates = FuelPolicy.builder()
            .nome("Nome Atualizado")
            .prioridade(20)
            .build();

        when(fuelPolicyRepository.save(any(FuelPolicy.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When: Update policy
        FuelPolicy result = service.atualizar(tenantId, policyId, updates);

        // Then: Should update fields
        assertThat(result.getNome()).isEqualTo("Nome Atualizado");
        assertThat(result.getPrioridade()).isEqualTo(20);
        verify(fuelPolicyRepository).save(existing);
    }

    @Test
    @DisplayName("atualizar: Should reject update when policy not found")
    void testAtualizar_NotFound() {
        // Given: Policy does not exist
        Long policyId = 999L;
        when(fuelPolicyRepository.findById(policyId))
            .thenReturn(Optional.empty());

        FuelPolicy updates = FuelPolicy.builder()
            .nome("Novo Nome")
            .build();

        // When/Then: Should throw NotFoundException
        assertThatThrownBy(() ->
            service.atualizar(tenantId, policyId, updates)
        )
            .isInstanceOf(NotFoundException.class)
            .hasMessageContaining("FuelPolicy não encontrada");
    }

    @Test
    @DisplayName("atualizar: Should reject update when tenant mismatch")
    void testAtualizar_TenantMismatch() {
        // Given: Policy belongs to different tenant
        Long policyId = 1L;
        UUID differentTenantId = UUID.randomUUID();

        FuelPolicy existing = FuelPolicy.builder()
            .id(policyId)
            .tenantId(differentTenantId) // Different tenant!
            .nome("Original")
            .build();

        when(fuelPolicyRepository.findById(policyId))
            .thenReturn(Optional.of(existing));

        FuelPolicy updates = FuelPolicy.builder()
            .nome("Atualizado")
            .build();

        // When/Then: Should throw NotFoundException
        assertThatThrownBy(() ->
            service.atualizar(tenantId, policyId, updates)
        )
            .isInstanceOf(NotFoundException.class)
            .hasMessageContaining("FuelPolicy não pertence ao tenant");
    }

    // ===================================================================
    // CRUD Tests: desativar
    // ===================================================================

    @Test
    @DisplayName("desativar: Should deactivate policy successfully")
    void testDesativar_Success() {
        // Given: Active policy
        Long policyId = 1L;
        FuelPolicy policy = FuelPolicy.builder()
            .id(policyId)
            .tenantId(tenantId)
            .nome("Política Ativa")
            .ativo(true)
            .build();

        when(fuelPolicyRepository.findById(policyId))
            .thenReturn(Optional.of(policy));

        when(fuelPolicyRepository.save(any(FuelPolicy.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When: Deactivate policy
        service.desativar(tenantId, policyId);

        // Then: Should set ativo = false
        assertThat(policy.getAtivo()).isFalse();
        verify(fuelPolicyRepository).save(policy);
    }

    @Test
    @DisplayName("desativar: Should reject when policy not found")
    void testDesativar_NotFound() {
        // Given: Policy does not exist
        Long policyId = 999L;
        when(fuelPolicyRepository.findById(policyId))
            .thenReturn(Optional.empty());

        // When/Then: Should throw NotFoundException
        assertThatThrownBy(() ->
            service.desativar(tenantId, policyId)
        )
            .isInstanceOf(NotFoundException.class)
            .hasMessageContaining("FuelPolicy não encontrada");
    }

    @Test
    @DisplayName("desativar: Should reject when tenant mismatch")
    void testDesativar_TenantMismatch() {
        // Given: Policy belongs to different tenant
        Long policyId = 1L;
        UUID differentTenantId = UUID.randomUUID();

        FuelPolicy policy = FuelPolicy.builder()
            .id(policyId)
            .tenantId(differentTenantId) // Different tenant!
            .nome("Política")
            .ativo(true)
            .build();

        when(fuelPolicyRepository.findById(policyId))
            .thenReturn(Optional.of(policy));

        // When/Then: Should throw NotFoundException
        assertThatThrownBy(() ->
            service.desativar(tenantId, policyId)
        )
            .isInstanceOf(NotFoundException.class)
            .hasMessageContaining("FuelPolicy não pertence ao tenant");
    }
}
