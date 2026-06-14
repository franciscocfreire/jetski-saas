package com.jetski.manutencao.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO para rejeitar uma DespesaManutencao.
 *
 * @author Jetski Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RejeitarDespesaManutencaoRequest {

    @NotBlank(message = "Motivo da rejeicao e obrigatorio")
    @Size(max = 500, message = "Motivo deve ter no maximo 500 caracteres")
    private String motivo;
}
