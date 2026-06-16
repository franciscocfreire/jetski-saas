package com.jetski.locacoes.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain Event: Pré-conta de cliente criada (atendimento de balcão).
 *
 * <p>Publicado quando um funcionário registra um cliente sem login
 * (origem=BALCAO, status PRE_CONTA). Consumido por {@code AuditEventListener}.
 */
public record PreContaCriadaEvent(
    UUID tenantId,
    UUID clienteId,
    String origem,
    UUID criadoPor,
    Instant occurredAt
) {
    public static PreContaCriadaEvent of(
            UUID tenantId, UUID clienteId, String origem, UUID criadoPor) {
        return new PreContaCriadaEvent(tenantId, clienteId, origem, criadoPor, Instant.now());
    }
}
