package com.jetski.locacoes.domain;

/**
 * Enum: FotoTipo
 *
 * Types of photos in the jetski rental system.
 *
 * Business Rules:
 * - Check-in requires 4 mandatory photos: FRENTE, LATERAL_ESQ, LATERAL_DIR, HORIMETRO
 * - Check-out requires 4 mandatory photos: FRENTE, LATERAL_ESQ, LATERAL_DIR, HORIMETRO
 * - Each photo type ensures complete documentation of jetski condition
 *
 * @author Jetski Team
 * @since 0.7.0
 */
public enum FotoTipo {
    /**
     * Check-in: Front view of jetski
     * Mandatory: Yes
     */
    CHECKIN_FRENTE,

    /**
     * Check-in: Left side view of jetski
     * Mandatory: Yes
     */
    CHECKIN_LATERAL_ESQ,

    /**
     * Check-in: Right side view of jetski
     * Mandatory: Yes
     */
    CHECKIN_LATERAL_DIR,

    /**
     * Check-in: Hour meter reading
     * Mandatory: Yes
     */
    CHECKIN_HORIMETRO,

    /**
     * Check-out: Front view of jetski
     * Mandatory: Yes
     */
    CHECKOUT_FRENTE,

    /**
     * Check-out: Left side view of jetski
     * Mandatory: Yes
     */
    CHECKOUT_LATERAL_ESQ,

    /**
     * Check-out: Right side view of jetski
     * Mandatory: Yes
     */
    CHECKOUT_LATERAL_DIR,

    /**
     * Check-out: Hour meter reading
     * Mandatory: Yes
     */
    CHECKOUT_HORIMETRO,

    /**
     * Incident photo (damage, collision, breakdown during rental)
     * Mandatory: No (only if incident occurs)
     */
    INCIDENTE,

    /**
     * Maintenance photo (repairs, inspections, part replacement)
     * Mandatory: No (for maintenance tracking)
     */
    MANUTENCAO;

    /**
     * Check if photo is check-in type
     */
    public boolean isCheckIn() {
        return this == CHECKIN_FRENTE || this == CHECKIN_LATERAL_ESQ ||
               this == CHECKIN_LATERAL_DIR || this == CHECKIN_HORIMETRO;
    }

    /**
     * Check if photo is check-out type
     */
    public boolean isCheckOut() {
        return this == CHECKOUT_FRENTE || this == CHECKOUT_LATERAL_ESQ ||
               this == CHECKOUT_LATERAL_DIR || this == CHECKOUT_HORIMETRO;
    }

    /**
     * Check if photo is incident type
     */
    public boolean isIncidente() {
        return this == INCIDENTE;
    }

    /**
     * Check if photo is maintenance type
     */
    public boolean isManutencao() {
        return this == MANUTENCAO;
    }
}
