package com.jetski.locacoes.internal;

import com.jetski.locacoes.domain.DocumentoTipo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ofício à Capitania (NORMAM-212/DPC 5.4.2): assunto com a reserva no FINAL, nome do
 * anexo "Nome completo + CPF.pdf", corpo com a lista de documentos e assinatura do EAMA
 * omitindo o que não foi informado.
 */
@DisplayName("MarinhaEmailTemplate (ofício NORMAM-212 5.4.2)")
class MarinhaEmailTemplateTest {

    private static final UUID RESERVA = UUID.fromString("abcd1234-0000-0000-0000-000000000001");

    private MarinhaEmailTemplate.DadosOficio completo(boolean reenvio) {
        return new MarinhaEmailTemplate.DadosOficio(
            "Jet Save Turismo Náutico LTDA", "65.455.888/0001-00", "Portaria 12/2026",
            "Maria da Silva", "(24) 3333-4444", "eama@jetsave.com.br",
            "Roberto Lima", "987.654.321-00", DocumentoTipo.CPF, false, "608931002438533333", RESERVA,
            List.of("Autodeclaração de Atestado de Saúde – Anexo 5-C",
                    "Atestado de Demonstração – Anexo 5-B (5-B-1 e 5-B-2)",
                    "Declaração de Residência – Anexo 1-C",
                    "Documento oficial de identificação, com fotografia"),
            "a".repeat(64), reenvio);
    }

    @Test
    @DisplayName("assunto: modelo do ofício com o número da reserva sempre no final")
    void assunto() {
        assertThat(MarinhaEmailTemplate.assunto(completo(false)))
            .isEqualTo("Solicitação de Emissão de CHA-MTA-E – Roberto Lima – CPF 987.654.321-00 – reserva #abcd1234");
        assertThat(MarinhaEmailTemplate.assunto(completo(true)))
            .isEqualTo("Solicitação de Emissão de CHA-MTA-E – Roberto Lima – CPF 987.654.321-00 (reenvio) – reserva #abcd1234")
            .endsWith("reserva #abcd1234");
    }

    @Test
    @DisplayName("estrangeiro: rótulo Passaporte no assunto, no corpo e no nome do arquivo")
    void estrangeiro() {
        var d = new MarinhaEmailTemplate.DadosOficio("EAMA X", null, null, null, null, null,
            "John Smith", "AB123456", DocumentoTipo.PASSAPORTE, true, "1", RESERVA, List.of(), null, false);
        assertThat(MarinhaEmailTemplate.assunto(d))
            .isEqualTo("Solicitação de Emissão de CHA-MTA-E – John Smith – Passaporte AB123456 – reserva #abcd1234");
        assertThat(MarinhaEmailTemplate.corpoHtml(d)).contains("Passaporte: <b>AB123456</b>");
        assertThat(MarinhaEmailTemplate.nomeArquivo(d)).isEqualTo("John Smith AB123456.pdf");
    }

    @Test
    @DisplayName("nome do arquivo = nome completo + CPF (5.4.2), sem caracteres proibidos, acentos mantidos")
    void nomeArquivo() {
        assertThat(MarinhaEmailTemplate.nomeArquivo(completo(false))).isEqualTo("Roberto Lima 987.654.321-00.pdf");
        var d = new MarinhaEmailTemplate.DadosOficio("E", null, null, null, null, null,
            "  José/Antônio  \"Jr\" <x>  ", "111.222.333-44", DocumentoTipo.CPF, false, null, RESERVA, List.of(), null, false);
        assertThat(MarinhaEmailTemplate.nomeArquivo(d)).isEqualTo("JoséAntônio Jr x 111.222.333-44.pdf");
    }

    @Test
    @DisplayName("corpo: ofício completo — EAMA, credenciamento, locatário, GRU, anexos, assinatura e referência")
    void corpoCompleto() {
        String html = MarinhaEmailTemplate.corpoHtml(completo(false));
        assertThat(html)
            .contains("Prezados Senhores")
            .contains("O EAMA <b>Jet Save Turismo Náutico LTDA</b>, devidamente credenciado (credenciamento nº Portaria 12/2026)")
            .contains("Carteira de Habilitação de Motonauta Especial – CHA-MTA-E")
            .contains("Nome: <b>Roberto Lima</b>")
            .contains("CPF: <b>987.654.321-00</b>")
            .contains("NORMAM-212/DPC, item 5.4.2")
            .contains("<li>Número da GRU paga: <b>608931002438533333</b></li>")
            .contains("<li>Autodeclaração de Atestado de Saúde – Anexo 5-C</li>")
            .contains("<li>Documento oficial de identificação, com fotografia</li>")
            .contains("Solicito, assim, o processamento da documentação e a emissão da respectiva CHA-MTA-E.")
            .contains("<b>Maria da Silva</b><br>EAMA Jet Save Turismo Náutico LTDA<br>CNPJ: 65.455.888/0001-00"
                + "<br>Telefone: (24) 3333-4444<br>E-mail: eama@jetsave.com.br")
            .contains("Referência interna: reserva #abcd1234")
            .contains("SHA-256 do PDF")
            .doesNotContain("Reenvio");
        assertThat(MarinhaEmailTemplate.corpoHtml(completo(true))).contains("Reenvio da documentação");
    }

    @Test
    @DisplayName("assinatura omite campos não informados (nunca placeholders) e escapa HTML")
    void camposAusentesEEscape() {
        var d = new MarinhaEmailTemplate.DadosOficio("Loja & Cia", null, null, null, null, null,
            "Ana <b>", "1", null, false, null, RESERVA, List.of("Anexo 5-C"), null, false);
        String html = MarinhaEmailTemplate.corpoHtml(d);
        assertThat(html)
            .contains("O EAMA <b>Loja &amp; Cia</b>, devidamente credenciado, encaminha")
            .contains("Nome: <b>Ana &lt;b&gt;</b>")
            .doesNotContain("CNPJ:").doesNotContain("Telefone:").doesNotContain("E-mail:")
            .doesNotContain("credenciamento nº").doesNotContain("GRU paga").doesNotContain("[")
            .doesNotContain("SHA-256");
    }
}
