package com.jetski.locacoes.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO: registrar estorno (devolução ao cliente) de reserva paga.
 * Fato de caixa manual — a observação (justificativa) é obrigatória.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegistrarEstornoRequest {

    /** DINHEIRO, PIX, CARTAO_CREDITO, CARTAO_DEBITO ou OUTRO. */
    @NotBlank(message = "Forma da devolução é obrigatória")
    private String forma;

    @NotNull(message = "Valor é obrigatório")
    @DecimalMin(value = "0.01", message = "Valor deve ser maior que zero")
    private BigDecimal valor;

    @NotBlank(message = "Observação (justificativa) é obrigatória")
    private String observacao;
}
