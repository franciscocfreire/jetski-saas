package com.jetski.shared.email;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

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
        String subject = "Você foi convidado para o Pega o Jet";
        String body = buildInvitationEmailBody(name, activationLink, temporaryPassword);

        // Store last email data for E2E testing
        lastEmail = new LastEmailData(to, name, subject, activationLink, temporaryPassword);
        log.info("📧 Last email data stored for E2E testing: to={}, magicToken={}", to,
            lastEmail.getMagicToken() != null ? lastEmail.getMagicToken().substring(0, 20) + "..." : "null");

        logAndSaveEmail(to, subject, body);
    }

    @Override
    public void sendPasswordResetEmail(String to, String name, String resetLink) {
        String subject = "Pega o Jet - Redefinição de senha";
        String body = buildPasswordResetEmailBody(name, resetLink);

        logAndSaveEmail(to, subject, body);
    }

    @Override
    public void sendEmailComAnexo(String to, String subject, String htmlBody,
                                  String attachmentName, byte[] attachment, String attachmentContentType) {
        int size = attachment == null ? 0 : attachment.length;
        String body = String.format("%s%n%n[ANEXO] %s (%s, %d bytes)",
            htmlBody, attachmentName, attachmentContentType, size);
        logAndSaveEmail(to, subject, body);
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

    private String buildInvitationEmailBody(String name, String activationLink, String temporaryPassword) {
        return String.format("""
            Olá %s,

            Você foi convidado para se juntar ao Pega o Jet!

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
            Equipe Pega o Jet

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

            Recebemos uma solicitação para redefinir sua senha no Pega o Jet.

            Clique no link abaixo para criar uma nova senha:

            %s

            Este link é válido por 24 horas.

            Se você não solicitou esta redefinição, ignore este email.
            Sua senha atual permanecerá inalterada.

            Atenciosamente,
            Equipe Pega o Jet

            ---
            [DEV MODE] Este email NÃO foi enviado. Apenas logado.
            """, name, resetLink);
    }
}
