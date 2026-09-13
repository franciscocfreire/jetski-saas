package com.jetski.tenant.internal;

import com.jetski.shared.email.TenantSmtpResolver;
import com.jetski.shared.security.SecretCipher;
import com.jetski.shared.security.TenantContext;
import com.jetski.tenant.domain.Tenant;
import com.jetski.tenant.internal.repository.TenantRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Resolve o SMTP da empresa (tenant) atual a partir do {@link TenantContext}, ou de um
 * tenant explícito ({@link #forTenant}). Só devolve config quando host + usuário + senha
 * estão preenchidos.
 */
@Component
@RequiredArgsConstructor
public class TenantSmtpResolverImpl implements TenantSmtpResolver {

    private final TenantRepository tenantRepository;
    private final SecretCipher secretCipher;
    private final EntityManager entityManager;

    @Override
    public Optional<SmtpSettings> forCurrentTenant() {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            return Optional.empty();
        }
        return montar(tenantRepository.findById(tenantId).orElse(null));
    }

    /**
     * Transação PRÓPRIA de propósito: a RLS de {@code tenant} (V042) só deixa ler a
     * linha do tenant da sessão, então a leitura de outro tenant precisa de
     * {@code set_config('app.tenant_id', alvo, true)} — local à transação, para não
     * vazar para o chamador. {@code REQUIRES_NEW} garante uma transação (o despacho
     * de e-mail roda sem transação no worker) e que o ajuste morre no commit.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<SmtpSettings> forTenant(UUID tenantId) {
        if (tenantId == null) {
            return Optional.empty();
        }
        entityManager.createNativeQuery("SELECT set_config('app.tenant_id', ?1, true)")
            .setParameter(1, tenantId.toString())
            .getSingleResult();
        return montar(tenantRepository.findById(tenantId).orElse(null));
    }

    private Optional<SmtpSettings> montar(Tenant t) {
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
