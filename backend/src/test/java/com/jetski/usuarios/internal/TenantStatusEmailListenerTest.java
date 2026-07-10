package com.jetski.usuarios.internal;

import com.jetski.shared.email.EmailService;
import com.jetski.tenant.domain.event.TenantStatusChangedEvent;
import com.jetski.usuarios.internal.repository.MembroRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Aviso por e-mail aos ADMIN_TENANT na mudança de status da empresa
 * (aprovada/suspensa/reativada); best-effort (nunca propaga exceção).
 */
@DisplayName("TenantStatusEmailListener")
class TenantStatusEmailListenerTest {

    private final MembroRepository membroRepository = mock(MembroRepository.class);
    private final EmailService emailService = mock(EmailService.class);

    private final TenantStatusEmailListener listener =
        new TenantStatusEmailListener(membroRepository, emailService);

    private final UUID tenant = UUID.randomUUID();
    private final UUID superAdmin = UUID.randomUUID();

    private TenantStatusChangedEvent evento(String acao, String motivo) {
        return TenantStatusChangedEvent.of(
            tenant, acao, "PENDENTE_APROVACAO", "ATIVO", superAdmin, motivo, "ACME Ltda", "acme");
    }

    @Test
    @DisplayName("aprovada → um e-mail por ADMIN_TENANT ativo")
    void avisaAdminsNaAprovacao() {
        when(membroRepository.findActiveMemberEmailsByTenantIdAndRole(tenant, "ADMIN_TENANT"))
            .thenReturn(List.of("dono@acme.com", "socio@acme.com"));

        listener.onTenantStatusChanged(evento("TENANT_APPROVED", null));

        verify(emailService).sendTenantStatusNotification("dono@acme.com", "TENANT_APPROVED", "ACME Ltda", null);
        verify(emailService).sendTenantStatusNotification("socio@acme.com", "TENANT_APPROVED", "ACME Ltda", null);
    }

    @Test
    @DisplayName("suspensa → motivo repassado ao e-mail")
    void repassaMotivoNaSuspensao() {
        when(membroRepository.findActiveMemberEmailsByTenantIdAndRole(tenant, "ADMIN_TENANT"))
            .thenReturn(List.of("dono@acme.com"));

        listener.onTenantStatusChanged(evento("TENANT_SUSPENDED", "inadimplência"));

        verify(emailService).sendTenantStatusNotification(
            "dono@acme.com", "TENANT_SUSPENDED", "ACME Ltda", "inadimplência");
    }

    @Test
    @DisplayName("nenhum ADMIN_TENANT ativo → não envia nada (caso coberto pelo listener do signup)")
    void semAdminsNaoEnvia() {
        when(membroRepository.findActiveMemberEmailsByTenantIdAndRole(tenant, "ADMIN_TENANT"))
            .thenReturn(List.of());

        listener.onTenantStatusChanged(evento("TENANT_APPROVED", null));

        verify(emailService, never()).sendTenantStatusNotification(any(), any(), any(), any());
    }

    @Test
    @DisplayName("EmailService lançando → não propaga e tenta os demais destinatários")
    void falhaDeEnvioNaoPropaga() {
        when(membroRepository.findActiveMemberEmailsByTenantIdAndRole(tenant, "ADMIN_TENANT"))
            .thenReturn(List.of("dono@acme.com", "socio@acme.com"));
        doThrow(new RuntimeException("smtp fora"))
            .when(emailService).sendTenantStatusNotification(anyString(), anyString(), anyString(), any());

        assertThatCode(() -> listener.onTenantStatusChanged(evento("TENANT_APPROVED", null)))
            .doesNotThrowAnyException();

        verify(emailService).sendTenantStatusNotification("socio@acme.com", "TENANT_APPROVED", "ACME Ltda", null);
    }

    @Test
    @DisplayName("repositório lançando → não propaga (best-effort)")
    void falhaDeQueryNaoPropaga() {
        when(membroRepository.findActiveMemberEmailsByTenantIdAndRole(any(), anyString()))
            .thenThrow(new RuntimeException("db fora"));

        assertThatCode(() -> listener.onTenantStatusChanged(evento("TENANT_APPROVED", null)))
            .doesNotThrowAnyException();
    }
}
