package com.jetski.combustivel.domain;

/**
 * Tipo de abastecimento.
 *
 * Define quando o abastecimento foi realizado:
 * - PRE_LOCACAO: antes de iniciar a locação (tanque cheio)
 * - POS_LOCACAO: após encerrar a locação (reabastecimento)
 * - FROTA: abastecimento de manutenção da frota (não vinculado a locação)
 */
public enum TipoAbastecimento {

    /**
     * Abastecimento antes de iniciar a locação.
     * Usado para medir consumo em políticas MEDIDO.
     */
    PRE_LOCACAO,

    /**
     * Abastecimento após encerrar a locação.
     * Usado para calcular litros consumidos em políticas MEDIDO.
     */
    POS_LOCACAO,

    /**
     * Abastecimento de manutenção da frota.
     * Não vinculado a uma locação específica.
     */
    FROTA;

    public boolean isPreLocacao() {
        return this == PRE_LOCACAO;
    }

    public boolean isPosLocacao() {
        return this == POS_LOCACAO;
    }

    public boolean isFrota() {
        return this == FROTA;
    }
}
