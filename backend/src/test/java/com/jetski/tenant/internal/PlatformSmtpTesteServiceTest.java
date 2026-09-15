package com.jetski.tenant.internal;

import com.jetski.shared.email.SmtpSenderFactory;
import com.jetski.shared.email.TenantSmtpResolver;
import com.jetski.shared.exception.BusinessException;
import com.jetski.tenant.domain.Tenant;
import com.jetski.tenant.domain.TenantStatus;
import com.jetski.tenant.domain.event.TenantStatusChangedEvent;
import com.jetski.tenant.internal.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("PlatformSmtpTesteService (teste de SMTP da empresa pelo console)")
class PlatformSmtpTesteServiceTest {

    private final TenantRepository tenantRepository = mock(TenantRepository.class);
    private final TenantSmtpResolver resolver = mock(TenantSmtpResolver.class);
    private final SmtpSenderFactory factory = mock(SmtpSenderFactory.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final PlatformSmtpTesteService service =
        new PlatformSmtpTesteService(tenantRepository, resolver, factory, events);

    private final UUID tenantId = UUID.randomUUID();
    private final TenantSmtpResolver.SmtpSettings smtp = new TenantSmtpResolver.SmtpSettings(
        "smtp.jetsave.com.br", 587, "eama@jetsave.com.br", "segredo", "eama@jetsave.com.br", "Jet Save", true);
    private final JavaMailSender sender = mock(JavaMailSender.class);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "emailPlataforma", "meujet.locadora@gmail.com");
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(Tenant.builder()
            .id(tenantId).slug("jetsave").razaoSocial("Jet Save").status(TenantStatus.ATIVO).build()));
        when(factory.build(smtp)).thenReturn(sender);
    }

    @Test
    @DisplayName("com SMTP: envia pelo servidor da empresa para o e-mail da plataforma e audita")
    void enviaParaAPlataforma() throws Exception {
        when(resolver.forTenant(tenantId)).thenReturn(Optional.of(smtp));

        var r = service.testar(tenantId);

        assertThat(r.enviado()).isTrue();
        assertThat(r.de()).isEqualTo("eama@jetsave.com.br");
        assertThat(r.para()).isEqualTo("meujet.locadora@gmail.com");
        assertThat(r.servidor()).isEqualTo("smtp.jetsave.com.br:587");
        assertThat(r.erro()).isNull();
        verify(factory).send(same(sender), eq("eama@jetsave.com.br"), eq("Jet Save"),
            eq("meujet.locadora@gmail.com"), anyString(), anyString(), isNull(), isNull(), isNull());
        ArgumentCaptor<TenantStatusChangedEvent> ev = ArgumentCaptor.forClass(TenantStatusChangedEvent.class);
        verify(events).publishEvent(ev.capture());
        assertThat(ev.getValue().acao()).isEqualTo("TENANT_SMTP_TESTE");
        assertThat(ev.getValue().motivo()).startsWith("enviado");
    }

    @Test
    @DisplayName("falha de SMTP: devolve enviado=false com a causa raiz (e audita a falha)")
    void falhaDevolveCausaRaiz() throws Exception {
        when(resolver.forTenant(tenantId)).thenReturn(Optional.of(smtp));
        doThrow(new RuntimeException("wrapper", new IllegalStateException("535 Authentication failed")))
            .when(factory).send(any(), anyString(), anyString(), anyString(), anyString(), anyString(),
                isNull(), isNull(), isNull());

        var r = service.testar(tenantId);

        assertThat(r.enviado()).isFalse();
        assertThat(r.erro()).isEqualTo("535 Authentication failed");
        ArgumentCaptor<TenantStatusChangedEvent> ev = ArgumentCaptor.forClass(TenantStatusChangedEvent.class);
        verify(events).publishEvent(ev.capture());
        assertThat(ev.getValue().motivo()).contains("falhou: 535 Authentication failed");
    }

    @Test
    @DisplayName("sem SMTP cadastrado: erro de negócio, nada é enviado")
    void semSmtp() throws Exception {
        when(resolver.forTenant(tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.testar(tenantId))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("SMTP não cadastrado");
        verify(factory, never()).build(any());
        verify(events, never()).publishEvent(any());
    }
}
