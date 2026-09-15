package com.jetski.locacoes.internal;

import org.springframework.web.util.HtmlUtils;

/**
 * E-mail ao cliente com a GRU (taxa da Marinha da CHA-MTA-E): número, valor,
 * vencimento, QR Code do PIX (imagem inline {@code cid:}) e copia-e-cola.
 * Paleta e cabeçalho seguem os e-mails da plataforma (BRAND.md).
 */
final class GruEmailTemplate {

    /** Content-ID da imagem do QR embutida na mensagem. */
    static final String QR_CID = "qrpix";

    private GruEmailTemplate() {
    }

    record Dados(String nomeCliente, String loja, String gruNumero, String valor,
                 String vencimento, String pixCopiaECola, boolean comQr) {}

    static String assunto(String loja) {
        return (loja == null || loja.isBlank())
            ? "Sua GRU da Marinha — pague pelo PIX"
            : "Sua GRU da Marinha — " + loja;
    }

    static String html(Dados d) {
        String nome = esc(primeiroNome(d.nomeCliente()));
        String loja = esc(d.loja());

        StringBuilder linhas = new StringBuilder();
        linhas.append(linha("Número da GRU", esc(d.gruNumero())));
        if (d.valor() != null) {
            linhas.append(linha("Valor", "<strong style=\"font-size:18px;color:#12263F;\">" + esc(d.valor()) + "</strong>"));
        }
        if (d.vencimento() != null) {
            linhas.append(linha("Pague até", esc(d.vencimento())));
        }

        StringBuilder pix = new StringBuilder();
        if (d.pixCopiaECola() != null) {
            pix.append("""
                <h3 style="color:#1E4266;font-size:16px;margin:28px 0 8px 0;">Pague com PIX</h3>
                """);
            if (d.comQr()) {
                pix.append("""
                    <p style="margin:0 0 12px 0;">Abra o app do seu banco, escolha <strong>Pagar com PIX → QR Code</strong> e aponte a câmera:</p>
                    <div style="text-align:center;margin:0 0 16px 0;">
                        <img src="cid:%s" width="220" height="220" alt="QR Code do PIX"
                             style="display:inline-block;border:1px solid #E3D9C2;border-radius:8px;padding:8px;background:#FFFFFF;">
                    </div>
                    """.formatted(QR_CID));
            }
            pix.append("""
                <p style="margin:0 0 6px 0;">Ou use o <strong>PIX copia-e-cola</strong> — copie o código abaixo e cole no app do banco:</p>
                <div style="background:#FFFFFF;border:1px dashed #C9A24B;border-radius:6px;padding:12px;
                            font-family:'Courier New',monospace;font-size:12px;word-break:break-all;color:#12263F;">%s</div>
                <p style="color:#777;font-size:12px;margin:8px 0 0 0;">O pagamento vai para o Tesouro Nacional (GRU da Marinha do Brasil).</p>
                """.formatted(esc(d.pixCopiaECola())));
        }

        return """
            <!DOCTYPE html>
            <html>
            <head><meta charset="UTF-8"></head>
            <body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333; background-color: #FCFAF6; margin:0;">
                <div style="max-width: 600px; margin: 0 auto; padding: 20px;">
                    <p style="font-family: Georgia, 'Times New Roman', serif; font-size: 20px;
                              letter-spacing: 5px; color: #12263F; margin: 0 0 4px 0;">MEU&nbsp;JET</p>
                    <div style="height: 2px; width: 64px; background-color: #C9A24B; margin: 0 0 24px 0;"></div>

                    <h2 style="color: #1E4266; margin: 0 0 12px 0;">Sua GRU da Marinha está pronta</h2>
                    <p>Olá, %s!</p>
                    <p>Para emitir sua habilitação temporária <strong>CHA-MTA-E</strong> e liberar o passeio,
                       falta pagar a taxa da Marinha (GRU)%s.</p>

                    <div style="background-color: #F8F4EA; padding: 15px 18px; margin: 20px 0; border-left: 4px solid #C9A24B;">
                        <table role="presentation" cellpadding="0" cellspacing="0" style="width:100%%;border-collapse:collapse;">
                            %s
                        </table>
                    </div>
                    %s

                    <h3 style="color:#1E4266;font-size:16px;margin:28px 0 8px 0;">Depois de pagar</h3>
                    <p style="margin:0;">A confirmação chega à loja automaticamente — não precisa mandar comprovante.
                       Se pagar por outro meio, guarde o comprovante e apresente na loja.</p>

                    <hr style="border: none; border-top: 1px solid #E3D9C2; margin: 30px 0;">
                    <p style="color: #999; font-size: 12px; margin:0;">%s · enviado pelo Meu Jet</p>
                </div>
            </body>
            </html>
            """.formatted(nome, loja.isBlank() ? "" : " para a <strong>" + loja + "</strong>",
                linhas, pix, loja.isBlank() ? "Meu Jet" : loja);
    }

    private static String linha(String rotulo, String valorHtml) {
        return """
            <tr>
                <td style="padding:4px 12px 4px 0;color:#666;white-space:nowrap;vertical-align:top;">%s</td>
                <td style="padding:4px 0;color:#12263F;">%s</td>
            </tr>
            """.formatted(rotulo, valorHtml);
    }

    private static String primeiroNome(String nome) {
        if (nome == null || nome.isBlank()) {
            return "";
        }
        return nome.trim().split("\\s+")[0];
    }

    private static String esc(String s) {
        // Com charset: escapa só a marcação (&<>"'); acentos seguem legíveis no UTF-8.
        return s == null ? "" : HtmlUtils.htmlEscape(s, "UTF-8");
    }
}
