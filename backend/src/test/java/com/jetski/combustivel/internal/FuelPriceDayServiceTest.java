package com.jetski.combustivel.internal;

import com.jetski.combustivel.domain.FuelPriceDay;
import com.jetski.combustivel.internal.repository.FuelPriceDayRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for FuelPriceDayService
 *
 * Tests intelligent fuel price fallback logic:
 * 1. Exact day price
 * 2. 7-day average
 * 3. Default R$ 6.00
 *
 * @author Jetski Team
 * @since 0.8.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FuelPriceDayService - Intelligent Price Fallback")
class FuelPriceDayServiceTest {

    @Mock
    private FuelPriceDayRepository fuelPriceDayRepository;

    @InjectMocks
    private FuelPriceDayService service;

    private UUID tenantId;
    private LocalDate dataConsulta;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        dataConsulta = LocalDate.of(2025, 10, 29);
    }

    // ===================================================================
    // Fallback Level 1: Exact day price
    // ===================================================================

    @Test
    @DisplayName("Level 1: Should return exact day price when exists")
    void testObterPrecoMedioDia_ExactDay() {
        // Given: Exact day price exists
        FuelPriceDay priceDay = FuelPriceDay.builder()
            .tenantId(tenantId)
            .data(dataConsulta)
            .precoMedioLitro(new BigDecimal("7.50"))
            .totalLitrosAbastecidos(new BigDecimal("100"))
            .totalCusto(new BigDecimal("750"))
            .qtdAbastecimentos(2)
            .build();

        when(fuelPriceDayRepository.findByTenantIdAndData(tenantId, dataConsulta))
            .thenReturn(Optional.of(priceDay));

        // When: Get average price
        BigDecimal preco = service.obterPrecoMedioDia(tenantId, dataConsulta);

        // Then: Should return exact day price
        assertThat(preco).isEqualByComparingTo(new BigDecimal("7.50"));

        // Should NOT search for weekly average
        verify(fuelPriceDayRepository, never()).findAveragePrice(any(), any(), any());
    }

    // ===================================================================
    // Fallback Level 2: 7-day average
    // ===================================================================

    @Test
    @DisplayName("Level 2: Should return 7-day average when exact day not found")
    void testObterPrecoMedioDia_WeeklyAverage() {
        // Given: No exact day, but weekly average exists
        when(fuelPriceDayRepository.findByTenantIdAndData(tenantId, dataConsulta))
            .thenReturn(Optional.empty());

        LocalDate dataInicio = dataConsulta.minusDays(7);
        when(fuelPriceDayRepository.findAveragePrice(tenantId, dataInicio, dataConsulta))
            .thenReturn(Optional.of(new BigDecimal("6.80")));

        // When: Get average price
        BigDecimal preco = service.obterPrecoMedioDia(tenantId, dataConsulta);

        // Then: Should return 7-day average
        assertThat(preco).isEqualByComparingTo(new BigDecimal("6.80"));

        // Should search for weekly average
        verify(fuelPriceDayRepository).findAveragePrice(tenantId, dataInicio, dataConsulta);
    }

    // ===================================================================
    // Fallback Level 3: Default R$ 6.00
    // ===================================================================

    @Test
    @DisplayName("Level 3: Should return default R$ 6.00 when no data exists")
    void testObterPrecoMedioDia_DefaultPrice() {
        // Given: No exact day and no weekly average
        when(fuelPriceDayRepository.findByTenantIdAndData(tenantId, dataConsulta))
            .thenReturn(Optional.empty());

        when(fuelPriceDayRepository.findAveragePrice(any(), any(), any()))
            .thenReturn(Optional.empty());

        // When: Get average price
        BigDecimal preco = service.obterPrecoMedioDia(tenantId, dataConsulta);

        // Then: Should return default R$ 6.00
        assertThat(preco).isEqualByComparingTo(new BigDecimal("6.00"));
    }

    @Test
    @DisplayName("Level 3: Should return default R$ 6.00 when weekly average is ZERO")
    void testObterPrecoMedioDia_DefaultPrice_ZeroAverage() {
        // Given: No exact day and weekly average is ZERO
        when(fuelPriceDayRepository.findByTenantIdAndData(tenantId, dataConsulta))
            .thenReturn(Optional.empty());

        when(fuelPriceDayRepository.findAveragePrice(any(), any(), any()))
            .thenReturn(Optional.of(BigDecimal.ZERO));

        // When: Get average price
        BigDecimal preco = service.obterPrecoMedioDia(tenantId, dataConsulta);

        // Then: Should return default R$ 6.00 (ZERO is invalid)
        assertThat(preco).isEqualByComparingTo(new BigDecimal("6.00"));
    }

    // ===================================================================
    // Update average price: New refill
    // ===================================================================

    @Test
    @DisplayName("New refill: Should create new FuelPriceDay")
    void testAtualizarPrecoMedioDia_NewDay() {
        // Given: No existing price for the day
        when(fuelPriceDayRepository.findByTenantIdAndData(tenantId, dataConsulta))
            .thenReturn(Optional.empty());

        when(fuelPriceDayRepository.save(any(FuelPriceDay.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When: Register new refill (50 liters × R$ 7.00 = R$ 350)
        service.atualizarPrecoMedioDia(
            tenantId,
            dataConsulta,
            new BigDecimal("50"),
            new BigDecimal("350")
        );

        // Then: Should create new FuelPriceDay with average R$ 7.00
        verify(fuelPriceDayRepository).save(argThat(priceDay ->
            priceDay.getTenantId().equals(tenantId) &&
            priceDay.getData().equals(dataConsulta) &&
            priceDay.getPrecoMedioLitro().compareTo(new BigDecimal("7.00")) == 0 &&
            priceDay.getTotalLitrosAbastecidos().compareTo(new BigDecimal("50")) == 0 &&
            priceDay.getTotalCusto().compareTo(new BigDecimal("350")) == 0 &&
            priceDay.getQtdAbastecimentos() == 1
        ));
    }

    // ===================================================================
    // Update average price: Existing day
    // ===================================================================

    @Test
    @DisplayName("Existing day: Should update average with new refill")
    void testAtualizarPrecoMedioDia_UpdateExisting() {
        // Given: Existing price with 1 refill (50L × R$ 7.00 = R$ 350)
        FuelPriceDay existing = FuelPriceDay.builder()
            .id(1L)
            .tenantId(tenantId)
            .data(dataConsulta)
            .precoMedioLitro(new BigDecimal("7.00"))
            .totalLitrosAbastecidos(new BigDecimal("50"))
            .totalCusto(new BigDecimal("350"))
            .qtdAbastecimentos(1)
            .build();

        when(fuelPriceDayRepository.findByTenantIdAndData(tenantId, dataConsulta))
            .thenReturn(Optional.of(existing));

        when(fuelPriceDayRepository.save(any(FuelPriceDay.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When: Add new refill (30L × R$ 6.00 = R$ 180)
        service.atualizarPrecoMedioDia(
            tenantId,
            dataConsulta,
            new BigDecimal("30"),
            new BigDecimal("180")
        );

        // Then: Should update totals and recalculate average
        // Total: 80L, R$ 530 → Average: R$ 6.625 ≈ R$ 6.63 (rounded)
        verify(fuelPriceDayRepository).save(argThat(priceDay ->
            priceDay.getId().equals(1L) &&
            priceDay.getTotalLitrosAbastecidos().compareTo(new BigDecimal("80")) == 0 &&
            priceDay.getTotalCusto().compareTo(new BigDecimal("530")) == 0 &&
            priceDay.getQtdAbastecimentos() == 2 &&
            // Average should be recalculated: 530 / 80 = 6.625 → 6.63 (HALF_UP)
            priceDay.getPrecoMedioLitro().compareTo(new BigDecimal("6.63")) == 0
        ));
    }

    // ===================================================================
    // Admin override: Save/overwrite price
    // ===================================================================

    @Test
    @DisplayName("Admin override: Should create new price when not exists")
    void testSalvar_NewPrice() {
        // Given: No existing price
        when(fuelPriceDayRepository.findByTenantIdAndData(tenantId, dataConsulta))
            .thenReturn(Optional.empty());

        FuelPriceDay newPrice = FuelPriceDay.builder()
            .tenantId(tenantId)
            .data(dataConsulta)
            .precoMedioLitro(new BigDecimal("8.00"))
            .totalLitrosAbastecidos(BigDecimal.ZERO)
            .totalCusto(BigDecimal.ZERO)
            .qtdAbastecimentos(0)
            .build();

        when(fuelPriceDayRepository.save(any(FuelPriceDay.class)))
            .thenReturn(newPrice);

        // When: Admin sets custom price
        FuelPriceDay saved = service.salvar(newPrice);

        // Then: Should save new price
        assertThat(saved).isNotNull();
        assertThat(saved.getPrecoMedioLitro()).isEqualByComparingTo(new BigDecimal("8.00"));
        verify(fuelPriceDayRepository).save(newPrice);
    }

    @Test
    @DisplayName("Admin override: Should update existing price")
    void testSalvar_UpdateExisting() {
        // Given: Existing price with R$ 7.00
        FuelPriceDay existing = FuelPriceDay.builder()
            .id(1L)
            .tenantId(tenantId)
            .data(dataConsulta)
            .precoMedioLitro(new BigDecimal("7.00"))
            .totalLitrosAbastecidos(new BigDecimal("100"))
            .totalCusto(new BigDecimal("700"))
            .qtdAbastecimentos(2)
            .build();

        when(fuelPriceDayRepository.findByTenantIdAndData(tenantId, dataConsulta))
            .thenReturn(Optional.of(existing));

        when(fuelPriceDayRepository.save(any(FuelPriceDay.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When: Admin overrides with new price R$ 8.50
        FuelPriceDay override = FuelPriceDay.builder()
            .tenantId(tenantId)
            .data(dataConsulta)
            .precoMedioLitro(new BigDecimal("8.50"))
            .totalLitrosAbastecidos(new BigDecimal("150"))
            .totalCusto(new BigDecimal("1275"))
            .qtdAbastecimentos(3)
            .build();

        FuelPriceDay saved = service.salvar(override);

        // Then: Should update existing record (ID=1)
        verify(fuelPriceDayRepository).save(argThat(priceDay ->
            priceDay.getId().equals(1L) &&
            priceDay.getPrecoMedioLitro().compareTo(new BigDecimal("8.50")) == 0 &&
            priceDay.getTotalLitrosAbastecidos().compareTo(new BigDecimal("150")) == 0 &&
            priceDay.getTotalCusto().compareTo(new BigDecimal("1275")) == 0 &&
            priceDay.getQtdAbastecimentos() == 3
        ));
    }
}
