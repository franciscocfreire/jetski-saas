package com.jetski.tenant.internal;

import com.jetski.shared.security.SecretCipher;
import com.jetski.tenant.domain.Tenant;
import com.jetski.tenant.internal.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Re-cifragem dos segredos dos tenants (passo "eager" da rotação de chave): decifra
 * cada {@code smtp_password} (chave atual ou anterior) e re-cifra com a chave ATUAL.
 * Também migra valores legados em texto puro para cifrado. Depois disso, a chave
 * anterior ({@code JETSKI_SECRET_KEY_PREVIOUS}) pode ser removida.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformSecretsService {

    private final TenantRepository tenantRepository;
    private final SecretCipher secretCipher;

    /** Resultado da re-cifragem. */
    public record ReencryptResult(int comSegredo, int recifrados, int falhas, boolean criptografiaAtiva) {}

    @Transactional
    public ReencryptResult reencrypt() {
        if (!secretCipher.isEnabled()) {
            log.warn("Re-cifragem ignorada: JETSKI_SECRET_KEY não configurada (criptografia desativada).");
            return new ReencryptResult(0, 0, 0, false);
        }
        int comSegredo = 0, recifrados = 0, falhas = 0;
        for (Tenant t : tenantRepository.findAll()) {
            String stored = t.getSmtpPassword();
            if (stored == null || stored.isBlank()) {
                continue;
            }
            comSegredo++;
            try {
                String plain = secretCipher.decrypt(stored);   // atual → anterior (keyring)
                t.setSmtpPassword(secretCipher.encrypt(plain)); // re-cifra com a atual
                tenantRepository.save(t);
                recifrados++;
            } catch (Exception e) {
                // Não conseguiu decifrar (chave anterior ausente?) — não derruba o lote.
                log.warn("Falha ao re-cifrar segredo do tenant {}: {}", t.getId(), e.getMessage());
                falhas++;
            }
        }
        log.info("Re-cifragem concluída: comSegredo={}, recifrados={}, falhas={}",
            comSegredo, recifrados, falhas);
        return new ReencryptResult(comSegredo, recifrados, falhas, true);
    }
}
