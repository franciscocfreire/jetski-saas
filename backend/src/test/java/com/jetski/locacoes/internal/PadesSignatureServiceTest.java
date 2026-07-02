package com.jetski.locacoes.internal;

import com.jetski.locacoes.internal.repository.AssinaturaCertificadoRepository;
import com.jetski.shared.security.SecretCipher;
import com.lowagie.text.Document;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.AcroFields;
import com.lowagie.text.pdf.PdfPKCS7;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.PdfWriter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * C2 — assinatura digital PAdES: gera o certificado, assina um PDF e verifica a
 * validade + cobertura da assinatura (tamper-evidence). Sem TSA (evita rede).
 */
@DisplayName("PadesSignatureService (C2)")
class PadesSignatureServiceTest {

    private final AssinaturaCertificadoRepository repo = mock(AssinaturaCertificadoRepository.class);
    private final SecretCipher cipher = mock(SecretCipher.class);
    private final PadesSignatureService service = new PadesSignatureService(repo, cipher);

    @Test
    @DisplayName("assina o PDF e a assinatura verifica + cobre todo o documento")
    void assinaEVerifica() throws Exception {
        when(repo.findFirstByOrderByCreatedAtDesc()).thenReturn(Optional.empty());
        when(cipher.encrypt(anyString())).thenAnswer(i -> i.getArgument(0));

        byte[] pdf = pdfSimples();
        byte[] assinado = service.assinar(pdf, null); // sem TSA

        assertThat(new String(assinado, 0, 5)).isEqualTo("%PDF-");
        assertThat(assinado.length).isGreaterThan(pdf.length);

        PdfReader reader = new PdfReader(assinado);
        AcroFields af = reader.getAcroFields();
        List<String> nomes = af.getSignatureNames();
        assertThat(nomes).isNotEmpty();
        PdfPKCS7 pk7 = af.verifySignature(nomes.get(0));
        assertThat(pk7.verify()).as("assinatura criptograficamente válida").isTrue();
        assertThat(af.signatureCoversWholeDocument(nomes.get(0)))
            .as("assinatura cobre todo o documento").isTrue();

        Path out = Paths.get("target", "documento-assinado.pdf");
        Files.createDirectories(out.getParent());
        Files.write(out, assinado);
    }

    private static byte[] pdfSimples() throws Exception {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        Document d = new Document();
        PdfWriter.getInstance(d, b);
        d.open();
        d.add(new Paragraph("Documento de teste para assinatura PAdES."));
        d.close();
        return b.toByteArray();
    }
}
