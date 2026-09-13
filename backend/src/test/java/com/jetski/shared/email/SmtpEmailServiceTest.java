package com.jetski.shared.email;

import jakarta.mail.Multipart;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Spike F0.3 — prova o envio de e-mail com anexo (MimeMessage multipart),
 * sem SMTP real (JavaMailSender mockado captura a mensagem).
 */
@DisplayName("SmtpEmailService - e-mail com anexo (F0.3)")
class SmtpEmailServiceTest {

    @Test
    @DisplayName("monta um MimeMessage multipart contendo o PDF anexado")
    void enviaComAnexo() throws Exception {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        MimeMessage realMessage = new JavaMailSenderImpl().createMimeMessage();
        when(mailSender.createMimeMessage()).thenReturn(realMessage);

        // Sem SMTP de tenant → usa o mailSender global via o factory real.
        TenantSmtpResolver smtpResolver = mock(TenantSmtpResolver.class);
        when(smtpResolver.forCurrentTenant()).thenReturn(java.util.Optional.empty());
        SmtpEmailService service = new SmtpEmailService(mailSender, smtpResolver, new SmtpSenderFactory());
        ReflectionTestUtils.setField(service, "fromEmail", "noreply@pegaojet.com.br");
        ReflectionTestUtils.setField(service, "fromName", "Meu Jet");

        byte[] pdf = "%PDF-1.4 documento".getBytes(StandardCharsets.US_ASCII);
        service.sendEmailComAnexo("cliente@example.com", "Seus documentos",
            "<b>Segue o PDF</b>", "documentos.pdf", pdf, "application/pdf");

        verify(mailSender).send(realMessage);

        Object content = realMessage.getContent();
        assertThat(content).isInstanceOf(Multipart.class);

        Multipart mp = (Multipart) content;
        boolean temAnexo = false;
        for (int i = 0; i < mp.getCount(); i++) {
            if ("documentos.pdf".equals(mp.getBodyPart(i).getFileName())) {
                temAnexo = true;
            }
        }
        assertThat(temAnexo).as("anexo documentos.pdf presente no e-mail").isTrue();
    }

    @Test
    @DisplayName("remetente explícito sem SMTP próprio: resolve pelo tenant dele e o From global leva o nome dele")
    void remetenteExplicitoNoSmtpGlobal() throws Exception {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        MimeMessage realMessage = new JavaMailSenderImpl().createMimeMessage();
        when(mailSender.createMimeMessage()).thenReturn(realMessage);

        java.util.UUID eama = java.util.UUID.randomUUID();
        TenantSmtpResolver smtpResolver = mock(TenantSmtpResolver.class);
        when(smtpResolver.forTenant(eama)).thenReturn(java.util.Optional.empty());
        SmtpEmailService service = new SmtpEmailService(mailSender, smtpResolver, new SmtpSenderFactory());
        ReflectionTestUtils.setField(service, "fromEmail", "noreply@pegaojet.com.br");
        ReflectionTestUtils.setField(service, "fromName", "Meu Jet");

        service.sendEmailComAnexo("capitania@example.com", "Ofício", "<p>ofício</p>", "doc.pdf",
            "%PDF".getBytes(StandardCharsets.US_ASCII), "application/pdf", "oficial@eama.com",
            new EmailService.Remetente(eama, "EAMA Santos LTDA"));

        // o tenant da sessão (a operadora, na delegada) não entra na resolução
        verify(smtpResolver).forTenant(eama);
        verify(smtpResolver, org.mockito.Mockito.never()).forCurrentTenant();
        var from = (jakarta.mail.internet.InternetAddress) realMessage.getFrom()[0];
        assertThat(from.getAddress()).isEqualTo("noreply@pegaojet.com.br");
        assertThat(from.getPersonal()).isEqualTo("EAMA Santos LTDA");
        assertThat(realMessage.getReplyTo()[0].toString()).contains("oficial@eama.com");
    }

    @Test
    @DisplayName("remetente explícito com SMTP próprio: envia pelo servidor dele, com o From dele")
    void remetenteExplicitoComSmtpProprio() throws Exception {
        java.util.UUID eama = java.util.UUID.randomUUID();
        var settings = new TenantSmtpResolver.SmtpSettings("smtp.eama", 587, "eama", "segredo",
            "oficios@eamasantos.com.br", "EAMA Santos LTDA", true);
        TenantSmtpResolver smtpResolver = mock(TenantSmtpResolver.class);
        when(smtpResolver.forTenant(eama)).thenReturn(java.util.Optional.of(settings));
        SmtpSenderFactory factory = mock(SmtpSenderFactory.class);
        JavaMailSender senderDaEama = mock(JavaMailSender.class);
        when(factory.build(settings)).thenReturn(senderDaEama);
        SmtpEmailService service = new SmtpEmailService(mock(JavaMailSender.class), smtpResolver, factory);
        ReflectionTestUtils.setField(service, "fromEmail", "noreply@pegaojet.com.br");
        ReflectionTestUtils.setField(service, "fromName", "Meu Jet");

        service.sendEmailComAnexo("capitania@example.com", "Ofício", "<p>ofício</p>", "doc.pdf",
            "%PDF".getBytes(StandardCharsets.US_ASCII), "application/pdf", "oficial@eama.com",
            new EmailService.Remetente(eama, "EAMA Santos LTDA"));

        verify(factory).send(org.mockito.ArgumentMatchers.same(senderDaEama),
            org.mockito.ArgumentMatchers.eq("oficios@eamasantos.com.br"),
            org.mockito.ArgumentMatchers.eq("EAMA Santos LTDA"),
            org.mockito.ArgumentMatchers.eq("capitania@example.com"),
            org.mockito.ArgumentMatchers.eq("Ofício"), org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.eq("doc.pdf"), org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.eq("application/pdf"),
            org.mockito.ArgumentMatchers.eq("oficial@eama.com"));
        verify(smtpResolver, org.mockito.Mockito.never()).forCurrentTenant();
    }
}
