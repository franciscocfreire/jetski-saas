package com.jetski.shared.audit.api.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Filter parameters for audit log queries.
 *
 * @author Jetski Team
 * @since 0.10.0
 */
public record AuditoriaFilters(
    String acao,
    String entidade,
    UUID entidadeId,
    UUID usuarioId,
    LocalDate dataInicio,
    LocalDate dataFim
) {

    /**
     * Check if any filter is active.
     */
    public boolean hasFilters() {
        return acao != null || entidade != null || entidadeId != null
            || usuarioId != null || dataInicio != null || dataFim != null;
    }
}
