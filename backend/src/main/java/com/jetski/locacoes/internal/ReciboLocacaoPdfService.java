package com.jetski.locacoes.internal;

import com.jetski.shared.exception.BusinessException;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Recibo da locação para o CLIENTE (backlog P4): resumo pós-checkout com
 * loja, embarcação, tempos e valores. Documento informativo — não fiscal.
 */
@Slf4j
@Service
public class ReciboLocacaoPdfService {

    private static final DateTimeFormatter DATA_HORA =
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Color NAVY = new Color(0x1E, 0x42, 0x66);
    private static final Color AREIA = new Color(0xF4, 0xEF, 0xE3);

    public record DadosRecibo(
        String lojaNome, String lojaCnpj, String lojaCidade,
        String clienteNome, String clienteCpfMascarado,
        String locacaoId, String modeloNome, String jetskiSerie,
        LocalDateTime checkIn, LocalDateTime checkOut,
        Integer minutosUsados, Integer minutosFaturaveis,
        BigDecimal valorBase, BigDecimal valorItensOpcionais,
        BigDecimal combustivelCusto, BigDecimal valorTotal) {}

    public byte[] gerar(DadosRecibo d) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Document doc = new Document(PageSize.A5, 40, 40, 44, 44);
            PdfWriter.getInstance(doc, baos);
            doc.open();

            Font h1 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, NAVY);
            Font h2 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, NAVY);
            Font normal = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.DARK_GRAY);
            Font label = FontFactory.getFont(FontFactory.HELVETICA, 8, new Color(0x6B, 0x72, 0x80));
            Font totalFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, NAVY);

            Paragraph titulo = new Paragraph("RECIBO DE LOCAÇÃO", h1);
            titulo.setAlignment(Element.ALIGN_CENTER);
            doc.add(titulo);
            Paragraph loja = new Paragraph(
                d.lojaNome() + (d.lojaCnpj() != null ? "  ·  CNPJ " + d.lojaCnpj() : "")
                    + (d.lojaCidade() != null ? "  ·  " + d.lojaCidade() : ""), normal);
            loja.setAlignment(Element.ALIGN_CENTER);
            loja.setSpacingAfter(14);
            doc.add(loja);

            PdfPTable info = new PdfPTable(2);
            info.setWidthPercentage(100);
            info.setSpacingAfter(10);
            addPar(info, "Cliente", d.clienteNome(), label, normal);
            addPar(info, "CPF", d.clienteCpfMascarado() != null ? d.clienteCpfMascarado() : "—", label, normal);
            addPar(info, "Embarcação", d.modeloNome() + (d.jetskiSerie() != null ? " (" + d.jetskiSerie() + ")" : ""), label, normal);
            addPar(info, "Locação nº", d.locacaoId(), label, normal);
            addPar(info, "Check-in", d.checkIn() != null ? DATA_HORA.format(d.checkIn()) : "—", label, normal);
            addPar(info, "Check-out", d.checkOut() != null ? DATA_HORA.format(d.checkOut()) : "—", label, normal);
            addPar(info, "Tempo utilizado", d.minutosUsados() != null ? d.minutosUsados() + " min" : "—", label, normal);
            addPar(info, "Tempo faturável", d.minutosFaturaveis() != null ? d.minutosFaturaveis() + " min" : "—", label, normal);
            doc.add(info);

            PdfPTable valores = new PdfPTable(new float[]{3, 1.4f});
            valores.setWidthPercentage(100);
            valores.setSpacingAfter(4);
            linhaValor(valores, "Valor da locação", d.valorBase(), h2, normal, false);
            if (positivo(d.valorItensOpcionais())) {
                linhaValor(valores, "Itens opcionais", d.valorItensOpcionais(), h2, normal, false);
            }
            if (positivo(d.combustivelCusto())) {
                linhaValor(valores, "Combustível", d.combustivelCusto(), h2, normal, false);
            }
            linhaValor(valores, "TOTAL", d.valorTotal(), totalFont, totalFont, true);
            doc.add(valores);

            Paragraph rodape = new Paragraph(
                "Documento informativo gerado pelo portal Meu Jet — não substitui documento fiscal.",
                label);
            rodape.setSpacingBefore(16);
            rodape.setAlignment(Element.ALIGN_CENTER);
            doc.add(rodape);

            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Falha ao gerar recibo de locação: {}", e.getMessage(), e);
            throw new BusinessException("Não foi possível gerar o recibo agora — tente novamente.");
        }
    }

    private static void addPar(PdfPTable t, String rotulo, String valor, Font label, Font normal) {
        PdfPCell c = new PdfPCell();
        c.setBorder(Rectangle.NO_BORDER);
        c.setPaddingBottom(7);
        c.addElement(new Paragraph(rotulo.toUpperCase(Locale.ROOT), label));
        c.addElement(new Paragraph(valor != null ? valor : "—", normal));
        t.addCell(c);
    }

    private static void linhaValor(PdfPTable t, String rotulo, BigDecimal valor,
                                   Font fLabel, Font fValor, boolean destaque) {
        PdfPCell l = new PdfPCell(new Phrase(rotulo, fLabel));
        PdfPCell v = new PdfPCell(new Phrase(brl(valor), fValor));
        for (PdfPCell c : new PdfPCell[]{l, v}) {
            c.setBorder(Rectangle.TOP);
            c.setBorderColor(new Color(0xE2, 0xE8, 0xF0));
            c.setPadding(6);
            if (destaque) c.setBackgroundColor(AREIA);
        }
        v.setHorizontalAlignment(Element.ALIGN_RIGHT);
        t.addCell(l);
        t.addCell(v);
    }

    private static boolean positivo(BigDecimal v) {
        return v != null && v.signum() > 0;
    }

    private static String brl(BigDecimal v) {
        if (v == null) return "—";
        return "R$ " + String.format(Locale.of("pt", "BR"), "%,.2f", v);
    }
}
