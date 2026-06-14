package com.jetski.dashboard.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO for daily revenue vs expenses chart data.
 * Used for stacked bar chart visualization.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReceitaDespesaDiaResponse {

    private LocalDate data;

    // Receita (verde)
    private BigDecimal receita;

    // Breakdown de despesas para barras empilhadas
    private BigDecimal despesasOperacionais;  // vermelho
    private BigDecimal combustivel;            // laranja
    private BigDecimal comissoes;              // azul
    private BigDecimal diariasVendedores;      // roxo
    private BigDecimal manutencoes;            // marrom

    // Total para o tooltip
    private BigDecimal totalDespesas;
    private BigDecimal saldo;
}
