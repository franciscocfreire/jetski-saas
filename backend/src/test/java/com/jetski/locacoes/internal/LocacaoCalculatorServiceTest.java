package com.jetski.locacoes.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for LocacaoCalculatorService
 *
 * Tests RN01: Billable time calculation with tolerance and rounding
 *
 * Scenarios from inicial.md:
 * - 1.1: Minutos usados = 10min, tolerância 5min → 15min faturável (arredonda para cima)
 * - 1.2: Minutos usados = 20min, tolerância 5min → 15min faturável
 * - 1.3: Minutos usados = 5min, tolerância 5min → 0min faturável
 *
 * @author Jetski Team
 * @since 0.7.0
 */
@DisplayName("LocacaoCalculatorService - RN01: Tolerance + 15-min Rounding")
class LocacaoCalculatorServiceTest {

    private LocacaoCalculatorService calculator;

    @BeforeEach
    void setUp() {
        calculator = new LocacaoCalculatorService();
    }

    // ===================================================================
    // Scenario 1.1: 10min used, 5min tolerance → 15min billable
    // ===================================================================

    @Test
    @DisplayName("Scenario 1.1: 10min used, 5min tolerance → 15min billable (round up)")
    void testCalculateBillableMinutes_Scenario1_1() {
        // Given: 10 minutes used, 5 minutes tolerance
        int usedMinutes = 10;
        int toleranceMinutes = 5;

        // When: Calculate billable minutes
        int billable = calculator.calculateBillableMinutes(usedMinutes, toleranceMinutes);

        // Then: Should be 15 minutes (10-5=5, round to 15)
        assertThat(billable).isEqualTo(15);
    }

    // ===================================================================
    // Scenario 1.2: 20min used, 5min tolerance → 15min billable
    // ===================================================================

    @Test
    @DisplayName("Scenario 1.2: 20min used, 5min tolerance → 15min billable (exact block)")
    void testCalculateBillableMinutes_Scenario1_2() {
        // Given: 20 minutes used, 5 minutes tolerance
        int usedMinutes = 20;
        int toleranceMinutes = 5;

        // When: Calculate billable minutes
        int billable = calculator.calculateBillableMinutes(usedMinutes, toleranceMinutes);

        // Then: Should be 15 minutes (20-5=15, already a 15-min block)
        assertThat(billable).isEqualTo(15);
    }

    // ===================================================================
    // Scenario 1.3: 5min used, 5min tolerance → 0min billable
    // ===================================================================

    @Test
    @DisplayName("Scenario 1.3: 5min used, 5min tolerance → 0min billable (within tolerance)")
    void testCalculateBillableMinutes_Scenario1_3() {
        // Given: 5 minutes used, 5 minutes tolerance
        int usedMinutes = 5;
        int toleranceMinutes = 5;

        // When: Calculate billable minutes
        int billable = calculator.calculateBillableMinutes(usedMinutes, toleranceMinutes);

        // Then: Should be 0 minutes (within tolerance)
        assertThat(billable).isEqualTo(0);
    }

    // ===================================================================
    // Edge Cases: Tolerance and Rounding
    // ===================================================================

    @Test
    @DisplayName("Edge case: 0min used → 0min billable")
    void testCalculateBillableMinutes_ZeroUsed() {
        assertThat(calculator.calculateBillableMinutes(0, 5)).isEqualTo(0);
    }

    @Test
    @DisplayName("Edge case: 4min used, 5min tolerance → 0min billable")
    void testCalculateBillableMinutes_BelowTolerance() {
        assertThat(calculator.calculateBillableMinutes(4, 5)).isEqualTo(0);
    }

    @Test
    @DisplayName("Edge case: 6min used, 5min tolerance → 15min billable (1min after tolerance)")
    void testCalculateBillableMinutes_OneMinuteAfterTolerance() {
        assertThat(calculator.calculateBillableMinutes(6, 5)).isEqualTo(15);
    }

    @Test
    @DisplayName("Edge case: 35min used, 5min tolerance → 30min billable (exactly 2 blocks)")
    void testCalculateBillableMinutes_ExactlyTwoBlocks() {
        assertThat(calculator.calculateBillableMinutes(35, 5)).isEqualTo(30);
    }

