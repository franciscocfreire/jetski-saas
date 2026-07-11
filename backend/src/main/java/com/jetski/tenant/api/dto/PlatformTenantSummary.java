package com.jetski.tenant.api.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Resumo de uma empresa para o painel de plataforma (super admin).
 *
 * <p>Mesma forma de TenantSummary (id, slug, razaoSocial, status, roles) para que o
 * frontend reutilize o tipo no switcher. {@code roles} vem como ADMIN_TENANT (god mode):
 * o super admin atua como admin em qualquer empresa que selecionar.
 */
public record PlatformTenantSummary(
    String id,
    String slug,
    String razaoSocial,
    String status,
    List<String> roles,
    /** Nome do plano da assinatura ativa (null se a empresa ainda não tem assinatura). */
    String plano,
    /** Fim da assinatura ativa (dt_fim) — no Trial, é a data em que os 14 dias vencem. */
    LocalDate assinaturaFim,
    /** Expurgo agendado (exclusão com carência); null = sem exclusão pendente. */
    java.time.Instant exclusaoAgendadaEm
) {
    public static PlatformTenantSummary of(UUID id, String slug, String razaoSocial, String status,
                                           String plano, LocalDate assinaturaFim,
                                           java.time.Instant exclusaoAgendadaEm) {
        return new PlatformTenantSummary(
            id.toString(), slug, razaoSocial, status, List.of("ADMIN_TENANT"), plano,
            assinaturaFim, exclusaoAgendadaEm);
    }
}
