package com.jetski.shared.email;

/**
 * Templates HTML compartilhados de email, usados por {@link SmtpEmailService} (prod)
 * e por {@link DevEmailService} (dev, enviando ao Mailpit) — garantindo que o email
 * inspecionado em dev é idêntico ao que o cliente recebe em produção.
 *
 * @author Jetski Team
 */
public final class EmailTemplates {

    private EmailTemplates() {
    }

    /** Assunto do email de convite/ativação. */
    public static final String INVITATION_SUBJECT = "Você foi convidado para o Pega o Jet";

    /** Assunto do email de redefinição de senha. */
    public static final String PASSWORD_RESET_SUBJECT = "Pega o Jet - Redefinição de senha";

    /** Assunto do email de notificação de nova empresa (super admin). */
    public static final String NEW_TENANT_SUBJECT = "Pega o Jet - Nova empresa aguardando aprovação";

    public static String newTenantNotificationHtml(String razaoSocial, String slug) {
        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
            </head>
            <body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333;">
                <div style="max-width: 600px; margin: 0 auto; padding: 20px;">
                    <h2 style="color: #0ea5e9;">Nova empresa aguardando aprovação</h2>

                    <p>Uma nova empresa se cadastrou no <strong>Pega o Jet</strong> e está aguardando
                       liberação:</p>

                    <div style="background-color: #f0f9ff; padding: 15px; margin: 20px 0; border-left: 4px solid #0ea5e9;">
                        <p style="margin: 5px 0;"><strong>Empresa:</strong> %s</p>
                        <p style="margin: 5px 0;"><strong>Identificador:</strong> %s</p>
                    </div>

                    <p>Acesse o painel de administração para aprovar ou bloquear esta empresa.</p>

                    <hr style="border: none; border-top: 1px solid #eee; margin: 30px 0;">

                    <p style="color: #999; font-size: 12px;">
                        Pega o Jet — notificação automática de plataforma
                    </p>
                </div>
            </body>
            </html>
            """, razaoSocial, slug);
    }

    public static String invitationHtml(String name, String activationLink, String temporaryPassword) {
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

    public static String passwordResetHtml(String name, String resetLink) {
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
