package com.jetski.locacoes.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO: LocacaoItemOpcionalResponse
 *
 * Response containing optional item attached to a rental.
 *
 * @author Jetski Team
 * @since 0.8.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocacaoItemOpcionalResponse {

    private UUID id;
    private UUID locacaoId;
    private UUID itemOpcionalId;

    /**
     * Item name (from catalog, for display)
     */
    private String itemNome;

    /**
     * Actual price charged
     */
    private BigDecimal valorCobrado;

    /**
     * Original catalog price (for reference)
     */
    private BigDecimal valorOriginal;

    /**
     * Optional note
     */
    private String observacao;

    /**
     * Whether the price was negotiated (differs from original)
     */
    private Boolean negociado;

    private Instant createdAt;
}
