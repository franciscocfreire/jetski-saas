package com.jetski.locacoes.api;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Port (inversão de dependência) para o módulo locacoes consultar se o fechamento
 * diário de uma data está bloqueado, sem depender do módulo fechamento.
 *
 * <p>Implementado pelo módulo {@code fechamento}. Quebra o ciclo locacoes ↔ fechamento:
 * fechamento → locacoes (implementa o port) é unidirecional.
 */
public interface FechamentoLockChecker {

    /**
     * @return true se o fechamento diário do tenant para a data está bloqueado
     *         (edições retroativas proibidas).
     */
    boolean isDataBloqueada(UUID tenantId, LocalDate data);

    /**
     * @return true se o fechamento diário do tenant para a data está bloqueado
     *         OU já fechado (não-editável).
     */
    boolean isFechamentoNaoEditavel(UUID tenantId, LocalDate data);
}
