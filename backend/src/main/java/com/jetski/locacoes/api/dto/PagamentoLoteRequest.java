package com.jetski.locacoes.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO: Request for bulk commission payment
 *
 * @author Jetski Team
 * @since 0.8.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PagamentoLoteRequest {

    @NotBlank(message = "Referência de pagamento é obrigatória")
    private String referenciaPagamento;

    private String observacao;
}
