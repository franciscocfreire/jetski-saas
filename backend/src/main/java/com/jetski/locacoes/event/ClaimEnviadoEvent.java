package com.jetski.locacoes.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain Event: Claim-token de ativação enviado ao cliente.
 *
 * <p>Publicado ao gerar/enviar o link de ativação da conta do cliente
 * (e-mail/SMS/WhatsApp/QR). Consumido por {@code AuditEventListener}.
 *
 * @param canais resumo dos canais usados (ex.: "email,whatsapp")
 */
public record ClaimEnviadoEvent(
    UUID tenantId,
    UUID clienteId,
    String canais,
    UUID enviadoPor,
    Instant occurredAt
) {
    public static ClaimEnviadoEvent of(
            UUID tenantId, UUID clienteId, String canais, UUID enviadoPor) {
        return new ClaimEnviadoEvent(tenantId, clienteId, canais, enviadoPor, Instant.now());
    }
}
