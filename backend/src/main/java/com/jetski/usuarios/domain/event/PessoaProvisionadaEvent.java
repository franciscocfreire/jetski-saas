package com.jetski.usuarios.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain Event: pessoa provisionada na plataforma (identidade única, F0).
 *
 * <p>Publicado quando um sub autenticado ganha (ou é ligado a) um {@code usuario}
 * — a raiz única da pessoa — fora dos fluxos de staff (convite/signup), i.e.
 * quando um CONSUMIDOR passa a existir como pessoa da plataforma: ativação de
 * claim de balcão ou primeira reserva logada no portal.
 *
 * <p>Consumido pelo AuditEventListener (linha GLOBAL, tenant NULL — a pessoa é
 * da plataforma, não de uma loja; princípio 2 da IDENTIDADE_UNICA_SPEC).
 *
 * @param usuarioId       a pessoa (raiz única)
 * @param providerUserId  sub do IdP vinculado
 * @param usuarioNovo     true = usuario criado agora; false = conta existente
 *                        (ex.: staff) acumulando o papel de cliente
 * @param origem          fluxo que disparou (CLAIM, RESERVA_PORTAL)
 * @param occurredAt      quando ocorreu
 */
public record PessoaProvisionadaEvent(
    UUID usuarioId,
    String providerUserId,
    boolean usuarioNovo,
    String origem,
    Instant occurredAt
) {
    public static PessoaProvisionadaEvent of(
            UUID usuarioId, String providerUserId, boolean usuarioNovo, String origem) {
        return new PessoaProvisionadaEvent(usuarioId, providerUserId, usuarioNovo, origem, Instant.now());
    }
}
