package com.jetski.locacoes.domain;

/**
 * Enum: ModalidadePreco
 *
 * Pricing modes for rental operations:
 * - PRECO_FECHADO: Fixed price (uses valorNegociado or calculated from hourly rate)
 * - DIARIA: Full day rental price
 * - MEIA_DIARIA: Half day rental price
 *
 * @author Jetski Team
 * @since 0.8.0
 */
public enum ModalidadePreco {
    /**
     * Fixed price mode (default).
     * Uses valorNegociado if set, otherwise calculates from hourly rate.
     */
    PRECO_FECHADO,

    /**
     * Full day rental.
     * Uses model's daily rate (configured in pacotesJson or precoBaseHora * 8).
     */
    DIARIA,

    /**
     * Half day rental.
     * Uses model's half-day rate (configured in pacotesJson or precoBaseHora * 4).
     */
    MEIA_DIARIA
}
