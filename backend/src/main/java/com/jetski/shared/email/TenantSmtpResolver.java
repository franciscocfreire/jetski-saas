package com.jetski.shared.email;

import java.util.Optional;

/**
 * Porta (resolvida no módulo tenant) que devolve a configuração SMTP da empresa
 * (tenant) atual, quando ela tem servidor próprio configurado. Permite envio com
 * o "from" real da empresa. Sem config → {@link Optional#empty()} (usa o global).
 */
public interface TenantSmtpResolver {

    Optional<SmtpSettings> forCurrentTenant();

    /**
     * SMTP de um tenant específico, que pode NÃO ser o da sessão (ex.: a EAMA emissora
     * quando a operadora dispara o ofício à Capitania). A implementação abre a própria
     * janela de RLS — a tabela {@code tenant} só é legível pelo próprio tenant (V042).
     * Sem config → {@link Optional#empty()} (usa o global).
     */
    Optional<SmtpSettings> forTenant(java.util.UUID tenantId);

    record SmtpSettings(
        String host,
        int port,
        String username,
        String password,
        String from,
        String fromName,
        boolean starttls
    ) {}
}
