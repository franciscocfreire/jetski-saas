package com.jetski.locacoes.internal;

import com.jetski.locacoes.internal.gru.GruPagamentoStatus;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Gera o comprovante de pagamento da GRU (PDF) a partir dos dados do PagTesouro
 * (pix-stn/sonda). Mesmas informações da página "Imprimir comprovante" do PagTesouro.
 */
@Slf4j
@Service
public class GruComprovantePdfService {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Color VERDE = new Color(0x1B, 0x5E, 0x20);

    public byte[] gerar(GruPagamentoStatus p) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 48, 48, 48, 48);
        try {
            PdfWriter.getInstance(doc, baos);
            doc.open();

            Font titulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, VERDE);
            Paragraph ok = new Paragraph("Pagamento realizado com sucesso", titulo);
            ok.setAlignment(Element.ALIGN_CENTER);
            ok.setSpacingAfter(6);
            doc.add(ok);

            Font sub = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.DARK_GRAY);
            Paragraph aviso = new Paragraph(
                "Dúvidas relativas a pagamento, comprovante, produto ou serviço devem ser "
                + "dirigidas ao órgão público favorecido.", sub);
            aviso.setAlignment(Element.ALIGN_CENTER);
            aviso.setSpacingAfter(14);
            doc.add(aviso);

            Font h = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
            Paragraph dados = new Paragraph("Dados do Pagamento", h);
            dados.setSpacingAfter(8);
            doc.add(dados);

            PdfPTable t = new PdfPTable(2);
            t.setWidthPercentage(100);
            campo(t, "Descrição", nz(p.descricao()));
            campo(t, "Identificação do pagamento", nz(p.idPagamento()));
            campo(t, "Nome do contribuinte", nz(p.nomeContribuinte()));
            campo(t, "Forma de pagamento", nz(p.formaPagamento()));
            campo(t, "CPF do contribuinte", formatCpf(p.cpfContribuinte()));
            campo(t, "Número/ID da transação no prestador", nz(p.refTran()));
            campo(t, "Número de referência", nz(p.numeroReferencia()));
            campo(t, "Data do pagamento", p.dataPagamento() != null ? fmt(p.dataPagamento(), D) : "—");
            campo(t, "Valor total do serviço", formatValor(p.valor()));
            campo(t, "Data e hora da confirmação",
                p.dataPagamento() != null ? fmt(p.dataPagamento(), DT) : "—");
            doc.add(t);

            Font rod = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.GRAY);
            Paragraph fonte = new Paragraph(
                "\nComprovante emitido a partir do PagTesouro — Tesouro Nacional.", rod);
            fonte.setSpacingBefore(20);
            doc.add(fonte);

            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Falha ao gerar comprovante PDF da GRU", e);
            throw new IllegalStateException("Falha ao gerar comprovante da GRU", e);
        }
    }

    private void campo(PdfPTable t, String label, String valor) {
        Font fl = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
        Font fv = FontFactory.getFont(FontFactory.HELVETICA, 10);
        Phrase ph = new Phrase();
        ph.add(new Phrase(label + "\n", fl));
        ph.add(new Phrase(valor, fv));
        PdfPCell c = new PdfPCell(ph);
        c.setBorder(0);
        c.setPaddingBottom(10);
        t.addCell(c);
    }

    private static String fmt(Instant i, DateTimeFormatter f) {
        return f.format(i.atZone(ZONE));
    }

    private static String formatValor(BigDecimal v) {
        if (v == null) {
            return "—";
        }
        return "R$ " + v.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }

    private static String formatCpf(String cpf) {
        String d = cpf == null ? "" : cpf.replaceAll("\\D", "");
        if (d.length() != 11) {
            return nz(cpf);
        }
        return d.substring(0, 3) + "." + d.substring(3, 6) + "." + d.substring(6, 9) + "-" + d.substring(9);
    }

    private static String nz(String s) {
        return s == null || s.isBlank() ? "—" : s;
    }
}
