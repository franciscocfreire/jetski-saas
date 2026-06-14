package com.jetski.bonus.domain;

/**
 * Enum: Bonus Status
 *
 * Workflow: PENDENTE -> APROVADO -> PAGO (or CANCELADO)
 *
 * @author Jetski Team
 * @since 0.8.0
 */
public enum StatusBonus {
    /**
     * Bonus created, waiting for manager approval
     */
    PENDENTE,

    /**
     * Bonus approved by manager, waiting for payment
     */
    APROVADO,

    /**
     * Bonus paid to seller
     */
    PAGO,

    /**
     * Bonus cancelled (invalid, duplicate, etc.)
     */
    CANCELADO
}
