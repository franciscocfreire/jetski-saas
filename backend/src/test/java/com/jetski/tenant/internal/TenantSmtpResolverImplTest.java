package com.jetski.tenant.internal;

import com.jetski.shared.security.SecretCipher;
import com.jetski.shared.security.TenantContext;
import com.jetski.tenant.domain.Tenant;
import com.jetski.tenant.internal.repository.TenantRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("TenantSmtpResolverImpl (From do SMTP próprio)")
class TenantSmtpResolverImplTest {

    private final TenantRepository tenantRepository = mock(TenantRepository.class);
    // Sem chave, o cipher é no-op: a senha volta como foi gravada.
    private final TenantSmtpResolverImpl resolver =
        new TenantSmtpResolverImpl(tenantRepository, new SecretCipher("", ""), mock(EntityManager.class));

    private final UUID tenantId = UUID.randomUUID();

    @AfterEach
    void limpar() {
        TenantContext.clear();
    }

    private Tenant.TenantBuilder base() {
        return Tenant.builder().id(tenantId).razaoSocial("Jet Save")
            .smtpHost("smtp.gmail.com").smtpPort(587)
            .smtpUsername("luciopaulojj@gmail.com").smtpPassword("senha-de-app");
    }

    private void comTenant(Tenant t) {
        TenantContext.setTenantId(tenantId);
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(t));
    }

    @Test
    @DisplayName("From vazio: remetente é a conta que autentica, não o e-mail de contato da loja")
    void fromVazioUsaAConta() {
        comTenant(base().emailRemetente("jetsaveturismonautico@gmail.com").build());

        var s = resolver.forCurrentTenant().orElseThrow();

        assertThat(s.username()).isEqualTo("luciopaulojj@gmail.com");
        assertThat(s.from()).isEqualTo("luciopaulojj@gmail.com");
    }

    @Test
    @DisplayName("From preenchido: prevalece sobre a conta")
    void fromPreenchidoPrevalece() {
        comTenant(base().smtpFrom("eama@jetsave.com.br").emailRemetente("contato@jetsave.com.br").build());

        var s = resolver.forCurrentTenant().orElseThrow();

        assertThat(s.from()).isEqualTo("eama@jetsave.com.br");
        assertThat(s.username()).isEqualTo("luciopaulojj@gmail.com");
    }

    @Test
    @DisplayName("sem senha: SMTP próprio não é usado")
    void semSenhaNaoUsa() {
        comTenant(base().smtpPassword(null).build());

        assertThat(resolver.forCurrentTenant()).isEmpty();
    }
}
