package com.jetski.despesas.api.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for marking a DespesaOperacional as paid
 *
 * @author Jetski Team
 * @since 0.9.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PagarDespesaRequest {

    @Size(max = 100, message = "Referencia de pagamento deve ter no maximo 100 caracteres")
    private String referenciaPagamento;
}
