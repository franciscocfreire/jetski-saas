package com.jetski.combustivel.api.dto;

import com.jetski.combustivel.domain.FuelChargeMode;
import com.jetski.combustivel.domain.FuelPolicyType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * DTO: FuelPolicyCreateRequest
 *
 * Request to create a new fuel charging policy.
 *
 * @author Jetski Team
 * @since 0.8.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FuelPolicyCreateRequest {

    @NotBlank(message = "Nome da política é obrigatório")
    @Size(max = 100, message = "Nome deve ter no máximo 100 caracteres")
    private String nome;

    @NotNull(message = "Tipo de cobrança é obrigatório")
    private FuelChargeMode tipo;

    @NotNull(message = "Aplicável a (GLOBAL/MODELO/JETSKI) é obrigatório")
    private FuelPolicyType aplicavelA;

    private UUID referenciaId; // Obrigatório se aplicavelA != GLOBAL

    @DecimalMin(value = "0.00", message = "Valor da taxa deve ser não-negativo")
    private BigDecimal valorTaxaPorHora; // Obrigatório se tipo == TAXA_FIXA

    @Builder.Default
    private Boolean comissionavel = false;

    @Builder.Default
    private Boolean ativo = true;

    @Builder.Default
    private Integer prioridade = 0;

    @Size(max = 500, message = "Descrição deve ter no máximo 500 caracteres")
    private String descricao;
}
