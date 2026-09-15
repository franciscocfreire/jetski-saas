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

    @Value("${jetski.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    @Value("${jetski.email.from-name:Meu Jet}")
    private String fromName;

    @Override
    public void sendInvitationEmail(String to, String name, String activationLink, String temporaryPassword,
                                    String empresa) {
        sendEmail(to, EmailTemplates.invitationSubject(empresa),
            EmailTemplates.invitationHtml(name, activationLink, temporaryPassword, empresa));
    }

    @Override
    public void sendExistingAccountInvitationEmail(String to, String name, String acceptLink, String empresa) {
        sendEmail(to, EmailTemplates.existingAccountInvitationSubject(empresa),
            EmailTemplates.existingAccountInvitationHtml(name, acceptLink, empresa));
    }

    @Override
    public void sendClienteInvitationEmail(String to, String name, String activationLink, String temporaryPassword,
                                           String empresa) {
        sendEmail(to, EmailTemplates.clienteInvitationSubject(empresa),
            EmailTemplates.clienteInvitationHtml(name, activationLink, temporaryPassword, empresa));
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
    public void sendTenantStatusNotification(String to, String acao, String razaoSocial, String motivo) {
        sendEmail(to, EmailTemplates.tenantStatusSubject(acao),
            EmailTemplates.tenantStatusHtml(acao, razaoSocial, motivo, frontendUrl + "/dashboard"));
    }

    @Override
    public void sendTrialWarningNotification(String to, String razaoSocial, int diasRestantes, String dataFim) {
        sendEmail(to, EmailTemplates.trialWarningSubject(diasRestantes),
            EmailTemplates.trialWarningHtml(razaoSocial, diasRestantes, dataFim));
    }

    @Override
    public void sendEmailComAnexo(String to, String subject, String htmlBody,
                                  String attachmentName, byte[] attachment, String attachmentContentType,
                                  String replyTo, Remetente remetente) {
        try {
            dispatch(to, subject, htmlBody, attachmentName, attachment, attachmentContentType, replyTo, remetente);
            log.info("Email com anexo enviado: to={}, subject={}, anexo={} ({} bytes)",
                to, subject, attachmentName, attachment == null ? 0 : attachment.length);
        } catch (SmtpProprioAusenteException e) {
            log.warn("E-mail NÃO enviado (exige SMTP próprio do tenant {}): to={}, subject={}",
                e.getTenantId(), to, subject);
            throw e; // sem embrulhar: o chamador distingue "sem SMTP" de falha de envio
        } catch (Exception e) {
            log.error("Failed to send email with attachment: to={}, subject={}", to, subject, e);
            throw new RuntimeException("Failed to send email with attachment", e);
        }
    }

    @Override
    public void sendEmail(String to, String subject, String htmlBody) {
        try {
            dispatch(to, subject, htmlBody, null, null, null, null, null);
            log.info("Email sent successfully: to={}, subject={}", to, subject);
        } catch (Exception e) {
            // Best-effort: uma falha de email NÃO deve interromper o fluxo de negócio
            // (signup/ativação/aprovação). Apenas registra o erro para diagnóstico.
            log.error("Failed to send email (ignored, best-effort): to={}, subject={}, error={}",
                to, subject, e.getMessage(), e);
        }
    }

    @Override
    public void sendEmailComImagemInline(String to, String subject, String htmlBody,
                                         String contentId, byte[] png) {
        if (png == null || png.length == 0) {
            sendEmail(to, subject, htmlBody);
            return;
        }
        try {
            var perTenant = tenantSmtpResolver.forCurrentTenant();
            if (perTenant.isPresent()) {
                var s = perTenant.get();
                String nome = (s.fromName() != null && !s.fromName().isBlank()) ? s.fromName() : fromName;
                senderFactory.sendComImagemInline(senderFactory.build(s), s.from(), nome, to, subject,
                    htmlBody, contentId, png);
            } else {
                senderFactory.sendComImagemInline(mailSender, fromEmail, fromName, to, subject,
                    htmlBody, contentId, png);
            }
            log.info("Email com imagem inline enviado: to={}, subject={}", to, subject);
        } catch (Exception e) {
            log.error("Failed to send email with inline image (ignored, best-effort): to={}, subject={}, error={}",
                to, subject, e.getMessage(), e);
        }
    }

    /**
     * Envia usando o SMTP próprio do tenant (se configurado) — "from" real da empresa —
     * ou o SMTP global da plataforma como fallback. Com {@code remetente} explícito, o
     * tenant é o dele (e não o da sessão) e, no fallback global, o nome de exibição
     * do "From" é o dele. Com {@code exigeSmtpProprio} (ofício à Capitania) não há
     * fallback: sem SMTP próprio, nada sai.
     */
    private void dispatch(String to, String subject, String html,
                          String attName, byte[] att, String attType, String replyTo,
                          Remetente remetente) throws Exception {
        var perTenant = remetente != null
            ? tenantSmtpResolver.forTenant(remetente.tenantId())
            : tenantSmtpResolver.forCurrentTenant();
        if (perTenant.isEmpty() && remetente != null && remetente.exigeSmtpProprio()) {
            throw new SmtpProprioAusenteException(remetente.tenantId(), remetente.nome());
        }
        String nomeGlobal = remetente != null && remetente.nome() != null && !remetente.nome().isBlank()
            ? remetente.nome() : fromName;
        String cc = remetente != null ? remetente.copia() : null;
        if (perTenant.isPresent()) {
            var s = perTenant.get();
            String nome = (s.fromName() != null && !s.fromName().isBlank()) ? s.fromName() : nomeGlobal;
            senderFactory.send(senderFactory.build(s), s.from(), nome, to, subject, html, attName, att, attType,
                replyTo, cc);
            log.debug("E-mail enviado pelo SMTP do tenant (from={})", s.from());
        } else {
            senderFactory.send(mailSender, fromEmail, nomeGlobal, to, subject, html, attName, att, attType,
                replyTo, cc);
        }
    }

}
