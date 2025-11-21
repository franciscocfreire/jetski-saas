package com.jetski.manutencao.domain;

/**
 * Enum: OS Manutenção Status
 *
 * <p>Status workflow for maintenance orders.
 *
 * <h3>Workflow</h3>
 * <pre>
 * ABERTA → EM_ANDAMENTO → [AGUARDANDO_PECAS] → CONCLUIDA
 *     ↓                                            ↑
 *     └──────────────→ CANCELADA ←────────────────┘
 * </pre>
 *
 * @author Jetski Team
 * @since 1.0.0
 */
public enum OSManutencaoStatus {
    /**
     * OS aberta, aguardando início dos trabalhos.
     */
    ABERTA("aberta"),

    /**
     * OS em execução pelo mecânico.
     */
    EM_ANDAMENTO("em_andamento"),

    /**
     * OS pausada aguardando chegada de peças.
     */
    AGUARDANDO_PECAS("aguardando_pecas"),

    /**
     * OS finalizada com sucesso.
     */
    CONCLUIDA("concluida"),

    /**
     * OS cancelada (não será executada).
     */
    CANCELADA("cancelada");

    private final String value;

    OSManutencaoStatus(String value) {
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
    public static OSManutencaoStatus fromValue(String value) {
        for (OSManutencaoStatus status : values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Invalid OSManutencaoStatus: " + value);
    }
}
