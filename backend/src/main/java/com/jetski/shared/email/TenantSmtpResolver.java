package com.jetski.shared.email;

import java.util.Optional;

/**
 * Porta (resolvida no módulo tenant) que devolve a configuração SMTP da empresa
 * (tenant) atual, quando ela tem servidor próprio configurado. Permite envio com
 * o "from" real da empresa. Sem config → {@link Optional#empty()} (usa o global).
 */
public interface TenantSmtpResolver {

    Optional<SmtpSettings> forCurrentTenant();

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
