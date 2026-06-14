package com.jetski.pagamentos.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO: ItemPendente
 *
 * Represents a single pending payment item (commission, daily allowance, or bonus).
 * Used for partial payment selection in the payment dialog.
 *
 * @author Jetski Team
 * @since 0.13.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItemPendente {

    /**
     * ID of the item (comissao.id, presenca.id, or bonus.id)
     */
    private UUID id;

    /**
     * Type of the item: COMISSAO, DIARIA, or BONUS
     */
    private String tipo;

    /**
     * Reference date for the item
     */
    private LocalDate dataReferencia;

    /**
     * Human-readable description
     */
    private String descricao;

    /**
     * Value of this item
     */
    private BigDecimal valor;
}
