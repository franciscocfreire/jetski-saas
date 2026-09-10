package com.jetski.locacoes.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Convenção única de nome de arquivo (NORMAM-212 5.4.2 promovida a padrão).
 * Antes cada caminho inventava o seu e o link abria tudo como "documento.pdf".
 */
@DisplayName("DocumentoNome — nome do PDF entregue")
class DocumentoNomeTest {

    @Test
    @DisplayName("Nome completo + CPF, como o anexo do ofício à Capitania")
    void nomeMaisDocumento() {
        assertThat(DocumentoNome.de("FELIPE H M", "49811586888"))
            .isEqualTo("FELIPE H M 49811586888.pdf");
    }

    @Test
    @DisplayName("Acentos ficam — o Content-Disposition os carrega em UTF-8")
    void mantemAcentos() {
        assertThat(DocumentoNome.de("Rogério Ferreira", "36744561849"))
            .isEqualTo("Rogério Ferreira 36744561849.pdf");
    }

    @Test
    @DisplayName("Prefixo distingue ficha e prévia do documento emitido")
    void comPrefixo() {
        assertThat(DocumentoNome.de("Ficha", "Ana Lima", "123"))
            .isEqualTo("Ficha Ana Lima 123.pdf");
        assertThat(DocumentoNome.de("Prévia Marinha", "Ana Lima", "123"))
            .isEqualTo("Prévia Marinha Ana Lima 123.pdf");
    }

    @Test
    @DisplayName("Separadores de caminho e caracteres proibidos saem (nome vem do usuário)")
    void sanitizaCaracteresProibidos() {
        assertThat(DocumentoNome.de("Ana/Maria:Lima*?", "123"))
            .isEqualTo("AnaMariaLima 123.pdf");
        assertThat(DocumentoNome.de("../../etc/passwd", "1"))
            .doesNotContain("/")
            .doesNotContain("\\");
    }

    @Test
    @DisplayName("Espaços repetidos colapsam; nome ausente cai no rótulo genérico")
    void colapsaEspacosETrataNulos() {
        assertThat(DocumentoNome.de("Ana   Paula", "123")).isEqualTo("Ana Paula 123.pdf");
        assertThat(DocumentoNome.de(null, "123")).isEqualTo("Locatario 123.pdf");
        assertThat(DocumentoNome.de("  ", "123")).isEqualTo("Locatario 123.pdf");
    }

    @Test
    @DisplayName("Estrangeiro: o passaporte ocupa o lugar do CPF")
    void estrangeiroUsaPassaporte() {
        assertThat(DocumentoNome.de("JOHN SMITH", "AB1234567"))
            .isEqualTo("JOHN SMITH AB1234567.pdf");
    }

    @Test
    @DisplayName("Sem documento, sobra só o nome (não deixa espaço solto antes do .pdf)")
    void semDocumento() {
        assertThat(DocumentoNome.de("Ana Lima", null)).isEqualTo("Ana Lima.pdf");
    }
}
