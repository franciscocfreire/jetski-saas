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
 * DTO: registrar pagamento presencial INTEGRAL da reserva de balcão.
 *
 * <p>O valor informado é o efetivamente cobrado (pode diferir do estimado —
 * desconto de balcão) e vira o {@code valorTotal} da reserva. A forma
 * alimenta o ledger {@code reserva_lancamento} (fechamento por forma na fase 3).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegistrarPagamentoPresencialRequest {

    /** DINHEIRO, PIX, CARTAO_CREDITO, CARTAO_DEBITO ou OUTRO. */
    @NotBlank(message = "Forma de pagamento é obrigatória")
    private String forma;

    @NotNull(message = "Valor é obrigatório")
    @DecimalMin(value = "0.01", message = "Valor deve ser maior que zero")
    private BigDecimal valor;

    private String observacao;
}
