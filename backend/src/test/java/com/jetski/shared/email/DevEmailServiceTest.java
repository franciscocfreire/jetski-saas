package com.jetski.shared.email;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Dev com Mailpit ligado: nenhum e-mail sai por SMTP real — nem o da empresa com SMTP próprio,
 * nem o ofício de EAMA sem SMTP (que em prod vira SEM_SMTP). O opt-in devolve a regra de prod.
 */
@DisplayName("DevEmailService - tudo no Mailpit em dev")
class DevEmailServiceTest {

    private static final UUID EAMA = UUID.randomUUID();
    private static final byte[] PDF = "%PDF-1.4 oficio".getBytes(StandardCharsets.US_ASCII);

    private final JavaMailSender mailpit = mock(JavaMailSender.class);
    private final TenantSmtpResolver resolver = mock(TenantSmtpResolver.class);
    private final SmtpSenderFactory factory = mock(SmtpSenderFactory.class);
    private final MimeMessage mensagem = new JavaMailSenderImpl().createMimeMessage();
    private final DevEmailService service = new DevEmailService();

    @BeforeEach
    void setUp() {
        when(mailpit.createMimeMessage()).thenReturn(mensagem);
        ReflectionTestUtils.setField(service, "mailSender", mailpit);
        ReflectionTestUtils.setField(service, "tenantSmtpResolver", resolver);
        ReflectionTestUtils.setField(service, "senderFactory", factory);
        ReflectionTestUtils.setField(service, "devSmtpEnabled", true);
        ReflectionTestUtils.setField(service, "fromEmail", "noreply@pegaojet.com.br");
        ReflectionTestUtils.setField(service, "fromName", "Meu Jet");
    }

    private void enviarOficio() {
        service.sendEmailComAnexo("capitania@example.com", "Ofício", "<p>ofício</p>", "doc.pdf",
            PDF, "application/pdf", null, EmailService.Remetente.oficioCapitania(EAMA, "Jet Save", null));
    }

    @Test
    @DisplayName("ofício de EAMA sem SMTP próprio chega ao Mailpit em vez de SEM_SMTP")
    void oficioSemSmtpVaiAoMailpit() {
        when(resolver.forTenant(EAMA)).thenReturn(Optional.empty());

        enviarOficio();

        verify(mailpit).send(mensagem);
    }

    @Test
    @DisplayName("EAMA com SMTP próprio: capturado no Mailpit com o From da empresa, sem SMTP real")
    void smtpProprioCapturadoNoMailpit() throws Exception {
        when(resolver.forTenant(EAMA)).thenReturn(Optional.of(new TenantSmtpResolver.SmtpSettings(
            "smtp.gmail.com", 587, "eama.emissora@gmail.com", "senha", "eama.emissora@gmail.com", "Jet Save", true)));

        enviarOficio();

        verify(mailpit).send(mensagem);
        verify(factory, never()).build(any());
        assertThat(((InternetAddress) mensagem.getFrom()[0]).getAddress()).isEqualTo("eama.emissora@gmail.com");
    }

    @Test
    @DisplayName("opt-in dev-usar-smtp-do-tenant: volta a regra de prod (SEM_SMTP)")
    void optInRegraDeProd() {
        ReflectionTestUtils.setField(service, "devUsarSmtpDoTenant", true);
        when(resolver.forTenant(EAMA)).thenReturn(Optional.empty());

        assertThatThrownBy(this::enviarOficio).isInstanceOf(SmtpProprioAusenteException.class);
        verify(mailpit, never()).send(any(MimeMessage.class));
    }
}
