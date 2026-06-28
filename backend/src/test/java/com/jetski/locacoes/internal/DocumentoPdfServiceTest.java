package com.jetski.locacoes.internal;

import com.lowagie.text.pdf.PdfReader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spike F0.1 — prova que o OpenPDF gera o Termo fiel + hash, com imagem de assinatura.
 * Gera também {@code target/termo-spike.pdf} para inspeção visual manual.
 */
@DisplayName("DocumentoPdfService - Spike OpenPDF (Termo de Responsabilidade)")
class DocumentoPdfServiceTest {

    private final DocumentoPdfService service = new DocumentoPdfService();

    @Test
    @DisplayName("gera o Termo com assinatura e hash SHA-256, e é um PDF válido")
    void geraTermoComAssinatura() throws Exception {
        var dados = new DocumentoPdfService.DadosTermo(
                "Roberto Lima",
                "987.654.321-00",
                "Jet Save Turismo Náutico LTDA",
                "65.455.888/0001-00",
                "Angra dos Reis",
                "16/06/2026");

        byte[] assinatura = assinaturaMockPng("Roberto Lima");

        DocumentoPdfService.DocumentoPdf pdf = service.gerarTermoResponsabilidade(dados, assinatura);

        // É um PDF válido
        assertThat(new String(pdf.conteudo(), 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        assertThat(pdf.conteudo().length).isGreaterThan(1500);
        // Hash SHA-256 em hex (64 chars)
        assertThat(pdf.sha256()).matches("^[0-9a-f]{64}$");

        // Salva para inspeção visual manual (target/ é volátil, não versionado)
        Path out = Paths.get("target", "termo-spike.pdf");
        Files.createDirectories(out.getParent());
        Files.write(out, pdf.conteudo());
    }

    @Test
    @DisplayName("gera sem assinatura (caminho null) ainda produz PDF válido")
    void geraSemAssinatura() {
        var dados = new DocumentoPdfService.DadosTermo(
                "Ana Martins", "111.222.333-44", "Loja Náutica X",
                "00.000.000/0001-00", "Paraty", "16/06/2026");

        DocumentoPdfService.DocumentoPdf pdf = service.gerarTermoResponsabilidade(dados, null);

        assertThat(new String(pdf.conteudo(), 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        assertThat(pdf.sha256()).hasSize(64);
    }

    @Test
    @DisplayName("Consolidado EMA (com residência) gera os 5 anexos: 1-C, 5-C, 5-B-1, 5-B-2, Termo")
    void consolidadoEma() throws Exception {
        DocumentoPdfService.DocumentoPdf pdf =
                service.gerarDocumentoConsolidado(dados("EMA", true), assinaturaMockPng("Roberto Lima"));

        assertThat(new String(pdf.conteudo(), 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        assertThat(pdf.sha256()).matches("^[0-9a-f]{64}$");
        // ≥5 (cada anexo numa seção; 5-B-2 com todo o conteúdo oficial pode transbordar p/ 2 págs)
        assertThat(new PdfReader(pdf.conteudo()).getNumberOfPages()).isGreaterThanOrEqualTo(5);

        Path out = Paths.get("target", "documento-ema.pdf");
        Files.createDirectories(out.getParent());
        Files.write(out, pdf.conteudo());
    }

    @Test
    @DisplayName("Consolidado CHA gera 1 página (apenas o Termo)")
    void consolidadoCha() throws Exception {
        DocumentoPdfService.DocumentoPdf pdf =
                service.gerarDocumentoConsolidado(dados("CHA", false), null);

        assertThat(new String(pdf.conteudo(), 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        assertThat(new PdfReader(pdf.conteudo()).getNumberOfPages()).isEqualTo(1);
    }

    @Test
    @DisplayName("Anexo do cliente em PDF (ex.: identidade) é mesclado como página, não embutido como imagem")
    void anexoPdfMesclado() throws Exception {
        // 1 página de Termo (CHA) + 1 página vinda do anexo PDF = 2.
        byte[] anexoPdf = service.gerarTermoResponsabilidade(
                new DocumentoPdfService.DadosTermo("X", "1", "Y", "2", "Z", "16/06/2026"), null).conteudo();
        DocumentoPdfService.DocumentoPdf pdf = service.gerarDocumentoConsolidado(
                dados("CHA", false), null,
                java.util.List.of(new DocumentoPdfService.AnexoImagem("Identidade (PDF)", anexoPdf)));

        assertThat(new String(pdf.conteudo(), 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        assertThat(new PdfReader(pdf.conteudo()).getNumberOfPages()).isEqualTo(2);
    }

    @Test
    @DisplayName("Anexo com bytes ilegíveis (nem imagem nem PDF) é ignorado sem derrubar a geração")
    void anexoIlegivelIgnorado() {
        DocumentoPdfService.DocumentoPdf pdf = service.gerarDocumentoConsolidado(
                dados("CHA", false), null,
                java.util.List.of(new DocumentoPdfService.AnexoImagem("Lixo", "não-é-imagem".getBytes())));

        assertThat(new String(pdf.conteudo(), 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }

    private static DocumentoPdfService.DadosDocumento dados(String via, boolean residencia) {
        return new DocumentoPdfService.DadosDocumento(
                "Roberto Lima", "987.654.321-00", "12.345.678-9", "DETRAN/RJ",
                "brasileira", "Rio de Janeiro/RJ", "(21) 3030-1020", "(21) 98888-1234", "roberto@email.com",
                "Av. Paulista, 1500, ap. 902 - Bela Vista", "São Paulo/SP", "01310-100",
                "Jet Save Turismo Náutico LTDA", "65.455.888/0001-00",
                "Angra dos Reis", "16 de junho de 2026", "16/06/2026",
                via, residencia, false, false, true,
                "Carlos Mendes", "98.765.432-1", "SSP/RJ", "111.222.333-44", "MTA-1234567",
                "10/05/2020", null,
                "2026-000482-19", "23,13", true);
    }

    /** Gera um PNG simples simulando uma assinatura (headless-safe). */
    private static byte[] assinaturaMockPng(String nome) throws Exception {
        BufferedImage img = new BufferedImage(300, 80, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 300, 80);
        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(2f));
        g.drawLine(20, 60, 90, 22);
        g.drawLine(90, 22, 160, 60);
        g.drawLine(160, 60, 240, 26);
        g.setFont(new Font("Serif", Font.ITALIC, 20));
        g.drawString(nome, 30, 52);
        g.dispose();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        return baos.toByteArray();
    }
}
