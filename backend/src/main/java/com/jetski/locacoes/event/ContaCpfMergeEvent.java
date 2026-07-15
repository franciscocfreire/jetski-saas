package com.jetski.locacoes.event;

import java.time.Instant;

/**
 * Domain Event: contas unificadas por CPF (portal do cliente).
 *
 * <p>Publicado quando o cliente prova a posse da conta dona do CPF via OTP e
 * a identidade federada (Google) da conta duplicada é transferida para ela;
 * a duplicata é descartada. Evento GLOBAL (sem tenant — identidade segue a
 * pessoa). Consumido por {@code AuditEventListener}.
 *
 * @param ownerProviderUserId sub da conta que ficou (dona do CPF)
 * @param dupProviderUserId   sub da conta duplicada descartada
 * @param cpfMascarado        CPF mascarado (ex.: "***.***.789-09") para trilha
 */
public record ContaCpfMergeEvent(
    String ownerProviderUserId,
    String dupProviderUserId,
    String cpfMascarado,
    Instant occurredAt
) {
    public static ContaCpfMergeEvent of(
            String ownerProviderUserId, String dupProviderUserId, String cpfMascarado) {
        return new ContaCpfMergeEvent(ownerProviderUserId, dupProviderUserId, cpfMascarado, Instant.now());
    }
}
