package com.jetski.despesas.domain;

/**
 * Status de uma despesa operacional no workflow de aprovacao.
 *
 * <p><strong>Fluxo de status:</strong></p>
 * <ol>
 *   <li>PENDENTE - Despesa registrada, aguardando aprovacao</li>
 *   <li>APROVADA - Gerente aprovou, pronta para pagamento</li>
 *   <li>PAGA - Financeiro registrou o pagamento</li>
 *   <li>REJEITADA - Despesa rejeitada (nao sera paga)</li>
 * </ol>
 *
 * @author Jetski Team
 * @since 0.9.0
 */
public enum StatusDespesa {

    /**
     * Despesa registrada, aguardando aprovacao do gerente
     */
    PENDENTE,

    /**
     * Aprovada pelo gerente, pronta para pagamento
     */
    APROVADA,

    /**
     * Rejeitada pelo gerente (nao sera paga)
     */
    REJEITADA,

    /**
     * Pagamento realizado pelo financeiro
     */
    PAGA,

    /**
     * Despesa cancelada (nao sera mais processada)
     */
    CANCELADA
}
