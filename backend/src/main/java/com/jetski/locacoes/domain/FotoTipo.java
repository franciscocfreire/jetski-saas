package com.jetski.locacoes.domain;

/**
 * Enum: FotoTipo
 *
 * Types of photos in the jetski rental system:
 * - CHECK_IN: Photos taken during check-in (jetski condition before rental)
 * - CHECK_OUT: Photos taken during check-out (jetski condition after rental)
 * - INCIDENTE: Photos of damage or incidents during rental
 * - MANUTENCAO: Photos taken during maintenance operations
 *
 * @author Jetski Team
 * @since 0.7.0
 */
public enum FotoTipo {
    /**
     * Check-in photo (jetski condition before rental)
     * Mandatory: Yes (usually 4 photos: front, back, left, right)
     */
    CHECK_IN,

    /**
     * Check-out photo (jetski condition after rental)
     * Mandatory: Yes (same angles as check-in for comparison)
     */
    CHECK_OUT,

    /**
     * Incident photo (damage, collision, breakdown during rental)
     * Mandatory: No (only if incident occurs)
     */
    INCIDENTE,

    /**
     * Maintenance photo (repairs, inspections, part replacement)
     * Mandatory: No (for maintenance tracking)
     */
    MANUTENCAO
}
