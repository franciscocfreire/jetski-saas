package com.jetski.tenant.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO: ComissaoConfigRequest
 *
 * Request to update tenant commission and bonus configuration.
 *
 * @author Jetski Team
 * @since 0.12.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComissaoConfigRequest {

    @NotNull(message = "Percentual padrão é obrigatório")
    @DecimalMin(value = "0.0", message = "Percentual deve ser >= 0")
    @DecimalMax(value = "100.0", message = "Percentual deve ser <= 100")
    private BigDecimal percentualPadrao;

    @NotNull(message = "Percentual abaixo base é obrigatório")
    @DecimalMin(value = "0.0", message = "Percentual deve ser >= 0")
    @DecimalMax(value = "100.0", message = "Percentual deve ser <= 100")
    private BigDecimal percentualAbaixoBase;

    @NotNull(message = "Flag de bônus ativo é obrigatória")
    private Boolean bonusAtivo;

    @Min(value = 1, message = "Meta deve ser >= 1")
    private Integer bonusMetaVendas;

    @DecimalMin(value = "0.0", message = "Valor do bônus deve ser >= 0")
    private BigDecimal bonusValor;
}
