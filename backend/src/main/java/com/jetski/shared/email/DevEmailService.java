package com.jetski.shared.email;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Development email service - logs emails instead of sending.
 *
 * Active profiles: local, dev, test
 *
 * Behavior:
 * - Logs email details to console
 * - Saves email to /tmp/emails/{timestamp}_{email}.txt
 * - Stores last email data for E2E testing
 * - NO actual email is sent
 *
 * @author Jetski Team
 * @since 0.3.0
 */
@Slf4j
@Service
@Profile({"local", "test", "dev"})
public class DevEmailService implements EmailService {

    private static final String EMAIL_DIR = "/tmp/emails";
    private static final DateTimeFormatter TIMESTAMP_FORMAT =
        DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    // Pattern to extract magic token from activation link
    private static final Pattern MAGIC_TOKEN_PATTERN =
        Pattern.compile("magic-activate\\?token=([^\\s\"]+)");

    /**
     * Quando true, além de logar, envia o email via SMTP (Mailpit em dev).
     * Default false → comportamento atual (só log), seguro para CI/test.
     */
    @Value("${jetski.email.dev-smtp-enabled:false}")
    private boolean devSmtpEnabled;

    @Value("${jetski.email.from:noreply@pegaojet.com.br}")
    private String fromEmail;

    @Value("${jetski.email.from-name:Meu Jet}")
    private String fromName;

    /** Opcional: presente quando spring.mail.host está configurado (Mailpit em dev). */
    @Autowired(required = false)
    private JavaMailSender mailSender;

    /** SMTP próprio do tenant (envio real pela conta da empresa) + factory. */
    @Autowired(required = false)
    private TenantSmtpResolver tenantSmtpResolver;
    @Autowired(required = false)
    private SmtpSenderFactory senderFactory;

    /**
     * Last email data - stored for E2E testing purposes.
     * Allows automated tests to retrieve activation tokens without email access.
     */
    @Getter
    private static volatile LastEmailData lastEmail;

    /**
     * Data class for last email sent.
     */
    @Getter
    public static class LastEmailData {
        private final String to;
        private final String name;
        private final String subject;
        private final String activationLink;
        private final String magicToken;
        private final String temporaryPassword;
        private final Instant sentAt;

        public LastEmailData(String to, String name, String subject,
                            String activationLink, String temporaryPassword) {
            this.to = to;
            this.name = name;
            this.subject = subject;
            this.activationLink = activationLink;
            this.temporaryPassword = temporaryPassword;
            this.sentAt = Instant.now();

            // Extract magic token from activation link
            if (activationLink != null) {
                Matcher matcher = MAGIC_TOKEN_PATTERN.matcher(activationLink);
                this.magicToken = matcher.find() ? matcher.group(1) : null;
            } else {
                this.magicToken = null;
            }
        }
    }

    /**
     * Clear last email data (useful for test isolation).
     */
    public static void clearLastEmail() {
        lastEmail = null;
        log.debug("Last email data cleared");
    }

    public DevEmailService() {
        try {
            Files.createDirectories(Paths.get(EMAIL_DIR));
            log.info("DevEmailService initialized - Emails will be logged to: {}", EMAIL_DIR);
        } catch (IOException e) {
            log.warn("Failed to create email directory: {}", EMAIL_DIR, e);
        }
    }

    @Override
    public void sendInvitationEmail(String to, String name, String activationLink, String temporaryPassword) {
        String subject = EmailTemplates.INVITATION_SUBJECT;

        // Store last email data for E2E testing
        lastEmail = new LastEmailData(to, name, subject, activationLink, temporaryPassword);
        log.info("📧 Last email data stored for E2E testing: to={}, magicToken={}", to,
            lastEmail.getMagicToken() != null ? lastEmail.getMagicToken().substring(0, 20) + "..." : "null");

        // Log/arquivo em texto legível; envio (Mailpit) usa o MESMO HTML do prod.
        logAndSaveEmail(to, subject, buildInvitationEmailBody(name, activationLink, temporaryPassword));
        maybeSendViaSmtp(to, subject, EmailTemplates.invitationHtml(name, activationLink, temporaryPassword));
    }

    @Override
    public void sendPasswordResetEmail(String to, String name, String resetLink) {
        String subject = EmailTemplates.PASSWORD_RESET_SUBJECT;

        logAndSaveEmail(to, subject, buildPasswordResetEmailBody(name, resetLink));
        maybeSendViaSmtp(to, subject, EmailTemplates.passwordResetHtml(name, resetLink));
    }

    @Override
    public void sendNewTenantNotification(String to, String razaoSocial, String slug) {
        String subject = EmailTemplates.NEW_TENANT_SUBJECT;
        String body = String.format(
            "Nova empresa aguardando aprovação:%n  Empresa: %s%n  Identificador: %s%n", razaoSocial, slug);
        logAndSaveEmail(to, subject, body);
        maybeSendViaSmtp(to, subject, EmailTemplates.newTenantNotificationHtml(razaoSocial, slug));
    }

    @Override
    public void sendEmailComAnexo(String to, String subject, String htmlBody,
                                  String attachmentName, byte[] attachment, String attachmentContentType) {
        int size = attachment == null ? 0 : attachment.length;
        String body = String.format("%s%n%n[ANEXO] %s (%s, %d bytes)",
            htmlBody, attachmentName, attachmentContentType, size);
        logAndSaveEmail(to, subject, body);
        // Mailpit recebe o HTML COM o anexo (p/ inspeção/download em dev).
        maybeSendViaSmtp(to, subject, htmlBody, attachmentName, attachment, attachmentContentType);
    }

