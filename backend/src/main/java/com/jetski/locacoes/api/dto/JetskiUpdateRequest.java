package com.jetski.locacoes.api.dto;

import com.jetski.locacoes.domain.JetskiStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO: Update Jetski Request
 *
 * Request to update an existing jetski unit.
 * All fields are optional - only provided fields will be updated.
 *
 * @author Jetski Team
 * @since 0.2.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JetskiUpdateRequest {

    @Size(min = 1, max = 255, message = "Série deve ter entre 1 e 255 caracteres")
    private String serie;

    @Min(value = 1900, message = "Ano inválido")
    private Integer ano;

    @DecimalMin(value = "0.00", message = "Horímetro não pode ser negativo")
    private BigDecimal horimetroAtual;

    private JetskiStatus status;
}
