package com.jetski.comissoes.domain;

/**
 * Tipo de cálculo de comissão
 *
 * <p>Baseado no CLAUDE.md: RN04 - Commission Calculation</p>
 *
 * @author Jetski Team
 * @since 0.7.0
 */
public enum TipoComissao {
    /**
     * Percentual sobre valor comissionável (ex: 10% do valor da locação)
     */
    PERCENTUAL,

    /**
     * Valor fixo por locação (ex: R$ 50,00 por locação)
     */
    VALOR_FIXO,

    /**
     * Escalonado por faixa de duração
     * Exemplo: 10% até 120min, 12% acima de 120min
     */
    ESCALONADO
}
