package com.jetski.pagamentos.domain;

/**
 * Enum: TipoPagamento (Payment Type)
 *
 * Represents the payment method used for seller payments.
 *
 * @author Jetski Team
 * @since 0.13.0
 */
public enum TipoPagamento {
    /**
     * Payment via PIX (Brazilian instant payment system)
     */
    PIX,

    /**
     * Cash payment
     */
    DINHEIRO
}
