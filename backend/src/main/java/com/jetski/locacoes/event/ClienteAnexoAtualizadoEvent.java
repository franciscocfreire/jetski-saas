package com.jetski.locacoes.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain Event: documento (anexo) do cliente enviado/substituído.
 *
 * <p>Trilha LGPD do fato — carrega o TIPO do documento e a origem, nunca o
 * conteúdo, bytes ou s3Key. Consumido por {@code AuditEventListener}.
 *
 * @param tenantId      tenant do cadastro (anexos são por loja)
 * @param clienteId     cliente dono do documento
 * @param tipo          IDENTIDADE | SELFIE | COMPROVANTE_RESIDENCIA | CHA
 * @param origem        "PORTAL" (próprio cliente) ou "BALCAO" (staff)
 * @param registradoPor sub do cliente (portal) ou id do usuário staff — texto livre
 * @param occurredAt    quando ocorreu
 */
public record ClienteAnexoAtualizadoEvent(
    UUID tenantId,
    UUID clienteId,
    String tipo,
    String origem,
    String registradoPor,
    Instant occurredAt
) {
    public static ClienteAnexoAtualizadoEvent of(
            UUID tenantId, UUID clienteId, String tipo, String origem, String registradoPor) {
        return new ClienteAnexoAtualizadoEvent(tenantId, clienteId, tipo, origem, registradoPor, Instant.now());
    }
}
