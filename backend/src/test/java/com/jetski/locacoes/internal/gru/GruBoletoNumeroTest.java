package com.jetski.locacoes.internal.gru;

import com.lowagie.text.Document;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GruClient.extrairNumeroReferencia - número do boleto a partir do PDF")
class GruBoletoNumeroTest {

    /** Monta um PDF de teste com o texto informado. */
    private static byte[] pdfCom(String texto) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document();
        PdfWriter.getInstance(doc, baos);
        doc.open();
        doc.add(new Paragraph(texto));
        doc.close();
        return baos.toByteArray();
    }

    @Test
    @DisplayName("extrai o número de referência (60+orgao+10 dígitos) do PDF")
    void extrai() throws Exception {
        byte[] pdf = pdfCom("Numero de Referencia: 60893100226022026 - GRU CHA");
        assertThat(GruClient.extrairNumeroReferencia(pdf, "89310")).isEqualTo("60893100226022026");
    }

    @Test
    @DisplayName("PDF sem o número → null (best-effort, sem regressão)")
    void semNumero() throws Exception {
        byte[] pdf = pdfCom("Boleto sem número de referência aqui.");
        assertThat(GruClient.extrairNumeroReferencia(pdf, "89310")).isNull();
    }

    @Test
    @DisplayName("não casa número de outro órgão")
    void outroOrgao() throws Exception {
        byte[] pdf = pdfCom("Numero: 60123450226022026");
        assertThat(GruClient.extrairNumeroReferencia(pdf, "89310")).isNull();
    }
}
