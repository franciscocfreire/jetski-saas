package com.jetski.locacoes.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO: enviar o PIX copia-e-cola da cobrança do balcão por e-mail ao cliente.
 * O valor é o efetivamente cobrado (digitado no passo de Pagamento).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnviarPixEmailRequest {

    @NotNull(message = "Valor é obrigatório")
    @DecimalMin(value = "0.01", message = "Valor deve ser maior que zero")
    private BigDecimal valor;
}
