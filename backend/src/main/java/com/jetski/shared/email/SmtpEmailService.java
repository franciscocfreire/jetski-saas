package com.jetski.shared.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

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

    @Value("${jetski.email.from:noreply@pegaojet.com.br}")
    private String fromEmail;

    @Value("${jetski.email.from-name:Pega o Jet}")
    private String fromName;

    @Override
    public void sendInvitationEmail(String to, String name, String activationLink, String temporaryPassword) {
        String subject = "Você foi convidado para o Pega o Jet";
        String body = buildInvitationEmailBody(name, activationLink, temporaryPassword);

        sendEmail(to, subject, body);
    }

    @Override
    public void sendPasswordResetEmail(String to, String name, String resetLink) {
        String subject = "Pega o Jet - Redefinição de senha";
        String body = buildPasswordResetEmailBody(name, resetLink);

        sendEmail(to, subject, body);
    }

    private void sendEmail(String to, String subject, String htmlBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail, fromName);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true); // true = HTML

            mailSender.send(message);
            log.info("Email sent successfully: to={}, subject={}", to, subject);

        } catch (MessagingException e) {
            log.error("Failed to send email: to={}, subject={}, error={}", to, subject, e.getMessage(), e);
            throw new RuntimeException("Failed to send email", e);
        } catch (Exception e) {
            log.error("Unexpected error sending email: to={}, subject={}", to, subject, e);
            throw new RuntimeException("Failed to send email", e);
        }
    }

    private String buildInvitationEmailBody(String name, String activationLink, String temporaryPassword) {
        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
            </head>
            <body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333;">
                <div style="max-width: 600px; margin: 0 auto; padding: 20px;">
                    <h2 style="color: #0ea5e9;">Você foi convidado!</h2>

                    <p>Olá <strong>%s</strong>,</p>

                    <p>Você foi convidado para se juntar ao <strong>Pega o Jet</strong>!</p>

                    <p>Para ativar sua conta, você precisará do link de ativação e da senha temporária abaixo:</p>

                    <div style="background-color: #f0f9ff; padding: 15px; margin: 20px 0; border-left: 4px solid #0ea5e9;">
                        <p style="margin: 5px 0;"><strong>Senha temporária:</strong></p>
                        <p style="font-family: 'Courier New', monospace; font-size: 16px; color: #0ea5e9; margin: 5px 0;">
                            %s
                        </p>
                    </div>

                    <p style="text-align: center; margin: 30px 0;">
                        <a href="%s"
                           style="background-color: #0ea5e9; color: white; padding: 12px 24px;
                                  text-decoration: none; border-radius: 8px; display: inline-block; font-weight: bold;">
                            Ativar Conta
                        </a>
                    </p>

                    <div style="background-color: #fff7ed; border-left: 4px solid #f97316; padding: 15px; margin: 20px 0;">
                        <p style="margin: 5px 0; font-weight: bold; color: #c2410c;">IMPORTANTE:</p>
                        <ul style="margin: 10px 0; padding-left: 20px;">
                            <li>Este link é válido por 48 horas</li>
                            <li>Use o link E a senha temporária para ativar sua conta</li>
                            <li>No primeiro login, você será solicitado a criar uma nova senha de sua escolha</li>
                            <li>Após definir sua senha permanente, você terá acesso completo ao sistema</li>
                        </ul>
                    </div>

                    <p style="color: #666; font-size: 14px;">
                        Se você não esperava este convite, ignore este email.
                    </p>

                    <hr style="border: none; border-top: 1px solid #eee; margin: 30px 0;">

                    <p style="color: #999; font-size: 12px;">
                        Atenciosamente,<br>
                        Equipe Pega o Jet
                    </p>
                </div>
            </body>
            </html>
            """, name, temporaryPassword, activationLink);
    }

    private String buildPasswordResetEmailBody(String name, String resetLink) {
        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
            </head>
            <body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333;">
                <div style="max-width: 600px; margin: 0 auto; padding: 20px;">
                    <h2 style="color: #0ea5e9;">Redefinição de Senha</h2>

                    <p>Olá <strong>%s</strong>,</p>

                    <p>Recebemos uma solicitação para redefinir sua senha no <strong>Pega o Jet</strong>.</p>

                    <p style="text-align: center; margin: 30px 0;">
                        <a href="%s"
                           style="background-color: #0ea5e9; color: white; padding: 12px 24px;
                                  text-decoration: none; border-radius: 8px; display: inline-block; font-weight: bold;">
                            Redefinir Senha
                        </a>
                    </p>

                    <p style="color: #666; font-size: 14px;">
                        Este link é válido por 24 horas.<br>
                        Se você não solicitou esta redefinição, ignore este email.
                        Sua senha atual permanecerá inalterada.
                    </p>

                    <hr style="border: none; border-top: 1px solid #eee; margin: 30px 0;">

                    <p style="color: #999; font-size: 12px;">
                        Atenciosamente,<br>
                        Equipe Pega o Jet
                    </p>
                </div>
            </body>
            </html>
            """, name, resetLink);
    }
}
