package com.jetski.dashboard.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO for the monthly financial calendar view.
 * Contains summary stats and daily data for all days of the month.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CalendarioFinanceiroResponse {

    private Integer ano;
    private Integer mes;

    // Summary stats for the month
    private BigDecimal totalReceitas;
    private BigDecimal totalDespesas;
    private BigDecimal saldoMes;
    private Integer diasPositivos;
    private Integer diasNegativos;
    private Integer diasNeutros;

    // Daily data
    private List<DiaFinanceiroResponse> dias;
}
