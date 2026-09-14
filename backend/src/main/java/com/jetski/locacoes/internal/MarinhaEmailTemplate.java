package com.jetski.locacoes.internal;

import com.jetski.locacoes.domain.DocumentoTipo;
import com.jetski.locacoes.domain.Documentos;

import java.util.List;
import java.util.UUID;

/**
 * Ofício do EAMA à Capitania dos Portos solicitando a emissão da CHA-MTA-E
 * (NORMAM-212/DPC, item 5.4.2 — ver {@code docs/normativos/}).
 *
 * <p>É o ÚNICO texto do e-mail à Marinha: emissão, reenvio pela loja e reenvio
 * pelo painel da EAMA emissora passam por aqui. Regras que vêm da norma:
 * <ul>
 *   <li>o anexo é um PDF único cujo nome é o nome completo do locatário + CPF
 *       (passaporte para estrangeiro) — {@link #nomeArquivo()};</li>
 *   <li>o e-mail sai (ou responde) pelo e-mail oficial do EAMA declarado no
 *       Anexo 5-A — o chamador usa {@link DadosOficio#emailOficial()} como Reply-To;</li>
 *   <li>o corpo lista os documentos exigidos (GRU, 5-C, 5-B, 1-C, identidade).</li>
 * </ul>
 * O número da reserva fica SEMPRE no final do assunto (referência interna,
 * pedido do produto); campos do EAMA não informados são omitidos da assinatura.
 */
public final class MarinhaEmailTemplate {

    private MarinhaEmailTemplate() {}

    /** Tudo que o ofício precisa. Campos nulos/brancos são tratados como ausentes. */
    public record DadosOficio(
            String eamaNome,
            String cnpj,
            String eamaRegistro,
            String responsavelNome,
            String telefone,
            String emailOficial,
            String locatarioNome,
            String documento,
            /** Como o documento é rotulado. {@code null} cai no {@code estrangeiro}. */
            DocumentoTipo documentoTipo,
            /** Anexos 5-B em inglês. Independe do tipo: estrangeiro residente tem CPF. */
            boolean estrangeiro,
            String gruNumero,
            UUID reservaId,
            List<String> anexos,
            String hashSha256,
            boolean reenvio,
            /**
             * Emissão delegada: a operadora afiliada que atendeu o locatário. Entra só na
             * assinatura ("{EAMA emissora} operado por {operadora}"); o remetente, o
             * credenciamento e o Reply-To continuam sendo os da EAMA. {@code null} = própria.
             */
            String operadoraNome
    ) {
        public DadosOficio {
            anexos = anexos == null ? List.of() : List.copyOf(anexos);
        }

        /** Emissão própria (sem operadora). */
        public DadosOficio(String eamaNome, String cnpj, String eamaRegistro, String responsavelNome,
                           String telefone, String emailOficial, String locatarioNome, String documento,
                           DocumentoTipo documentoTipo, boolean estrangeiro, String gruNumero, UUID reservaId,
                           List<String> anexos, String hashSha256, boolean reenvio) {
            this(eamaNome, cnpj, eamaRegistro, responsavelNome, telefone, emailOficial, locatarioNome,
                documento, documentoTipo, estrangeiro, gruNumero, reservaId, anexos, hashSha256, reenvio, null);
        }

        /** Código curto da reserva — o mesmo que o backoffice exibe ({@code #xxxxxxxx}). */
        public String reservaCodigo() {
            return reservaId != null ? "#" + reservaId.toString().substring(0, 8) : "#—";
        }

        /**
         * O rótulo vem do TIPO do documento, não da nacionalidade. Antes saía de
         * {@code estrangeiro} — um checkbox marcado três passos depois da
         * identificação — e um esquecimento mandava à Capitania um ofício
         * dizendo "CPF: AB123456".
         */
        String rotuloDocumento() {
            if (documentoTipo != null) {
                return documentoTipo.rotulo();
            }
            return estrangeiro ? "Passaporte" : "CPF";
        }

        /** Valor pontuado: o documento é guardado canônico, mas o ofício é um ofício. */
        String documentoExibicao() {
            String f = Documentos.formatar(documentoTipo, documento);
            return f != null ? f : documento;
        }
    }

