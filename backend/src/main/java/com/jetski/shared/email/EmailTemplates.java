package com.jetski.shared.email;

/**
 * Templates HTML compartilhados de email, usados por {@link SmtpEmailService} (prod)
 * e por {@link DevEmailService} (dev, enviando ao Mailpit) — garantindo que o email
 * inspecionado em dev é idêntico ao que o cliente recebe em produção.
 *
 * <p>Identidade visual "Meu Jet" náutico premium (ver BRAND.md): navy #1E4266 como
 * cor de marca/CTA, areia #F8F4EA para painéis, dourado #C9A24B apenas como acento
 * de borda (nunca texto pequeno sobre fundo claro).
 *
 * @author Jetski Team
 */
public final class EmailTemplates {

    private EmailTemplates() {
    }

    /** Assunto do email de convite/ativação. */
    public static final String INVITATION_SUBJECT = "Você foi convidado para o Meu Jet";

    /** Assunto do email de ativação de conta do CLIENTE (pré-conta de balcão). */
    public static final String CLIENTE_INVITATION_SUBJECT = "Ative sua conta no Meu Jet";

    /** Assunto do email de redefinição de senha. */
    public static final String PASSWORD_RESET_SUBJECT = "Meu Jet - Redefinição de senha";

    /** Assunto do email de notificação de nova empresa (super admin). */
    public static final String NEW_TENANT_SUBJECT = "Meu Jet - Nova empresa aguardando aprovação";

    /** Assuntos dos avisos de mudança de status da empresa (enviados aos admins do tenant). */
    public static final String TENANT_APPROVED_SUBJECT = "Meu Jet - Sua empresa foi liberada!";
    public static final String TENANT_SUSPENDED_SUBJECT = "Meu Jet - Acesso da sua empresa suspenso";
    public static final String TENANT_REACTIVATED_SUBJECT = "Meu Jet - Acesso da sua empresa reativado";

    /** Cabeçalho de marca compartilhado: wordmark em texto (imagens são bloqueadas por clientes de email). */
    private static final String BRAND_HEADER = """
                    <p style="font-family: Georgia, 'Times New Roman', serif; font-size: 20px;
                              letter-spacing: 5px; color: #12263F; margin: 0 0 4px 0;">
                        MEU&nbsp;JET
                    </p>
                    <div style="height: 2px; width: 64px; background-color: #C9A24B; margin: 0 0 24px 0;"></div>
            """;

