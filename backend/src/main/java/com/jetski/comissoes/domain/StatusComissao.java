package com.jetski.comissoes.domain;

/**
 * Status do cálculo/pagamento de comissão
 *
 * @author Jetski Team
 * @since 0.7.0
 */
public enum StatusComissao {
    /**
     * Comissão calculada, aguardando aprovação do gerente
     */
    PENDENTE,

    /**
     * Comissão aprovada pelo gerente, aguardando pagamento
     */
    APROVADA,

    /**
     * Comissão paga ao vendedor
     */
    PAGA,

    /**
     * Comissão cancelada (locação cancelada/estornada)
     */
    CANCELADA
}
