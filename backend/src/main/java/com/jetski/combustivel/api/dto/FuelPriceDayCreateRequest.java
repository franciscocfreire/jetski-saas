package com.jetski.combustivel.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO: FuelPriceDayCreateRequest
 *
 * Request to manually set daily fuel price (admin override).
 *
 * @author Jetski Team
 * @since 0.8.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FuelPriceDayCreateRequest {

    @NotNull(message = "Data é obrigatória")
    private LocalDate data;

    @NotNull(message = "Preço médio por litro é obrigatório")
    @DecimalMin(value = "0.01", message = "Preço deve ser maior que zero")
    private BigDecimal precoMedioLitro;

    private BigDecimal totalLitrosAbastecidos;

    private BigDecimal totalCusto;

    private Integer qtdAbastecimentos;
}
