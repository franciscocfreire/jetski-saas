package com.jetski.locacoes.domain;

/**
 * Enum: JetskiStatus
 *
 * Represents the operational status of a jetski unit.
 *
 * Status transitions:
 * - DISPONIVEL → LOCADO (when rental check-in occurs)
 * - LOCADO → DISPONIVEL (when rental check-out occurs)
 * - DISPONIVEL → MANUTENCAO (when maintenance order is opened)
 * - MANUTENCAO → DISPONIVEL (when maintenance order is closed)
 * - * → INDISPONIVEL (administrative block)
 *
 * Business Rules:
 * - RN06: Jetski in MANUTENCAO status cannot be reserved
 * - Only DISPONIVEL jetskis can be reserved/rented
 *
 * @author Jetski Team
 * @since 0.2.0
 */
public enum JetskiStatus {
    /**
     * Available for rental - can be reserved and rented
     */
    DISPONIVEL,

    /**
     * Currently rented - blocked until check-out
     */
    LOCADO,

    /**
     * Under maintenance - blocked until maintenance order closes (RN06)
     */
    MANUTENCAO,

    /**
     * Unavailable - administrative block (damage, retired, etc.)
     */
    INDISPONIVEL
}
