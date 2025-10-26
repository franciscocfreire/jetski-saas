package com.jetski.locacoes.internal;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Service: LocacaoCalculatorService
 *
 * Implements billing calculation logic for rental operations.
 * Applies business rules for tolerance, rounding, and value calculation.
 *
 * Business Rules Implemented:
 * - RN01: Billable time calculation with tolerance and 15-minute rounding
 *
 * RN01 - Billable Time Calculation:
 * 1. Subtract tolerance (grace period) from used minutes
 * 2. Round result to nearest 15-minute block (ceiling)
 * 3. Minimum billable time is 0 (within tolerance = free)
 *
 * Examples (tolerance = 5 minutes):
 * - Used 4 min  → Billable 0 min   (within tolerance)
 * - Used 68 min → Billable 60 min  (68-5=63 → rounds down to 60)
 * - Used 19 min → Billable 15 min  (19-5=14 → rounds up to 15)
 * - Used 21 min → Billable 30 min  (21-5=16 → rounds up to 30)
 *
 * @author Jetski Team
 * @since 0.7.0
 */
@Slf4j
@Service
public class LocacaoCalculatorService {

    /**
     * Rounding block size in minutes (always 15 minutes per spec)
     */
    private static final int ROUNDING_BLOCK_MINUTES = 15;

    /**
     * Calculate billable minutes applying tolerance and rounding (RN01)
     *
     * Algorithm:
     * 1. minutesAfterTolerance = max(0, usedMinutes - toleranceMinutes)
     * 2. billableMinutes = ceil(minutesAfterTolerance / 15) * 15
     *
     * @param usedMinutes Total minutes the jetski was used
     * @param toleranceMinutes Grace period minutes (free, not charged)
     * @return Billable minutes after tolerance and rounding
     */
    public int calculateBillableMinutes(int usedMinutes, int toleranceMinutes) {
        log.debug("Calculating billable minutes: used={}, tolerance={}", usedMinutes, toleranceMinutes);

        // Step 1: Apply tolerance (grace period)
        int minutesAfterTolerance = Math.max(0, usedMinutes - toleranceMinutes);

        if (minutesAfterTolerance == 0) {
            log.debug("Usage within tolerance: billable=0");
            return 0;
        }

        // Step 2: Round to nearest 15-minute block (ceiling)
        int billableMinutes = roundToNearestBlock(minutesAfterTolerance);

        log.debug("Billable minutes calculated: used={}, after_tolerance={}, billable={}",
                  usedMinutes, minutesAfterTolerance, billableMinutes);

        return billableMinutes;
    }

    /**
     * Calculate used minutes from hourmeter readings
     *
     * Formula: used_minutes = (horimetroFim - horimetroInicio) * 60
     *
     * @param horimetroInicio Hourmeter reading at check-in (e.g., 100.5)
     * @param horimetroFim Hourmeter reading at check-out (e.g., 101.5)
     * @return Minutes used (e.g., 60 minutes)
     */
    public int calculateUsedMinutes(BigDecimal horimetroInicio, BigDecimal horimetroFim) {
        BigDecimal hoursUsed = horimetroFim.subtract(horimetroInicio);
        BigDecimal minutesUsed = hoursUsed.multiply(BigDecimal.valueOf(60));

        // Round to nearest integer (half-up)
        int minutes = minutesUsed.setScale(0, RoundingMode.HALF_UP).intValue();

        log.debug("Used minutes calculated: horimetro_ini={}, horimetro_fim={}, hours={}, minutes={}",
                  horimetroInicio, horimetroFim, hoursUsed, minutes);

        return minutes;
    }

    /**
     * Calculate base rental value from billable minutes
     *
     * Formula: value = (billableMinutes / 60.0) * pricePerHour
     *
     * @param billableMinutes Minutes to be charged
     * @param pricePerHour Price per hour for the modelo
     * @return Base rental value (before taxes/discounts)
     */
    public BigDecimal calculateBaseValue(int billableMinutes, BigDecimal pricePerHour) {
        if (billableMinutes == 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal hours = BigDecimal.valueOf(billableMinutes)
                                     .divide(BigDecimal.valueOf(60), 4, RoundingMode.HALF_UP);

        BigDecimal baseValue = hours.multiply(pricePerHour)
                                   .setScale(2, RoundingMode.HALF_UP);

        log.debug("Base value calculated: billable_minutes={}, price_per_hour={}, hours={}, value={}",
                  billableMinutes, pricePerHour, hours, baseValue);

        return baseValue;
    }

    /**
     * Round minutes to nearest 15-minute block (ceiling)
     *
     * Examples:
     * - 1-15 min   → 15 min
     * - 16-30 min  → 30 min
     * - 31-45 min  → 45 min
     * - 46-60 min  → 60 min
     * - 61-75 min  → 75 min
     *
     * @param minutes Minutes to round
     * @return Rounded minutes (always multiple of 15)
     */
    private int roundToNearestBlock(int minutes) {
        if (minutes <= 0) {
            return 0;
        }

        // Ceiling division: ceil(minutes / block) * block
        int blocks = (int) Math.ceil((double) minutes / ROUNDING_BLOCK_MINUTES);
        return blocks * ROUNDING_BLOCK_MINUTES;
    }

    /**
     * Validate that horimetro readings are consistent
     *
     * @param horimetroInicio Check-in reading
     * @param horimetroFim Check-out reading
     * @throws IllegalArgumentException if parameters are null or horimetroFim < horimetroInicio
     */
    public void validateHorimetroReadings(BigDecimal horimetroInicio, BigDecimal horimetroFim) {
        if (horimetroInicio == null) {
            throw new IllegalArgumentException("Horímetro inicial não pode ser nulo");
        }
        if (horimetroFim == null) {
            throw new IllegalArgumentException("Horímetro final não pode ser nulo");
        }
        if (horimetroFim.compareTo(horimetroInicio) < 0) {
            throw new IllegalArgumentException(
                String.format("Horímetro final (%.2f) não pode ser menor que inicial (%.2f)",
                              horimetroFim, horimetroInicio)
            );
        }
    }
}