    @Test
    @DisplayName("Edge case: 36min used, 5min tolerance → 45min billable (round to 3 blocks)")
    void testCalculateBillableMinutes_RoundToThreeBlocks() {
        assertThat(calculator.calculateBillableMinutes(36, 5)).isEqualTo(45);
    }

    @Test
    @DisplayName("Edge case: 0min tolerance → billable = used (rounded)")
    void testCalculateBillableMinutes_ZeroTolerance() {
        assertThat(calculator.calculateBillableMinutes(10, 0)).isEqualTo(15);
        assertThat(calculator.calculateBillableMinutes(15, 0)).isEqualTo(15);
        assertThat(calculator.calculateBillableMinutes(16, 0)).isEqualTo(30);
    }

    @Test
    @DisplayName("Edge case: Large tolerance exceeds used → 0min billable")
    void testCalculateBillableMinutes_LargeToleranceExceedsUsed() {
        assertThat(calculator.calculateBillableMinutes(10, 20)).isEqualTo(0);
    }

    // ===================================================================
    // Calculate Used Minutes from Horimeter
    // ===================================================================

    @Test
    @DisplayName("Calculate used minutes from horimeter: 1.5 hours = 90 minutes")
    void testCalculateUsedMinutes_OnePointFiveHours() {
        BigDecimal horimetroInicio = new BigDecimal("100.0");
        BigDecimal horimetroFim = new BigDecimal("101.5");

        int usedMinutes = calculator.calculateUsedMinutes(horimetroInicio, horimetroFim);

        assertThat(usedMinutes).isEqualTo(90);
    }

    @Test
    @DisplayName("Calculate used minutes from horimeter: 0.25 hours = 15 minutes")
    void testCalculateUsedMinutes_QuarterHour() {
        BigDecimal horimetroInicio = new BigDecimal("100.0");
        BigDecimal horimetroFim = new BigDecimal("100.25");

        int usedMinutes = calculator.calculateUsedMinutes(horimetroInicio, horimetroFim);

        assertThat(usedMinutes).isEqualTo(15);
    }

    @Test
    @DisplayName("Calculate used minutes from horimeter: 2 hours = 120 minutes")
    void testCalculateUsedMinutes_TwoHours() {
        BigDecimal horimetroInicio = new BigDecimal("50.0");
        BigDecimal horimetroFim = new BigDecimal("52.0");

        int usedMinutes = calculator.calculateUsedMinutes(horimetroInicio, horimetroFim);

        assertThat(usedMinutes).isEqualTo(120);
    }

    // ===================================================================
    // Calculate Base Value
    // ===================================================================

    @Test
    @DisplayName("Calculate base value: 15min at R$150/hour = R$37.50")
    void testCalculateBaseValue_FifteenMinutes() {
        int billableMinutes = 15;
        BigDecimal pricePerHour = new BigDecimal("150.00");

        BigDecimal valorBase = calculator.calculateBaseValue(billableMinutes, pricePerHour);

        assertThat(valorBase).isEqualByComparingTo("37.50");
    }

    @Test
    @DisplayName("Calculate base value: 30min at R$150/hour = R$75.00")
    void testCalculateBaseValue_ThirtyMinutes() {
        int billableMinutes = 30;
        BigDecimal pricePerHour = new BigDecimal("150.00");

        BigDecimal valorBase = calculator.calculateBaseValue(billableMinutes, pricePerHour);

        assertThat(valorBase).isEqualByComparingTo("75.00");
    }

    @Test
    @DisplayName("Calculate base value: 60min at R$200/hour = R$200.00")
    void testCalculateBaseValue_OneHour() {
        int billableMinutes = 60;
        BigDecimal pricePerHour = new BigDecimal("200.00");

        BigDecimal valorBase = calculator.calculateBaseValue(billableMinutes, pricePerHour);

        assertThat(valorBase).isEqualByComparingTo("200.00");
    }

    @Test
    @DisplayName("Calculate base value: 0min = R$0.00")
    void testCalculateBaseValue_ZeroMinutes() {
        int billableMinutes = 0;
        BigDecimal pricePerHour = new BigDecimal("150.00");

        BigDecimal valorBase = calculator.calculateBaseValue(billableMinutes, pricePerHour);

        assertThat(valorBase).isEqualByComparingTo("0.00");
    }

