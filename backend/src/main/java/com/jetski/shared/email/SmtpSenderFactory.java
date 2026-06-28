package com.jetski.shared.email;

import jakarta.mail.internet.MimeMessage;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.util.Properties;

/**
 * Monta um {@link JavaMailSender} a partir das credenciais SMTP de um tenant e
 * envia mensagens HTML (com ou sem anexo) — usado para o envio com o servidor
 * próprio da empresa.
 */
@Component
public class SmtpSenderFactory {

    public JavaMailSender build(TenantSmtpResolver.SmtpSettings s) {
        JavaMailSenderImpl m = new JavaMailSenderImpl();
        m.setHost(s.host());
        m.setPort(s.port());
        m.setUsername(s.username());
        m.setPassword(s.password());
        Properties p = m.getJavaMailProperties();
        p.put("mail.transport.protocol", "smtp");
        p.put("mail.smtp.auth", "true");
        p.put("mail.smtp.starttls.enable", String.valueOf(s.starttls()));
        p.put("mail.smtp.starttls.required", String.valueOf(s.starttls()));
        p.put("mail.smtp.connectiontimeout", "5000");
        p.put("mail.smtp.timeout", "8000");
        p.put("mail.smtp.writetimeout", "8000");
        return m;
    }

    public void send(JavaMailSender sender, String from, String fromName, String to, String subject,
                     String html, String attachmentName, byte[] attachment, String attachmentContentType)
            throws Exception {
        MimeMessage message = sender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, attachment != null, "UTF-8");
        if (fromName != null && !fromName.isBlank()) {
            helper.setFrom(from, fromName);
        } else {
            helper.setFrom(from);
        }
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(html, true);
        if (attachment != null) {
            helper.addAttachment(attachmentName, new ByteArrayResource(attachment), attachmentContentType);
        }
        sender.send(message);
    }
}
