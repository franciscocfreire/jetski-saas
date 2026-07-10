package com.jetski.signup.internal;

import com.jetski.shared.email.EmailService;
import com.jetski.signup.domain.SignupStatus;
import com.jetski.signup.domain.TenantSignup;
import com.jetski.signup.internal.repository.TenantSignupRepository;
import com.jetski.tenant.domain.event.TenantStatusChangedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Aviso de aprovação para empresa cujo admin ainda NÃO ativou o magic-link
 * (sem Usuario/Membro): o destino é o e-mail do tenant_signup pendente.
 */
@DisplayName("SignupTenantApprovedListener")
class SignupTenantApprovedListenerTest {

    private final TenantSignupRepository signupRepository = mock(TenantSignupRepository.class);
    private final EmailService emailService = mock(EmailService.class);

    private final SignupTenantApprovedListener listener =
        new SignupTenantApprovedListener(signupRepository, emailService);

    private final UUID tenant = UUID.randomUUID();

    private TenantStatusChangedEvent evento(String acao) {
        return TenantStatusChangedEvent.of(
            tenant, acao, "PENDENTE_APROVACAO", "ATIVO", UUID.randomUUID(), null, "ACME Ltda", "acme");
    }

    @Test
    @DisplayName("aprovada com signup PENDING → avisa o e-mail do signup")
    void avisaEmailDoSignupPendente() {
        TenantSignup signup = TenantSignup.builder()
            .tenantId(tenant).email("fundador@acme.com").nome("Fundador")
            .token("t").status(SignupStatus.PENDING).build();
        when(signupRepository.findByTenantIdAndStatus(tenant, SignupStatus.PENDING))
            .thenReturn(Optional.of(signup));

        listener.onTenantStatusChanged(evento("TENANT_APPROVED"));

        verify(emailService).sendTenantStatusNotification(
            "fundador@acme.com", "TENANT_APPROVED", "ACME Ltda", null);
    }

    @Test
    @DisplayName("sem signup PENDING (conta já ativada) → nada a fazer aqui")
    void semSignupPendenteNaoEnvia() {
        when(signupRepository.findByTenantIdAndStatus(tenant, SignupStatus.PENDING))
            .thenReturn(Optional.empty());

        listener.onTenantStatusChanged(evento("TENANT_APPROVED"));

        verify(emailService, never()).sendTenantStatusNotification(any(), any(), any(), any());
    }

    @Test
    @DisplayName("suspensão/reativação → ignoradas (só aprovação interessa aqui)")
    void ignoraOutrasAcoes() {
        listener.onTenantStatusChanged(evento("TENANT_SUSPENDED"));
        listener.onTenantStatusChanged(evento("TENANT_REACTIVATED"));

        verifyNoInteractions(signupRepository, emailService);
    }

    @Test
    @DisplayName("falha de envio → não propaga (best-effort)")
    void falhaNaoPropaga() {
        when(signupRepository.findByTenantIdAndStatus(any(), any()))
            .thenThrow(new RuntimeException("db fora"));

        assertThatCode(() -> listener.onTenantStatusChanged(evento("TENANT_APPROVED")))
            .doesNotThrowAnyException();
    }
}