    // ===================================================================
    // Validation: Horimeter Readings
    // ===================================================================

    @Test
    @DisplayName("Validation: horimetroFim must be >= horimetroInicio")
    void testValidateHorimetroReadings_Valid() {
        BigDecimal horimetroInicio = new BigDecimal("100.0");
        BigDecimal horimetroFim = new BigDecimal("102.0");

        // Should not throw exception
        calculator.validateHorimetroReadings(horimetroInicio, horimetroFim);
    }

    @Test
    @DisplayName("Validation: horimetroFim equals horimetroInicio is valid")
    void testValidateHorimetroReadings_Equal() {
        BigDecimal horimetroInicio = new BigDecimal("100.0");
        BigDecimal horimetroFim = new BigDecimal("100.0");

        // Should not throw exception
        calculator.validateHorimetroReadings(horimetroInicio, horimetroFim);
    }

    @Test
    @DisplayName("Validation: horimetroFim < horimetroInicio throws exception")
    void testValidateHorimetroReadings_Invalid() {
        BigDecimal horimetroInicio = new BigDecimal("102.0");
        BigDecimal horimetroFim = new BigDecimal("100.0");

        assertThatThrownBy(() ->
            calculator.validateHorimetroReadings(horimetroInicio, horimetroFim)
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Horímetro final");
    }

    @Test
    @DisplayName("Validation: null horimetroInicio throws exception")
    void testValidateHorimetroReadings_NullInicio() {
        BigDecimal horimetroFim = new BigDecimal("100.0");

        assertThatThrownBy(() ->
            calculator.validateHorimetroReadings(null, horimetroFim)
        )
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Validation: null horimetroFim throws exception")
    void testValidateHorimetroReadings_NullFim() {
        BigDecimal horimetroInicio = new BigDecimal("100.0");

        assertThatThrownBy(() ->
            calculator.validateHorimetroReadings(horimetroInicio, null)
        )
            .isInstanceOf(IllegalArgumentException.class);
    }

    // ===================================================================
    // Integration: Full Calculation Flow
    // ===================================================================

    @Test
    @DisplayName("Full flow: horimeter 100.0→101.5 (90min), tolerance 5min, R$150/hour → R$200.00")
    void testFullCalculationFlow() {
        // Given: horimeter readings
        BigDecimal horimetroInicio = new BigDecimal("100.0");
        BigDecimal horimetroFim = new BigDecimal("101.5");

        // Calculate used minutes: 1.5 hours = 90 minutes
        int usedMinutes = calculator.calculateUsedMinutes(horimetroInicio, horimetroFim);
        assertThat(usedMinutes).isEqualTo(90);

        // Apply RN01: 90-5=85min → ceil(85/15)*15 = 90min billable
        int billableMinutes = calculator.calculateBillableMinutes(usedMinutes, 5);
        assertThat(billableMinutes).isEqualTo(90);

        // Calculate value: 90min at R$150/hour = R$225.00
        BigDecimal pricePerHour = new BigDecimal("150.00");
        BigDecimal valorBase = calculator.calculateBaseValue(billableMinutes, pricePerHour);
        assertThat(valorBase).isEqualByComparingTo("225.00");
    }

    @Test
    @DisplayName("Full flow: horimeter 100.0→100.1 (6min), tolerance 5min, R$200/hour → R$50.00")
    void testFullCalculationFlow_ShortRental() {
        // Given: horimeter readings
        BigDecimal horimetroInicio = new BigDecimal("100.0");
        BigDecimal horimetroFim = new BigDecimal("100.1");

        // Calculate used minutes: 0.1 hours = 6 minutes
        int usedMinutes = calculator.calculateUsedMinutes(horimetroInicio, horimetroFim);
        assertThat(usedMinutes).isEqualTo(6);

        // Apply RN01: 6-5=1min → ceil(1/15)*15 = 15min billable
        int billableMinutes = calculator.calculateBillableMinutes(usedMinutes, 5);
        assertThat(billableMinutes).isEqualTo(15);

        // Calculate value: 15min at R$200/hour = R$50.00
        BigDecimal pricePerHour = new BigDecimal("200.00");
        BigDecimal valorBase = calculator.calculateBaseValue(billableMinutes, pricePerHour);
        assertThat(valorBase).isEqualByComparingTo("50.00");
    }
}
