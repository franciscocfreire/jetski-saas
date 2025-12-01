package com.jetski.locacoes.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * DTO: LocacaoItemOpcionalRequest
 *
 * Request to add or update an optional item in a rental.
 *
 * @author Jetski Team
 * @since 0.8.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocacaoItemOpcionalRequest {

    /**
     * Optional item ID from catalog
     */
    @NotNull(message = "Item opcional ID é obrigatório")
    private UUID itemOpcionalId;

    /**
     * Price to charge (optional, defaults to catalog base price)
     */
    @DecimalMin(value = "0.00", message = "Valor cobrado não pode ser negativo")
    private BigDecimal valorCobrado;

    /**
     * Optional note (e.g., reason for price adjustment)
     */
    private String observacao;
}
