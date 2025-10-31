package com.jetski.comissoes.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for marking a commission as paid (FINANCEIRO action)
 *
 * @author Jetski Team
 * @since 0.7.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PagarComissaoRequest {

    @NotBlank(message = "Referência de pagamento é obrigatória")
    @Size(max = 100, message = "Referência deve ter no máximo 100 caracteres")
    private String referenciaPagamento;

    // The pagoPor will come from the authenticated user context
}
