package com.jetski.tenant.internal;

import com.jetski.shared.email.TenantSmtpResolver;
import com.jetski.shared.security.SecretCipher;
import com.jetski.shared.security.TenantContext;
import com.jetski.tenant.domain.Tenant;
import com.jetski.tenant.internal.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Resolve o SMTP da empresa (tenant) atual a partir do {@link TenantContext}.
 * Só devolve config quando host + usuário + senha estão preenchidos.
 */
@Component
@RequiredArgsConstructor
public class TenantSmtpResolverImpl implements TenantSmtpResolver {

    private final TenantRepository tenantRepository;
    private final SecretCipher secretCipher;

    @Override
    public Optional<SmtpSettings> forCurrentTenant() {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            return Optional.empty();
        }
        Tenant t = tenantRepository.findById(tenantId).orElse(null);
        if (t == null || isBlank(t.getSmtpHost()) || isBlank(t.getSmtpUsername())
                || isBlank(t.getSmtpPassword())) {
            return Optional.empty();
        }
        String from = firstNonBlank(t.getSmtpFrom(), t.getEmailRemetente(), t.getSmtpUsername());
        int port = t.getSmtpPort() != null ? t.getSmtpPort() : 587;
        boolean tls = t.getSmtpStarttls() == null || t.getSmtpStarttls();
        return Optional.of(new SmtpSettings(
            t.getSmtpHost().trim(), port, t.getSmtpUsername().trim(),
            secretCipher.decrypt(t.getSmtpPassword()),
            from, t.getRazaoSocial(), tls));
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String firstNonBlank(String... vals) {
        for (String v : vals) {
            if (v != null && !v.isBlank()) return v.trim();
        }
        return null;
    }
}
