package com.jetski.combustivel.api.dto;

import com.jetski.combustivel.domain.FuelChargeMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO: FuelCostCalculationResponse
 *
 * Response containing calculated fuel cost for a rental.
 *
 * @author Jetski Team
 * @since 0.8.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FuelCostCalculationResponse {

    private BigDecimal custoTotal;
    private FuelChargeMode modoCobranca;
    private String descricaoCalculo;

    // Detalhes do cálculo
    private BigDecimal litrosConsumidos;
    private BigDecimal precoLitro;
    private BigDecimal horasFaturaveis;
    private BigDecimal valorTaxaPorHora;
}
