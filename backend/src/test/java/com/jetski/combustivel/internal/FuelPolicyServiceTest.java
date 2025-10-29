package com.jetski.combustivel.internal;

import com.jetski.combustivel.domain.*;
import com.jetski.combustivel.internal.repository.AbastecimentoRepository;
import com.jetski.combustivel.internal.repository.FuelPolicyRepository;
import com.jetski.locacoes.domain.Locacao;
import com.jetski.shared.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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

        Locacao locacao = Locacao.builder()
            .tenantId(tenantId)
            .jetskiId(jetskiId)
            .minutosUsados(65)
            .minutosFaturaveis(60)
            .build();

        // When: Calculate fuel cost
        BigDecimal custo = service.calcularCustoCombustivel(locacao, modeloId);

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

        Locacao locacao = Locacao.builder()
            .tenantId(tenantId)
            .jetskiId(jetskiId)
            .minutosUsados(95) // 95 minutes used
            .minutosFaturaveis(90) // 1.5 hours billable after tolerance
            .build();

        // When: Calculate fuel cost
        BigDecimal custo = service.calcularCustoCombustivel(locacao, modeloId);

        // Then: Should be R$ 15.00 (1.5 hours × R$ 10/hour)
        assertThat(custo).isEqualByComparingTo(new BigDecimal("15.00"));
    }
}
