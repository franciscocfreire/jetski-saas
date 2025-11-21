package com.jetski.manutencao.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO Request: Finalizar OS Manutenção
 *
 * @author Jetski Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Requisição para finalizar uma ordem de serviço de manutenção")
public class OSManutencaoFinishRequest {

    @NotNull(message = "Horímetro de fechamento é obrigatório")
    @Schema(description = "Horímetro do jetski no fechamento da OS", example = "126.5", required = true)
    private BigDecimal horimetroFechamento;

    @PositiveOrZero(message = "Valor das peças deve ser maior ou igual a zero")
    @Schema(description = "Valor total das peças utilizadas", example = "250.00")
    @Builder.Default
    private BigDecimal valorPecas = BigDecimal.ZERO;

    @PositiveOrZero(message = "Valor da mão de obra deve ser maior ou igual a zero")
    @Schema(description = "Valor da mão de obra", example = "150.00")
    @Builder.Default
    private BigDecimal valorMaoObra = BigDecimal.ZERO;

    @Schema(description = "Observações finais sobre o serviço realizado", example = "Serviço concluído com sucesso. Próxima revisão em 50h.")
    private String observacoesFinais;
}