    @Override
    public void sendEmail(String to, String subject, String htmlBody) {
        logAndSaveEmail(to, subject, htmlBody);
        maybeSendViaSmtp(to, subject, htmlBody);
    }

    private void logAndSaveEmail(String to, String subject, String body) {
        // Log to console
        log.info("\n" +
                "================== EMAIL (DEV MODE) ==================\n" +
                "To: {}\n" +
                "Subject: {}\n" +
                "------------------------------------------------------\n" +
                "{}\n" +
                "======================================================",
                to, subject, body);

        // Save to file
        try {
            String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
            String sanitizedEmail = to.replaceAll("[^a-zA-Z0-9@.]", "_");
            String filename = String.format("%s_%s.txt", timestamp, sanitizedEmail);
            Path filePath = Paths.get(EMAIL_DIR, filename);

            String content = String.format(
                "To: %s%nSubject: %s%nDate: %s%n%n%s",
                to, subject, LocalDateTime.now(), body
            );

            Files.writeString(filePath, content, StandardOpenOption.CREATE);
            log.debug("Email saved to: {}", filePath);
        } catch (IOException e) {
            log.warn("Failed to save email to file: to={}", to, e);
        }
    }

    /**
     * Em dev, opcionalmente envia o email (HTML) via SMTP (Mailpit) para inspeção visual.
     * Usa o MESMO template HTML do prod (EmailTemplates), então o que se vê no Mailpit é fiel.
     * Best-effort: uma falha de SMTP NUNCA interrompe o fluxo (signup/convite) nem o E2E.
     */
    private void maybeSendViaSmtp(String to, String subject, String htmlBody) {
        maybeSendViaSmtp(to, subject, htmlBody, null, null, null);
    }

    private void maybeSendViaSmtp(String to, String subject, String htmlBody,
                                  String attachmentName, byte[] attachment, String attachmentContentType) {
        boolean comAnexo = attachment != null && attachment.length > 0;

        // Tenant com SMTP próprio → envio real pela conta da empresa ("from" da loja).
        var perTenant = (tenantSmtpResolver != null && senderFactory != null)
            ? tenantSmtpResolver.forCurrentTenant() : java.util.Optional.<TenantSmtpResolver.SmtpSettings>empty();
        if (perTenant.isPresent()) {
            var s = perTenant.get();
            try {
                String nome = (s.fromName() != null && !s.fromName().isBlank()) ? s.fromName() : fromName;
                senderFactory.send(senderFactory.build(s), s.from(), nome, to, subject, htmlBody,
                    attachmentName, comAnexo ? attachment : null, attachmentContentType);
                log.info("📨 Email enviado pelo SMTP do tenant: to={}, from={}", to, s.from());
            } catch (Exception e) {
                log.warn("Falha (ignorada) ao enviar pelo SMTP do tenant: to={}, error={}", to, e.getMessage());
            }
            return;
        }

        // Senão, comportamento dev padrão: Mailpit (se habilitado).
        if (!devSmtpEnabled || mailSender == null) {
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, comAnexo, "UTF-8");
            helper.setFrom(fromEmail, fromName);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            if (comAnexo) {
                helper.addAttachment(attachmentName,
                    new org.springframework.core.io.ByteArrayResource(attachment), attachmentContentType);
            }
            mailSender.send(message);
            log.info("📨 Email enviado ao Mailpit: to={}, subject={}, anexo={} ({} bytes)",
                to, subject, comAnexo ? attachmentName : "—", attachment == null ? 0 : attachment.length);
        } catch (Exception e) {
            log.warn("Falha (ignorada) ao enviar email via SMTP em dev: to={}, error={}", to, e.getMessage());
        }
    }

    private String buildInvitationEmailBody(String name, String activationLink, String temporaryPassword) {
        return String.format("""
            Olá %s,

            Você foi convidado para se juntar ao Meu Jet!

            Para ativar sua conta, você precisará do link de ativação e da senha temporária abaixo:

            Link de ativação: %s

            Senha temporária: %s

            IMPORTANTE:
            - Este link é válido por 48 horas
            - Use o link E a senha temporária para ativar sua conta
            - No primeiro login, você será solicitado a criar uma nova senha de sua escolha
            - Após definir sua senha permanente, você terá acesso completo ao sistema

            Se você não esperava este convite, ignore este email.

            Atenciosamente,
            Equipe Meu Jet

            ---
            [DEV MODE] Este email NÃO foi enviado. Apenas logado.

            FLUXO TÉCNICO (Option 2):
            1. Admin gera convite → backend cria senha temporária aleatória (12 chars)
            2. Backend armazena hash BCrypt da senha temporária no BD
            3. Email enviado com token + senha temporária em texto plano
            4. Usuário acessa link de ativação e fornece token + senha temporária
            5. Backend valida: BCrypt.matches(providedPassword, storedHash)
            6. Se válido: cria usuário no Keycloak com senha temporária + UPDATE_PASSWORD required action
            7. Usuário faz primeiro login com senha temporária
            8. Keycloak FORÇA troca de senha (gerenciado pelo Keycloak com políticas de senha!)
            9. Usuário define senha permanente e tem acesso completo
            """, name, activationLink, temporaryPassword);
    }

    private String buildPasswordResetEmailBody(String name, String resetLink) {
        return String.format("""
            Olá %s,

            Recebemos uma solicitação para redefinir sua senha no Meu Jet.

            Clique no link abaixo para criar uma nova senha:

            %s

            Este link é válido por 24 horas.

            Se você não solicitou esta redefinição, ignore este email.
            Sua senha atual permanecerá inalterada.

            Atenciosamente,
            Equipe Meu Jet

            ---
            [DEV MODE] Este email NÃO foi enviado. Apenas logado.
            """, name, resetLink);
    }
}
