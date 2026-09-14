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
        // Mesmos valores do SMTP da plataforma: o servidor próprio da empresa costuma
        // ser hospedagem compartilhada, tipicamente mais lenta que o Gmail. O timeout
        // de leitura é o que estoura ao esperar o "250 OK" de um anexo de ~1 MB.
        p.put("mail.smtp.connectiontimeout", "10000");
        p.put("mail.smtp.timeout", "30000");
        p.put("mail.smtp.writetimeout", "30000");
        return m;
    }

    /**
     * HTML com uma imagem PNG embutida (Content-ID) — o HTML referencia {@code cid:<contentId>}.
     * Clientes de e-mail bloqueiam {@code data:} em {@code <img>}; a parte inline é o que o Gmail mostra.
     */
    public void sendComImagemInline(JavaMailSender sender, String from, String fromName, String to,
                                    String subject, String html, String contentId, byte[] png)
            throws Exception {
        MimeMessage message = sender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        if (fromName != null && !fromName.isBlank()) {
            helper.setFrom(from, fromName);
        } else {
            helper.setFrom(from);
        }
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(html, true);
        helper.addInline(contentId, new ByteArrayResource(png), "image/png");
        sender.send(message);
    }

    public void send(JavaMailSender sender, String from, String fromName, String to, String subject,
                     String html, String attachmentName, byte[] attachment, String attachmentContentType)
            throws Exception {
        send(sender, from, fromName, to, subject, html, attachmentName, attachment, attachmentContentType, null);
    }

    public void send(JavaMailSender sender, String from, String fromName, String to, String subject,
                     String html, String attachmentName, byte[] attachment, String attachmentContentType,
                     String replyTo)
            throws Exception {
        send(sender, from, fromName, to, subject, html, attachmentName, attachment, attachmentContentType,
            replyTo, null);
    }

    /** Idem, com um endereço em cópia (Cc) opcional. */
    public void send(JavaMailSender sender, String from, String fromName, String to, String subject,
                     String html, String attachmentName, byte[] attachment, String attachmentContentType,
                     String replyTo, String cc)
            throws Exception {
        MimeMessage message = sender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, attachment != null, "UTF-8");
        if (fromName != null && !fromName.isBlank()) {
            helper.setFrom(from, fromName);
        } else {
            helper.setFrom(from);
        }
        helper.setTo(to);
        if (cc != null && !cc.isBlank() && !cc.equalsIgnoreCase(to)) {
            helper.setCc(cc);
        }
        if (replyTo != null && !replyTo.isBlank()) {
            helper.setReplyTo(replyTo);
        }
        helper.setSubject(subject);
        helper.setText(html, true);
        if (attachment != null) {
            helper.addAttachment(attachmentName, new ByteArrayResource(attachment), attachmentContentType);
        }
        sender.send(message);
    }
}
