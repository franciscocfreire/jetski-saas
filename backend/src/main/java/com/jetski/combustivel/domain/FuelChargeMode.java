package com.jetski.combustivel.domain;

/**
 * Modo de cobrança de combustível (RN03).
 *
 * Define como o combustível será cobrado do cliente:
 * - INCLUSO: já está incluído no preço/hora da locação (não cobra separadamente)
 * - MEDIDO: cobra pelos litros consumidos com base no preço do dia
 * - TAXA_FIXA: cobra um valor fixo por hora faturável
 */
public enum FuelChargeMode {

    /**
     * Combustível incluído no preço/hora.
     * Cliente não é cobrado separadamente, mas custo é rastreado para controle operacional.
     */
    INCLUSO,

    /**
     * Combustível cobrado por litros consumidos.
     * Custo = (litros_pós_locação - litros_pré_locação) × preço_médio_dia
     */
    MEDIDO,

    /**
     * Combustível cobrado como taxa fixa por hora.
     * Custo = valor_taxa_por_hora × horas_faturáveis
     */
    TAXA_FIXA;

    public boolean isIncluso() {
        return this == INCLUSO;
    }

    public boolean isMedido() {
        return this == MEDIDO;
    }

    public boolean isTaxaFixa() {
        return this == TAXA_FIXA;
    }
}
