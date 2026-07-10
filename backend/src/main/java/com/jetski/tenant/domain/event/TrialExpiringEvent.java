package com.jetski.tenant.domain.event;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Domain Event: o período de teste (Trial) da empresa está para vencer.
 *
 * <p>Publicado pelo job diário de expiração quando faltam poucos dias (3/1) para
 * {@code assinatura.dt_fim}. Consumido pelo listener de e-mail (usuarios) para avisar
 * os ADMIN_TENANT — por isso carrega razão social/slug (consumidor não lê o
 * repositório do módulo tenant).
 *
 * @param tenantId       tenant afetado
 * @param razaoSocial    razão social da empresa
 * @param slug           identificador da empresa
 * @param diasRestantes  dias até o fim do trial (3 ou 1)
 * @param dataFim        data em que o trial vence
 * @param occurredAt     quando o evento foi publicado
 *
 * @author Jetski Team
 */
public record TrialExpiringEvent(
    UUID tenantId,
    String razaoSocial,
    String slug,
    int diasRestantes,
    LocalDate dataFim,
    Instant occurredAt
) {
    public static TrialExpiringEvent of(
            UUID tenantId, String razaoSocial, String slug, int diasRestantes, LocalDate dataFim) {
        return new TrialExpiringEvent(tenantId, razaoSocial, slug, diasRestantes, dataFim, Instant.now());
    }
}
