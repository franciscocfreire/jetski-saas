package com.jetski.manutencao.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO Response: Disponibilidade do Jetski
 *
 * @author Jetski Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Resposta sobre disponibilidade do jetski para locação")
public class JetskiDisponibilidadeResponse {

    @Schema(
        description = "Se o jetski está disponível para locação (sem OS ativa bloqueando)",
        example = "true",
        required = true
    )
    private Boolean disponivel;

    @Schema(
        description = "Motivo da indisponibilidade (null se disponível)",
        example = "Jetski em manutenção preventiva",
        nullable = true
    )
    private String motivo;
}
