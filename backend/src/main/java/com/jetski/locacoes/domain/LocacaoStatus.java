package com.jetski.locacoes.domain;

/**
 * Enum: LocacaoStatus
 *
 * Status lifecycle for rental operations:
 * - EM_CURSO: Active rental (checked in, not yet checked out)
 * - FINALIZADA: Rental completed (checked out, value calculated)
 * - CANCELADA: Rental cancelled before checkout
 *
 * @author Jetski Team
 * @since 0.7.0
 */
public enum LocacaoStatus {
    /**
     * Rental in progress (checked in, customer using jetski)
     */
    EM_CURSO,

    /**
     * Rental completed (checked out, value calculated and confirmed)
     */
    FINALIZADA,

    /**
     * Rental cancelled before completion
     */
    CANCELADA
}
