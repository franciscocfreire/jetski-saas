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
        ReflectionTestUtils.setField(service, "fromName", "MeuJet");

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
}
