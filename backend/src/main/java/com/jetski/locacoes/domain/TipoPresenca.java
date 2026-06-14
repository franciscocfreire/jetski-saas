package com.jetski.locacoes.domain;

/**
 * Tipo de presença do vendedor no dia.
 *
 * <ul>
 *   <li>INTEGRAL - Diária completa (100% do valor base)</li>
 *   <li>MEIA_DIARIA - Meia diária (50% do valor base) - ex: dia de chuva</li>
 * </ul>
 *
 * @author Jetski Team
 * @since 0.10.0
 */
public enum TipoPresenca {

    INTEGRAL(1.0, "Integral"),
    MEIA_DIARIA(0.5, "Meia Diária");

    private final double fator;
    private final String descricao;

    TipoPresenca(double fator, String descricao) {
        this.fator = fator;
        this.descricao = descricao;
    }

    /**
     * Fator multiplicador para cálculo da diária.
     * INTEGRAL = 1.0 (100%), MEIA_DIARIA = 0.5 (50%)
     */
    public double getFator() {
        return fator;
    }

    public String getDescricao() {
        return descricao;
    }
}
