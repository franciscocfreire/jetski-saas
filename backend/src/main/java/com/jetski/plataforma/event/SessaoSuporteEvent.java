package com.jetski.plataforma.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Abertura e encerramento de sessão de suporte. Evento de plataforma — a auditoria grava
 * DUAS linhas: uma no tenant alvo (a empresa tem direito de saber quem entrou e por quê) e
 * uma global (o console lê sem cross-tenant). Mesmo padrão de audit dual da emissão delegada.
 *
 * @param sessaoId       sessão
 * @param tenantId       empresa alvo (null no encerramento — a linha já liga os dois)
 * @param operadorId     operador de plataforma
 * @param motivo         justificativa declarada na abertura
 * @param somenteLeitura se a sessão nasce sem poder de escrita
 * @param abertura       true = abertura, false = encerramento
 * @param quando         instante
 */
public record SessaoSuporteEvent(
    UUID sessaoId, UUID tenantId, UUID operadorId, String motivo,
    boolean somenteLeitura, boolean abertura, Instant quando) {

    public static SessaoSuporteEvent aberta(UUID sessaoId, UUID tenantId, UUID operadorId,
                                            String motivo, boolean somenteLeitura) {
        return new SessaoSuporteEvent(sessaoId, tenantId, operadorId, motivo,
            somenteLeitura, true, Instant.now());
    }

    public static SessaoSuporteEvent encerrada(UUID sessaoId, UUID quem) {
        return new SessaoSuporteEvent(sessaoId, null, quem, null, false, false, Instant.now());
    }
}
