package com.jetski.locacoes.api.dto;

import com.jetski.locacoes.domain.JetskiStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * DTO: Create Jetski Request
 *
 * Request to register a new jetski unit in the fleet.
 *
 * @author Jetski Team
 * @since 0.2.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JetskiCreateRequest {

    @NotNull(message = "Modelo é obrigatório")
    private UUID modeloId;

    @NotBlank(message = "Número de série é obrigatório")
    @Size(min = 1, max = 255, message = "Série deve ter entre 1 e 255 caracteres")
    private String serie;

    @Min(value = 1900, message = "Ano inválido")
    private Integer ano;

    @DecimalMin(value = "0.00", message = "Horímetro não pode ser negativo")
    private BigDecimal horimetroAtual;

    private JetskiStatus status;
}
