package com.jetski.locacoes.internal;

import com.jetski.locacoes.api.dto.FolioExtratoResponse;
import com.jetski.locacoes.api.dto.ReservaFichaResponse;
import com.jetski.shared.exception.BusinessException;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Ficha da reserva em PDF (A4) — primeiro PDF WHITE-LABEL: logo e dados da
 * loja no cabeçalho (fallback: nome da loja/identidade Meu Jet). Espelha as
 * seções da página de detalhe. Documento informativo — não fiscal.
 */
@Slf4j
@Service
public class ReservaFichaPdfService {

    private static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DATA_HORA =
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZONA);
    private static final Color NAVY = new Color(0x1E, 0x42, 0x66);
    private static final Color AREIA = new Color(0xF4, 0xEF, 0xE3);
    private static final Color CINZA_BORDA = new Color(0xE2, 0xE8, 0xF0);

    @Builder
    public record DadosFicha(
        String lojaNome, String lojaCnpj, String lojaCidade, byte[] logoBytes,
        ReservaFichaResponse ficha,
        String geradoPor, Instant geradoEm) {}

    public byte[] gerar(DadosFicha d) {
        try {
            ReservaFichaResponse f = d.ficha();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Document doc = new Document(PageSize.A4, 40, 40, 40, 44);
            PdfWriter.getInstance(doc, baos);
            doc.open();

            Font h1 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 15, NAVY);
            Font h2 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, NAVY);
            Font normal = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.DARK_GRAY);
            Font label = FontFactory.getFont(FontFactory.HELVETICA, 7.5f, new Color(0x6B, 0x72, 0x80));
            Font mono = FontFactory.getFont(FontFactory.COURIER, 9, Color.DARK_GRAY);
            Font totalFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, NAVY);

            // ---------- Header white-label: logo (ou nome) | título + loja ----------
            PdfPTable header = new PdfPTable(new float[]{1.2f, 2f});
            header.setWidthPercentage(100);
            header.setSpacingAfter(6);
            PdfPCell esq = new PdfPCell();
            esq.setBorder(Rectangle.NO_BORDER);
            esq.setVerticalAlignment(Element.ALIGN_MIDDLE);
            if (d.logoBytes() != null && d.logoBytes().length > 0) {
                Image logo = Image.getInstance(d.logoBytes());
                logo.scaleToFit(120, 48);
                esq.addElement(logo);
            } else {
                esq.addElement(new Paragraph(d.lojaNome() != null ? d.lojaNome() : "Meu Jet", h1));
            }
            header.addCell(esq);

            String codigo = f.reserva() != null && f.reserva().getId() != null
                ? f.reserva().getId().toString().substring(0, 8).toUpperCase(Locale.ROOT) : "—";
            PdfPCell dir = new PdfPCell();
            dir.setBorder(Rectangle.NO_BORDER);
            dir.setHorizontalAlignment(Element.ALIGN_RIGHT);
            Paragraph tit = new Paragraph("FICHA DA RESERVA  #" + codigo, h1);
            tit.setAlignment(Element.ALIGN_RIGHT);
            dir.addElement(tit);
            Paragraph lojaLinha = new Paragraph(
                nvl(d.lojaNome())
                    + (d.lojaCnpj() != null ? "  ·  CNPJ " + d.lojaCnpj() : "")
                    + (d.lojaCidade() != null ? "  ·  " + d.lojaCidade() : ""), normal);
            lojaLinha.setAlignment(Element.ALIGN_RIGHT);
            dir.addElement(lojaLinha);
            header.addCell(dir);
            doc.add(header);
            doc.add(regua());

            // ---------- Reserva + Cliente ----------
            doc.add(secao("Reserva", h2));
            PdfPTable info = tabelaInfo();
            var r = f.reserva();
            addPar(info, "Status", r != null && r.getStatus() != null ? r.getStatus().name() : null, label, normal);
            addPar(info, "Canal", r != null && r.getCanal() != null
                ? ("PORTAL".equals(r.getCanal()) ? "Portal (online)" : "Balcão") : null, label, normal);
            addPar(info, "Criada em", r != null ? fmt(r.getCreatedAt()) : null, label, normal);
            addPar(info, "Período", r != null && r.getDataInicio() != null
                ? DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").format(r.getDataInicio())
                    + (r.getDataFimPrevista() != null
                        ? " — " + DateTimeFormatter.ofPattern("HH:mm").format(r.getDataFimPrevista()) : "")
                : null, label, normal);
            doc.add(info);

            doc.add(secao("Cliente", h2));
            PdfPTable cli = tabelaInfo();
            var c = f.cliente();
            addPar(cli, "Nome", c != null ? c.nome() : null, label, normal);
            addPar(cli, "CPF", c != null ? c.documentoMascarado() : null, label, normal);
            addPar(cli, "Telefone/WhatsApp", c != null
                ? (c.whatsapp() != null ? c.whatsapp() : c.telefone()) : null, label, normal);
            addPar(cli, "E-mail", c != null ? c.email() : null, label, normal);
            doc.add(cli);

            doc.add(secao("Passeio", h2));
            PdfPTable pas = tabelaInfo();
            var p = f.passeio();
            addPar(pas, "Modelo", p != null ? p.modeloNome() : null, label, normal);
            addPar(pas, "Jetski", p != null ? p.jetskiSerie() : null, label, normal);
            addPar(pas, "Valor estimado", r != null ? brl(r.getValorTotal()) : null, label, normal);
            addPar(pas, "Sinal", r != null ? brl(r.getValorSinal()) : null, label, normal);
            doc.add(pas);

            // ---------- Financeiro ----------
            doc.add(secao("Financeiro (extrato da reserva)", h2));
            FolioExtratoResponse ex = f.extrato();
            if (ex == null || ex.lancamentos() == null || ex.lancamentos().isEmpty()) {
                doc.add(new Paragraph("Sem lançamentos registrados.", normal));
            } else {
                PdfPTable fin = new PdfPTable(new float[]{1.4f, 1.1f, 1.1f, 1f, 2.2f});
                fin.setWidthPercentage(100);
                fin.setSpacingAfter(4);
                for (String hCol : new String[]{"Data", "Tipo", "Forma", "Valor", "Observação"}) {
                    PdfPCell hc = new PdfPCell(new Phrase(hCol.toUpperCase(Locale.ROOT), label));
                    hc.setBorder(Rectangle.BOTTOM);
                    hc.setBorderColor(CINZA_BORDA);
                    hc.setPadding(4);
                    fin.addCell(hc);
                }
                for (var l : ex.lancamentos()) {
                    celula(fin, fmt(l.createdAt()), normal);
                    celula(fin, l.tipo(), normal);
                    celula(fin, nvl(l.forma()), normal);
                    celula(fin, brl(l.valor()), normal);
                    celula(fin, nvl(l.observacao()), normal);
                }
                doc.add(fin);

                PdfPTable tot = new PdfPTable(new float[]{3, 1.4f});
                tot.setWidthPercentage(100);
                tot.setSpacingAfter(4);
                linhaValor(tot, "Cobranças", ex.totalCobrancas(), h2, normal, false);
                linhaValor(tot, "Pagamentos", ex.totalPagamentos(), h2, normal, false);
                if (ex.totalEstornos() != null && ex.totalEstornos().signum() > 0) {
                    linhaValor(tot, "Estornos", ex.totalEstornos(), h2, normal, false);
                }
                linhaValor(tot, "SALDO", ex.saldo(), totalFont, totalFont, true);
                doc.add(tot);
            }

            // ---------- Habilitação & GRU ----------
            doc.add(secao("Habilitação & GRU (Marinha)", h2));
            var hab = f.habilitacao();
            if (hab == null) {
                doc.add(new Paragraph("Habilitação ainda não registrada.", normal));
            } else if (f.ciclo() == null) {
                doc.add(new Paragraph(
                    ("CHA".equals(hab.getVia()) ? "CHA do condutor" : nvl(hab.getVia()))
                        + (hab.getChaCategoria() != null ? " · " + hab.getChaCategoria() : "")
                        + (hab.getChaNumero() != null ? " · nº " + hab.getChaNumero() : "")
                        + (hab.getChaValidade() != null ? " · validade " + hab.getChaValidade() : ""),
                    normal));
            } else {
                var g = f.ciclo();
                PdfPTable ciclo = new PdfPTable(new float[]{0.35f, 3.2f, 2.6f});
                ciclo.setWidthPercentage(100);
                ciclo.setSpacingAfter(4);
                passo(ciclo, true, "GRU gerada",
                    "Nº " + g.gruNumero() + (g.gruValor() != null ? " · " + brl(g.gruValor()) : "")
                        + " · " + fmt(g.gruGeradaEm()), normal, mono);
                passo(ciclo, Boolean.TRUE.equals(g.gruPago()), "GRU paga",
                    Boolean.TRUE.equals(g.gruPago()) ? fmt(g.gruPagoEm()) : "aguardando pagamento", normal, mono);
                passo(ciclo, g.documentoEmitidoEm() != null, "Documentação emitida",
                    g.documentoEmitidoEm() != null ? fmt(g.documentoEmitidoEm()) : "pendente", normal, mono);
                passo(ciclo, g.marinhaEnviadaEm() != null, "E-mail à Marinha",
                    g.marinhaEnviadaEm() != null ? fmt(g.marinhaEnviadaEm()) : "não enviado", normal, mono);
                passo(ciclo, g.marinhaConfirmadaEm() != null, "Confirmada pela Marinha (devolutiva)",
                    g.marinhaConfirmadaEm() != null ? fmt(g.marinhaConfirmadaEm()) : "aguardando devolutiva", normal, mono);
                doc.add(ciclo);
            }

            // ---------- Termos & Documentos ----------
            doc.add(secao("Termos & Aceite", h2));
            var a = f.aceite();
            if (a == null) {
                doc.add(new Paragraph("Termo ainda não assinado.", normal));
            } else {
                PdfPTable ace = tabelaInfo();
                addPar(ace, "Método", "PAPEL".equals(a.getMetodo()) ? "Assinatura em papel" : "Assinatura digital", label, normal);
                addPar(ace, "Assinado em", fmt(a.getAceitoEm()), label, normal);
                addPar(ace, "Hash (integridade)", a.getHashSha256() != null
                    ? a.getHashSha256().substring(0, Math.min(24, a.getHashSha256().length())) + "…" : null, label, normal);
                addPar(ace, "Origem", a.getOrigem(), label, normal);
                doc.add(ace);
            }

            doc.add(secao("Documentos emitidos", h2));
            if (f.documentos() == null || f.documentos().isEmpty()) {
                doc.add(new Paragraph("Nenhum documento emitido para esta reserva.", normal));
            } else {
                PdfPTable docs = tabelaInfo();
                for (var de : f.documentos()) {
                    addPar(docs, "Emitido em " + fmt(de.getEmitidoEm()),
                        "SHA-256 " + (de.getHashSha256() != null
                            ? de.getHashSha256().substring(0, Math.min(20, de.getHashSha256().length())) + "…" : "—"),
                        label, mono);
                }
                doc.add(docs);
            }

            // ---------- Rodapé ----------
            Paragraph rodape = new Paragraph(
                "Gerado em " + fmt(d.geradoEm())
                    + (d.geradoPor() != null ? " · operador " + d.geradoPor() : "")
                    + " — documento informativo, não fiscal. Meu Jet.", label);
            rodape.setSpacingBefore(16);
            rodape.setAlignment(Element.ALIGN_CENTER);
            doc.add(rodape);

            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Falha ao gerar ficha da reserva: {}", e.getMessage(), e);
            throw new BusinessException("Não foi possível gerar a ficha agora — tente novamente.");
        }
    }

    // ---------- helpers ----------

    private static Paragraph secao(String titulo, Font h2) {
        Paragraph s = new Paragraph(titulo, h2);
        s.setSpacingBefore(12);
        s.setSpacingAfter(5);
        return s;
    }

    private static PdfPTable tabelaInfo() {
        PdfPTable t = new PdfPTable(2);
        t.setWidthPercentage(100);
        t.setSpacingAfter(2);
        return t;
    }

    private static com.lowagie.text.pdf.draw.LineSeparator regua() {
        return new com.lowagie.text.pdf.draw.LineSeparator(0.8f, 100, CINZA_BORDA, Element.ALIGN_CENTER, -2);
    }

    private static void addPar(PdfPTable t, String rotulo, String valor, Font label, Font normal) {
        PdfPCell c = new PdfPCell();
        c.setBorder(Rectangle.NO_BORDER);
        c.setPaddingBottom(6);
        c.addElement(new Paragraph(rotulo.toUpperCase(Locale.ROOT), label));
        c.addElement(new Paragraph(valor != null ? valor : "—", normal));
        t.addCell(c);
    }

    private static void celula(PdfPTable t, String texto, Font f) {
        PdfPCell c = new PdfPCell(new Phrase(texto != null ? texto : "—", f));
        c.setBorder(Rectangle.BOTTOM);
        c.setBorderColor(CINZA_BORDA);
        c.setPadding(4);
        t.addCell(c);
    }

    /** Passo do ciclo Marinha: "✓" verde-navy ou "—" cinza + título + detalhe. */
    private static void passo(PdfPTable t, boolean ok, String titulo, String detalhe,
                              Font normal, Font mono) {
        Font marca = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10,
            ok ? new Color(0x0B, 0x7A, 0x4B) : new Color(0x9C, 0xA3, 0xAF));
        for (PdfPCell c : new PdfPCell[]{
                new PdfPCell(new Phrase(ok ? "✓" : "—", marca)),
                new PdfPCell(new Phrase(titulo, normal)),
                new PdfPCell(new Phrase(detalhe != null ? detalhe : "—", mono))}) {
            c.setBorder(Rectangle.NO_BORDER);
            c.setPaddingBottom(5);
            t.addCell(c);
        }
    }

    private static void linhaValor(PdfPTable t, String rotulo, BigDecimal valor,
                                   Font fLabel, Font fValor, boolean destaque) {
        PdfPCell l = new PdfPCell(new Phrase(rotulo, fLabel));
        PdfPCell v = new PdfPCell(new Phrase(brl(valor), fValor));
        for (PdfPCell c : new PdfPCell[]{l, v}) {
            c.setBorder(Rectangle.TOP);
            c.setBorderColor(CINZA_BORDA);
            c.setPadding(6);
            if (destaque) c.setBackgroundColor(AREIA);
        }
        v.setHorizontalAlignment(Element.ALIGN_RIGHT);
        t.addCell(l);
        t.addCell(v);
    }

    private static String fmt(Instant i) {
        return i != null ? DATA_HORA.format(i) : "—";
    }

    private static String nvl(String s) {
        return s != null && !s.isBlank() ? s : "—";
    }

    private static String brl(BigDecimal v) {
        if (v == null) return "—";
        return "R$ " + String.format(Locale.of("pt", "BR"), "%,.2f", v);
    }
}
