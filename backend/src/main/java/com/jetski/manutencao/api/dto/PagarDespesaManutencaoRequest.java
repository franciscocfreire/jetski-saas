package com.jetski.manutencao.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for paying a maintenance expense.
 *
 * @author Jetski Team
 * @since 0.9.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PagarDespesaManutencaoRequest {

    /**
     * Referencia do pagamento (transferencia, recibo, etc)
     */
    private String referenciaPagamento;
}