    public static String newTenantNotificationHtml(String razaoSocial, String slug) {
        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
            </head>
            <body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333; background-color: #FCFAF6;">
                <div style="max-width: 600px; margin: 0 auto; padding: 20px;">
            %s
                    <h2 style="color: #1E4266;">Nova empresa aguardando aprovação</h2>

                    <p>Uma nova empresa se cadastrou no <strong>Meu Jet</strong> e está aguardando
                       liberação:</p>

                    <div style="background-color: #F8F4EA; padding: 15px; margin: 20px 0; border-left: 4px solid #C9A24B;">
                        <p style="margin: 5px 0;"><strong>Empresa:</strong> %s</p>
                        <p style="margin: 5px 0;"><strong>Identificador:</strong> %s</p>
                    </div>

                    <p>Acesse o painel de administração para aprovar ou bloquear esta empresa.</p>

                    <hr style="border: none; border-top: 1px solid #E3D9C2; margin: 30px 0;">

                    <p style="color: #999; font-size: 12px;">
                        Meu Jet — notificação automática de plataforma
                    </p>
                </div>
            </body>
            </html>
            """, BRAND_HEADER, razaoSocial, slug);
    }

    /** Assunto do aviso de mudança de status conforme a ação do evento. */
    public static String tenantStatusSubject(String acao) {
        return switch (acao) {
            case "TENANT_APPROVED" -> TENANT_APPROVED_SUBJECT;
            case "TENANT_SUSPENDED" -> TENANT_SUSPENDED_SUBJECT;
            case "TENANT_REACTIVATED" -> TENANT_REACTIVATED_SUBJECT;
            default -> throw new IllegalArgumentException("Ação de status desconhecida: " + acao);
        };
    }

    public static String tenantStatusHtml(String acao, String razaoSocial, String motivo) {
        String titulo;
        String mensagem;
        switch (acao) {
            case "TENANT_APPROVED" -> {
                titulo = "Sua empresa foi liberada!";
                mensagem = "A empresa <strong>%s</strong> foi aprovada no <strong>Meu Jet</strong>. "
                    + "Sua equipe já pode acessar o sistema normalmente — o período de teste começa agora.";
            }
            case "TENANT_SUSPENDED" -> {
                titulo = "Acesso suspenso";
                mensagem = "O acesso da empresa <strong>%s</strong> ao <strong>Meu Jet</strong> foi suspenso. "
                    + "Enquanto a suspensão durar, o sistema fica indisponível para a sua equipe.";
            }
            case "TENANT_REACTIVATED" -> {
                titulo = "Acesso reativado";
                mensagem = "O acesso da empresa <strong>%s</strong> ao <strong>Meu Jet</strong> foi reativado. "
                    + "Sua equipe já pode voltar a usar o sistema normalmente.";
            }
            default -> throw new IllegalArgumentException("Ação de status desconhecida: " + acao);
        }
        String blocoMotivo = (motivo == null || motivo.isBlank()) ? "" : String.format("""
                    <div style="background-color: #F8F4EA; padding: 15px; margin: 20px 0; border-left: 4px solid #C9A24B;">
                        <p style="margin: 5px 0;"><strong>Motivo:</strong> %s</p>
                    </div>
            """, motivo);
        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
            </head>
            <body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333; background-color: #FCFAF6;">
                <div style="max-width: 600px; margin: 0 auto; padding: 20px;">
            %s
                    <h2 style="color: #1E4266;">%s</h2>

                    <p>%s</p>
            %s
                    <p>Em caso de dúvidas, responda este e-mail ou fale com o suporte do Meu Jet.</p>

                    <hr style="border: none; border-top: 1px solid #E3D9C2; margin: 30px 0;">

                    <p style="color: #999; font-size: 12px;">
                        Meu Jet — notificação automática de plataforma
                    </p>
                </div>
            </body>
            </html>
            """, BRAND_HEADER, titulo, String.format(mensagem, razaoSocial), blocoMotivo);
    }

    public static String invitationHtml(String name, String activationLink, String temporaryPassword) {
        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
            </head>
            <body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333; background-color: #FCFAF6;">
                <div style="max-width: 600px; margin: 0 auto; padding: 20px;">
            %s
                    <h2 style="color: #1E4266;">Você foi convidado!</h2>

                    <p>Olá <strong>%s</strong>,</p>

                    <p>Você foi convidado para se juntar ao <strong>Meu Jet</strong>!</p>

                    <p>Para ativar sua conta, você precisará do link de ativação e da senha temporária abaixo:</p>

                    <div style="background-color: #F8F4EA; padding: 15px; margin: 20px 0; border-left: 4px solid #C9A24B;">
                        <p style="margin: 5px 0;"><strong>Senha temporária:</strong></p>
                        <p style="font-family: 'Courier New', monospace; font-size: 16px; color: #1E4266; margin: 5px 0;">
                            %s
                        </p>
                    </div>

                    <p style="text-align: center; margin: 30px 0;">
                        <a href="%s"
                           style="background-color: #1E4266; color: white; padding: 12px 24px;
                                  text-decoration: none; border-radius: 8px; display: inline-block; font-weight: bold;">
                            Ativar Conta
                        </a>
                    </p>

                    <div style="background-color: #FBF7EE; border-left: 4px solid #B78934; padding: 15px; margin: 20px 0;">
                        <p style="margin: 5px 0; font-weight: bold; color: #7A5A10;">IMPORTANTE:</p>
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

                    <hr style="border: none; border-top: 1px solid #E3D9C2; margin: 30px 0;">

                    <p style="color: #999; font-size: 12px;">
                        Atenciosamente,<br>
                        Equipe Meu Jet
                    </p>
                </div>
            </body>
            </html>
            """, BRAND_HEADER, name, temporaryPassword, activationLink);
    }

    /**
     * Convite de ativação da conta do CLIENTE (pré-conta criada no balcão).
     * Difere do {@link #invitationHtml} (staff): validade de 7 dias (TTL do
     * claim-token) e texto voltado ao portal do cliente — inclusive o caso de
     * quem já tem conta no Meu Jet (a senha atual continua valendo).
     */
    public static String clienteInvitationHtml(String name, String activationLink, String temporaryPassword) {
        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
            </head>
            <body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333; background-color: #FCFAF6;">
                <div style="max-width: 600px; margin: 0 auto; padding: 20px;">
            %s
                    <h2 style="color: #1E4266;">Sua conta no Meu Jet está quase pronta</h2>

                    <p>Olá <strong>%s</strong>,</p>

                    <p>A loja criou um cadastro para você no <strong>Meu Jet</strong>.
                       Ativando sua conta, você acompanha suas reservas, documentos e
                       histórico direto pelo portal.</p>

                    <p>Para ativar, clique no botão abaixo e informe a senha temporária:</p>

                    <div style="background-color: #F8F4EA; padding: 15px; margin: 20px 0; border-left: 4px solid #C9A24B;">
                        <p style="margin: 5px 0;"><strong>Senha temporária:</strong></p>
                        <p style="font-family: 'Courier New', monospace; font-size: 16px; color: #1E4266; margin: 5px 0;">
                            %s
                        </p>
                    </div>

                    <p style="text-align: center; margin: 30px 0;">
                        <a href="%s"
                           style="background-color: #1E4266; color: white; padding: 12px 24px;
                                  text-decoration: none; border-radius: 8px; display: inline-block; font-weight: bold;">
                            Ativar Conta
                        </a>
                    </p>

                    <div style="background-color: #FBF7EE; border-left: 4px solid #B78934; padding: 15px; margin: 20px 0;">
                        <p style="margin: 5px 0; font-weight: bold; color: #7A5A10;">IMPORTANTE:</p>
                        <ul style="margin: 10px 0; padding-left: 20px;">
                            <li>Este link é válido por 7 dias</li>
                            <li>A senha temporária serve para confirmar a ativação</li>
                            <li>Se este é seu primeiro acesso, você entrará com a senha temporária
                                e criará sua nova senha em seguida</li>
                            <li>Se você já tem conta no Meu Jet com este e-mail, sua senha atual
                                continua valendo — nada muda no seu acesso</li>
                        </ul>
                    </div>

                    <p style="color: #666; font-size: 14px;">
                        Se você não esperava este convite, ignore este email.
                    </p>

                    <hr style="border: none; border-top: 1px solid #E3D9C2; margin: 30px 0;">

                    <p style="color: #999; font-size: 12px;">
                        Atenciosamente,<br>
                        Equipe Meu Jet
                    </p>
                </div>
            </body>
            </html>
            """, BRAND_HEADER, name, temporaryPassword, activationLink);
    }

    public static String passwordResetHtml(String name, String resetLink) {
        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
            </head>
            <body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333; background-color: #FCFAF6;">
                <div style="max-width: 600px; margin: 0 auto; padding: 20px;">
            %s
                    <h2 style="color: #1E4266;">Redefinição de Senha</h2>

                    <p>Olá <strong>%s</strong>,</p>

                    <p>Recebemos uma solicitação para redefinir sua senha no <strong>Meu Jet</strong>.</p>

                    <p style="text-align: center; margin: 30px 0;">
                        <a href="%s"
                           style="background-color: #1E4266; color: white; padding: 12px 24px;
                                  text-decoration: none; border-radius: 8px; display: inline-block; font-weight: bold;">
                            Redefinir Senha
                        </a>
                    </p>

                    <p style="color: #666; font-size: 14px;">
                        Este link é válido por 24 horas.<br>
                        Se você não solicitou esta redefinição, ignore este email.
                        Sua senha atual permanecerá inalterada.
                    </p>

                    <hr style="border: none; border-top: 1px solid #E3D9C2; margin: 30px 0;">

                    <p style="color: #999; font-size: 12px;">
                        Atenciosamente,<br>
                        Equipe Meu Jet
                    </p>
                </div>
            </body>
            </html>
            """, BRAND_HEADER, name, resetLink);
    }
}
