package com.jetski.combustivel.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO: FuelPolicyUpdateRequest
 *
 * Request to update an existing fuel charging policy.
 *
 * @author Jetski Team
 * @since 0.8.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FuelPolicyUpdateRequest {

    @Size(max = 100, message = "Nome deve ter no máximo 100 caracteres")
    private String nome;

    @DecimalMin(value = "0.00", message = "Valor da taxa deve ser não-negativo")
    private BigDecimal valorTaxaPorHora;

    private Boolean comissionavel;

    private Boolean ativo;

    private Integer prioridade;

    @Size(max = 500, message = "Descrição deve ter no máximo 500 caracteres")
    private String descricao;
}
