package com.jetski.manutencao.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Request DTO for generating maintenance expense installments.
 *
 * @author Jetski Team
 * @since 0.9.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GerarDespesaManutencaoRequest {

    /**
     * Numero de parcelas (1-12)
     */
    @NotNull(message = "Numero de parcelas e obrigatorio")
    @Min(value = 1, message = "Minimo de 1 parcela")
    @Max(value = 12, message = "Maximo de 12 parcelas")
    private Integer numeroParcelas;

    /**
     * Data do primeiro vencimento
     */
    @NotNull(message = "Data do primeiro vencimento e obrigatoria")
    private LocalDate primeiroVencimento;

    /**
     * Observacoes opcionais
     */
    private String observacoes;
}
