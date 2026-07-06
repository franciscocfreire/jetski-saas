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
 * DTO: registrar recebimento no folio da LOCAÇÃO — acerto do check-out
 * (saldo de combustível/hora extra) ou pagamento integral de walk-in.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegistrarPagamentoLocacaoRequest {

    /** DINHEIRO, PIX, CARTAO_CREDITO, CARTAO_DEBITO ou OUTRO. */
    @NotBlank(message = "Forma de pagamento é obrigatória")
    private String forma;

    @NotNull(message = "Valor é obrigatório")
    @DecimalMin(value = "0.01", message = "Valor deve ser maior que zero")
    private BigDecimal valor;

    private String observacao;
}
