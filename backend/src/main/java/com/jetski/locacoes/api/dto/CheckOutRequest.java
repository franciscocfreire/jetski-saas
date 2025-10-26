package com.jetski.locacoes.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO: CheckOutRequest
 *
 * Request to perform check-out and complete rental.
 *
 * @author Jetski Team
 * @since 0.7.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckOutRequest {

    /**
     * Hourmeter reading at check-out (e.g., 102.0 hours)
     * Must be >= horimetroInicio
     */
    @NotNull(message = "Horímetro final é obrigatório")
    @DecimalMin(value = "0.0", message = "Horímetro deve ser positivo")
    private BigDecimal horimetroFim;

    /**
     * Optional notes for check-out
     */
    private String observacoes;
}
