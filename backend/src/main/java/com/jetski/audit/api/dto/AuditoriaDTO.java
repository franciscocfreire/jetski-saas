package com.jetski.shared.audit.api.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * DTO for audit log entries.
 *
 * <p>Exposes audit data to the frontend with user-friendly field names
 * and resolved references (e.g., usuario name instead of just ID).
 *
 * @author Jetski Team
 * @since 0.10.0
 */
public record AuditoriaDTO(
    UUID id,
    String acao,
    String entidade,
    UUID entidadeId,
    UUID usuarioId,
    String usuarioNome,
    String ip,
    String traceId,
    Instant createdAt,
    Map<String, Object> dadosAnteriores,
    Map<String, Object> dadosNovos
) {

    /**
     * Factory method to create DTO from entity with resolved user name.
     */
    public static AuditoriaDTO of(
            UUID id, String acao, String entidade, UUID entidadeId,
            UUID usuarioId, String usuarioNome, String ip, String traceId,
            Instant createdAt, Map<String, Object> dadosAnteriores,
            Map<String, Object> dadosNovos) {
        return new AuditoriaDTO(
            id, acao, entidade, entidadeId,
            usuarioId, usuarioNome, ip, traceId,
            createdAt, dadosAnteriores, dadosNovos
        );
    }
}
