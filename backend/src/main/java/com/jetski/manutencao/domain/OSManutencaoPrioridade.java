package com.jetski.manutencao.domain;

/**
 * Enum: OS Manutenção Prioridade
 *
 * <p>Priority level for maintenance orders.
 *
 * <ul>
 *   <li><b>URGENTE</b>: Immediate action required (jetski unsafe)</li>
 *   <li><b>ALTA</b>: High priority (affects operations)</li>
 *   <li><b>MEDIA</b>: Medium priority (default)</li>
 *   <li><b>BAIXA</b>: Low priority (can wait)</li>
 * </ul>
 *
 * @author Jetski Team
 * @since 1.0.0
 */
public enum OSManutencaoPrioridade {
    /**
     * Prioridade baixa (pode aguardar).
     */
    BAIXA("baixa"),

    /**
     * Prioridade média (padrão).
     */
    MEDIA("media"),

    /**
     * Prioridade alta (afeta operações).
     */
    ALTA("alta"),

    /**
     * Prioridade urgente (jetski inseguro).
     */
    URGENTE("urgente");

    private final String value;

    OSManutencaoPrioridade(String value) {
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
    public static OSManutencaoPrioridade fromValue(String value) {
        for (OSManutencaoPrioridade prioridade : values()) {
            if (prioridade.value.equals(value)) {
                return prioridade;
            }
        }
        throw new IllegalArgumentException("Invalid OSManutencaoPrioridade: " + value);
    }
}
