package com.jetski.locacoes.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Response DTO com resumo das diárias de um dia.
 * Inclui totais e lista de detalhes por vendedor.
 *
 * @author Jetski Team
 * @since 0.10.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumoDiariasResponse {

    private LocalDate dtReferencia;

    /**
     * Total de vendedores que trabalharam no dia
     */
    private int totalVendedoresPresentes;

    /**
     * Quantidade com diária integral (100%)
     */
    private int totalIntegral;

    /**
     * Quantidade com meia-diária (50%)
     */
    private int totalMeiaDiaria;

    /**
     * Soma total das diárias a pagar
     */
    private BigDecimal totalDiarias;

    /**
     * Lista detalhada de presenças por vendedor
     */
    private List<PresencaVendedorResponse> detalhes;
}
