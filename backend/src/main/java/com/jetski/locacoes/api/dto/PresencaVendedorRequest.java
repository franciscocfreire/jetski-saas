package com.jetski.locacoes.api.dto;

import com.jetski.locacoes.domain.TipoPresenca;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request DTO para registro de presença de um vendedor.
 *
 * @author Jetski Team
 * @since 0.10.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PresencaVendedorRequest {

    @NotNull(message = "vendedorId é obrigatório")
    private UUID vendedorId;

    @NotNull(message = "tipo é obrigatório")
    private TipoPresenca tipo;

    /**
     * Valor ajustado manualmente (opcional).
     * Se informado, substitui o valor calculado.
     */
    private BigDecimal valorAjustado;

    /**
     * Motivo do ajuste (obrigatório se valorAjustado != null).
     */
    private String motivoAjuste;
}
