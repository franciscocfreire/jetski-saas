package com.jetski.fechamento.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for consolidating a specific month
 *
 * @author Jetski Team
 * @since 0.8.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsolidarMesRequest {

    @NotNull(message = "Ano é obrigatório")
    @Min(value = 2020, message = "Ano deve ser >= 2020")
    private Integer ano;

    @NotNull(message = "Mês é obrigatório")
    @Min(value = 1, message = "Mês deve estar entre 1 e 12")
    @Max(value = 12, message = "Mês deve estar entre 1 e 12")
    private Integer mes;
}
