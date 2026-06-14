package com.jetski.despesas.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for rejecting a DespesaOperacional
 *
 * @author Jetski Team
 * @since 0.9.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RejeitarDespesaRequest {

    @NotBlank(message = "Motivo da rejeicao e obrigatorio")
    @Size(max = 500, message = "Motivo deve ter no maximo 500 caracteres")
    private String motivo;
}
