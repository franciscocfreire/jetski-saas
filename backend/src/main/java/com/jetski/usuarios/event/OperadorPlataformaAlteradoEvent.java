package com.jetski.usuarios.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Concessão ou revogação de papéis de PLATAFORMA. Evento global — sem tenant.
 *
 * <p>Conceder acesso de plataforma é a ação mais sensível do produto: quem recebe
 * enxerga e opera todas as empresas. A trilha precisa registrar quem concedeu, para
 * quem, o antes e o depois.
 *
 * @param usuarioId    alvo da concessão/revogação
 * @param emailAlvo    e-mail do alvo (a auditoria sobrevive à exclusão do usuário)
 * @param papeisAntes  papéis de plataforma antes da mudança
 * @param papeisDepois papéis de plataforma depois (vazio = revogação total)
 * @param actor        operador que executou a mudança
 * @param quando       instante da mudança
 */
public record OperadorPlataformaAlteradoEvent(
    UUID usuarioId,
    String emailAlvo,
    List<String> papeisAntes,
    List<String> papeisDepois,
    UUID actor,
    Instant quando
) {
    public static OperadorPlataformaAlteradoEvent of(
            UUID usuarioId, String emailAlvo,
            List<String> papeisAntes, List<String> papeisDepois, UUID actor) {
        return new OperadorPlataformaAlteradoEvent(
            usuarioId, emailAlvo, List.copyOf(papeisAntes), List.copyOf(papeisDepois),
            actor, Instant.now());
    }

    /** Revogação total (ficou sem nenhum papel de plataforma). */
    public boolean ehRevogacao() {
        return papeisDepois.isEmpty();
    }
}
