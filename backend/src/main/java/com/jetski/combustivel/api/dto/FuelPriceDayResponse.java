package com.jetski.combustivel.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO: FuelPriceDayResponse
 *
 * Response containing daily fuel price details.
 *
 * @author Jetski Team
 * @since 0.8.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FuelPriceDayResponse {

    private Long id;
    private UUID tenantId;
    private LocalDate data;

    private BigDecimal precoMedioLitro;
    private BigDecimal totalLitrosAbastecidos;
    private BigDecimal totalCusto;
    private Integer qtdAbastecimentos;

    private Instant createdAt;
    private Instant updatedAt;
}
