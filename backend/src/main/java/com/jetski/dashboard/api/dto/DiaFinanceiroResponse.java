package com.jetski.dashboard.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO for daily financial data in the calendar view.
 * Shows summary of the day with indicator for positive/negative balance.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiaFinanceiroResponse {

    private LocalDate data;

    // Receitas
    private BigDecimal receita;

    // Despesas
    private BigDecimal despesasOperacionais;
    private BigDecimal combustivel;
    private BigDecimal comissoes;
    private BigDecimal diariasVendedores;
    private BigDecimal manutencoes;

    // Totals
    private BigDecimal totalDespesas;
    private BigDecimal saldo;

    // Indicator for calendar coloring: POSITIVO, NEGATIVO, NEUTRO
    private String indicador;

    // Status do fechamento: aberto, fechado, aprovado
    private String statusFechamento;

    // Flag se tem fechamento consolidado
    private Boolean temFechamento;
}