    /**
     * {@code Solicitação de Emissão de CHA-MTA-E – NOME – CPF 000.000.000-00 – reserva #abcd1234}
     * (com {@code (reenvio)} antes da reserva quando for reenvio).
     */
    public static String assunto(DadosOficio d) {
        StringBuilder sb = new StringBuilder("Solicitação de Emissão de CHA-MTA-E – ")
            .append(nz(d.locatarioNome(), "Locatário"))
            .append(" – ").append(d.rotuloDocumento()).append(' ').append(nz(d.documentoExibicao(), "—"));
        if (d.reenvio()) sb.append(" (reenvio)");
        return sb.append(" – reserva ").append(d.reservaCodigo()).toString();
    }

    /**
     * Nome do PDF anexo exigido pela NORMAM-212 5.4.2: nome completo + CPF/passaporte.
     * A regra virou padrão do sistema — vive em {@link DocumentoNome}.
     */
    public static String nomeArquivo(DadosOficio d) {
        return DocumentoNome.de(d.locatarioNome(), d.documento());
    }

    public static String corpoHtml(DadosOficio d) {
        String eama = esc(nz(d.eamaNome(), "—"));
        StringBuilder sb = new StringBuilder();
        sb.append("<p>Prezados Senhores,</p>");
        sb.append("<p>O EAMA <b>").append(eama).append("</b>, devidamente credenciado");
        if (has(d.eamaRegistro())) {
            sb.append(" (credenciamento nº ").append(esc(d.eamaRegistro())).append(")");
        }
        sb.append(", encaminha para análise e emissão da Carteira de Habilitação de Motonauta Especial"
            + " – CHA-MTA-E, referente ao locatário abaixo:</p>");
        sb.append("<p>Nome: <b>").append(esc(nz(d.locatarioNome(), "—"))).append("</b><br>")
          .append(d.rotuloDocumento()).append(": <b>").append(esc(nz(d.documentoExibicao(), "—"))).append("</b></p>");
        if (d.reenvio()) {
            sb.append("<p><i>Reenvio da documentação já encaminhada anteriormente.</i></p>");
        }
        sb.append("<p>Seguem anexos os documentos exigidos pela NORMAM-212/DPC, item 5.4.2:</p><ul>");
        if (has(d.gruNumero())) {
            sb.append("<li>Número da GRU paga: <b>").append(esc(d.gruNumero())).append("</b></li>");
        }
        for (String a : d.anexos()) {
            sb.append("<li>").append(esc(a)).append("</li>");
        }
        sb.append("</ul>");
        sb.append("<p>Solicito, assim, o processamento da documentação e a emissão da respectiva CHA-MTA-E.</p>");
        sb.append("<p>Atenciosamente,<br>");
        if (has(d.responsavelNome())) sb.append("<b>").append(esc(d.responsavelNome())).append("</b><br>");
        sb.append("EAMA ").append(eama);
        if (has(d.cnpj())) sb.append("<br>CNPJ: ").append(esc(d.cnpj()));
        // Delegada: a operadora aparece só aqui, como quem opera em nome da EAMA.
        if (has(d.operadoraNome())) sb.append("<br>operado por <b>").append(esc(d.operadoraNome())).append("</b>");
        if (has(d.telefone())) {
            sb.append("<br>Telefone: ").append(esc(com.jetski.locacoes.domain.Telefones.formatar(d.telefone())));
        }
        if (has(d.emailOficial())) sb.append("<br>E-mail: ").append(esc(d.emailOficial()));
        sb.append("</p>");
        sb.append("<p style=\"font-size:12px;color:#666\">Referência interna: reserva ")
          .append(d.reservaCodigo());
        if (has(d.hashSha256())) {
            sb.append(" · SHA-256 do PDF: <code>").append(esc(d.hashSha256())).append("</code>");
        }
        sb.append("</p>");
        return sb.toString();
    }

    private static boolean has(String s) {
        return s != null && !s.isBlank();
    }

    private static String nz(String s, String fallback) {
        return has(s) ? s.trim() : fallback;
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
