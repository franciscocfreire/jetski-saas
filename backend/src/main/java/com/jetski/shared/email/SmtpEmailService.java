package com.jetski.shared.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Production email service - sends real emails via SMTP.
 *
 * Active profile: prod only
 * (dev/local/test use DevEmailService for logging and E2E test support)
 *
 * Requires configuration in application-prod.yml:
 * - spring.mail.host
 * - spring.mail.port
 * - spring.mail.username
 * - spring.mail.password
 *
 * @author Jetski Team
 * @since 0.3.0
 */
@Slf4j
@Service
@Profile("prod")
@RequiredArgsConstructor
public class SmtpEmailService implements EmailService {

    private final JavaMailSender mailSender;
    private final TenantSmtpResolver tenantSmtpResolver;
    private final SmtpSenderFactory senderFactory;

    @Value("${jetski.email.from:noreply@pegaojet.com.br}")
    private String fromEmail;

    @Value("${jetski.email.from-name:Pega o Jet}")
    private String fromName;

    @Override
    public void sendInvitationEmail(String to, String name, String activationLink, String temporaryPassword) {
        sendEmail(to, EmailTemplates.INVITATION_SUBJECT,
            EmailTemplates.invitationHtml(name, activationLink, temporaryPassword));
    }

    @Override
    public void sendPasswordResetEmail(String to, String name, String resetLink) {
        sendEmail(to, EmailTemplates.PASSWORD_RESET_SUBJECT,
            EmailTemplates.passwordResetHtml(name, resetLink));
    }

    @Override
    public void sendNewTenantNotification(String to, String razaoSocial, String slug) {
        sendEmail(to, EmailTemplates.NEW_TENANT_SUBJECT,
            EmailTemplates.newTenantNotificationHtml(razaoSocial, slug));
    }

    @Override
    public void sendEmailComAnexo(String to, String subject, String htmlBody,
                                  String attachmentName, byte[] attachment, String attachmentContentType) {
        try {
            dispatch(to, subject, htmlBody, attachmentName, attachment, attachmentContentType);
            log.info("Email com anexo enviado: to={}, subject={}, anexo={} ({} bytes)",
                to, subject, attachmentName, attachment == null ? 0 : attachment.length);
        } catch (Exception e) {
            log.error("Failed to send email with attachment: to={}, subject={}", to, subject, e);
            throw new RuntimeException("Failed to send email with attachment", e);
        }
    }

    @Override
    public void sendEmail(String to, String subject, String htmlBody) {
        try {
            dispatch(to, subject, htmlBody, null, null, null);
            log.info("Email sent successfully: to={}, subject={}", to, subject);
        } catch (Exception e) {
            // Best-effort: uma falha de email NÃO deve interromper o fluxo de negócio
            // (signup/ativação/aprovação). Apenas registra o erro para diagnóstico.
            log.error("Failed to send email (ignored, best-effort): to={}, subject={}, error={}",
                to, subject, e.getMessage(), e);
        }
    }

    /**
     * Envia usando o SMTP próprio do tenant (se configurado) — "from" real da empresa —
     * ou o SMTP global da plataforma como fallback.
     */
    private void dispatch(String to, String subject, String html,
                          String attName, byte[] att, String attType) throws Exception {
        var perTenant = tenantSmtpResolver.forCurrentTenant();
        if (perTenant.isPresent()) {
            var s = perTenant.get();
            String nome = (s.fromName() != null && !s.fromName().isBlank()) ? s.fromName() : fromName;
            senderFactory.send(senderFactory.build(s), s.from(), nome, to, subject, html, attName, att, attType);
            log.debug("E-mail enviado pelo SMTP do tenant (from={})", s.from());
        } else {
            senderFactory.send(mailSender, fromEmail, fromName, to, subject, html, attName, att, attType);
        }
    }

}
