package com.jetski.frota.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * DTO: Dashboard Revenue Metrics
 *
 * Cached metrics for dashboard display with calendar month calculation.
 *
 * <p><strong>Caching Strategy:</strong>
 * - Cached in Redis with key: "dashboard:metrics:{tenantId}:{date}"
 * - Invalidated on rental completion (RentalCompletedEvent)
 * - TTL fallback: 5 minutes
 *
 * @author Jetski Team
 * @since 0.9.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardMetrics implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Revenue from finalized rentals TODAY (check-out date = today)
     */
    private BigDecimal receitaHoje;

    /**
     * Revenue from finalized rentals in the current CALENDAR MONTH
     * (from day 1 of current month to today)
     */
    private BigDecimal receitaMes;

    /**
     * Number of rentals finalized today
     */
    private Integer locacoesHoje;

    /**
     * Number of rentals finalized this calendar month
     */
    private Integer locacoesMes;

    /**
     * Reference date for the metrics
     */
    private LocalDate dataReferencia;

    /**
     * First day of the calendar month used for calculation
     */
    private LocalDate inicioMes;

    /**
     * Timestamp when metrics were calculated
     */
    private Instant calculatedAt;

    /**
     * Create empty metrics (when no data exists)
     */
    public static DashboardMetrics empty(LocalDate date) {
        return DashboardMetrics.builder()
                .receitaHoje(BigDecimal.ZERO)
                .receitaMes(BigDecimal.ZERO)
                .locacoesHoje(0)
                .locacoesMes(0)
                .dataReferencia(date)
                .inicioMes(date.withDayOfMonth(1))
                .calculatedAt(Instant.now())
                .build();
    }
}
