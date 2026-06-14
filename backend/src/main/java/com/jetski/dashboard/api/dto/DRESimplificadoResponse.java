package com.jetski.dashboard.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO for simplified DRE (Demonstração do Resultado do Exercício).
 * Income Statement for a given month.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DRESimplificadoResponse {

    private Integer ano;
    private Integer mes;

    // (+) Receita Bruta (total faturado das locações)
    private BigDecimal receitaBruta;

    // (-) Deduções (taxas, descontos, cancelamentos)
    private BigDecimal deducoes;

    // (=) Receita Líquida
    private BigDecimal receitaLiquida;

    // (-) Custos Variáveis
    private BigDecimal combustivel;
    private BigDecimal comissoes;
    private BigDecimal totalCustosVariaveis;

    // (=) Lucro Bruto (Margem de Contribuição)
    private BigDecimal lucroBruto;

    // (-) Despesas Operacionais
    private BigDecimal despesasDiarias;       // diárias de funcionários (DespesaOperacional)
    private BigDecimal diariasVendedores;     // diárias dos vendedores (presenca_vendedor)
    private BigDecimal despesasRefeicao;      // refeições
    private BigDecimal despesasTransporte;    // transporte
    private BigDecimal despesasLimpeza;       // limpeza
    private BigDecimal outrasDesepsas;        // outras despesas
    private BigDecimal totalDespesasOperacionais;

    // (-) Manutenções
    private BigDecimal manutencoes;

    // (=) Resultado Líquido (EBITDA simplificado)
    private BigDecimal resultadoLiquido;

    // Margem de lucro %
    private BigDecimal margemLiquida;
}
