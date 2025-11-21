package com.jetski.manutencao.domain;

/**
 * Enum: OS Manutenção Tipo
 *
 * <p>Type of maintenance order.
 *
 * <ul>
 *   <li><b>PREVENTIVA</b>: Scheduled preventive maintenance (e.g., every 50h)</li>
 *   <li><b>CORRETIVA</b>: Corrective maintenance due to failure/incident</li>
 *   <li><b>REVISAO</b>: General inspection/review</li>
 * </ul>
 *
 * @author Jetski Team
 * @since 1.0.0
 */
public enum OSManutencaoTipo {
    /**
     * Manutenção preventiva programada.
     */
    PREVENTIVA("preventiva"),

    /**
     * Manutenção corretiva (quebra/falha).
     */
    CORRETIVA("corretiva"),

    /**
     * Revisão geral periódica.
     */
    REVISAO("revisao");

    private final String value;

    OSManutencaoTipo(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    /**
     * Parse from database string value.
     *
     * @param value Database column value
     * @return Corresponding enum
     * @throws IllegalArgumentException if value is invalid
     */
    public static OSManutencaoTipo fromValue(String value) {
        for (OSManutencaoTipo tipo : values()) {
            if (tipo.value.equals(value)) {
                return tipo;
            }
        }
        throw new IllegalArgumentException("Invalid OSManutencaoTipo: " + value);
    }
}
