package com.jetski.locacoes.internal;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.ListItem;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Geração de documentos em PDF (OpenPDF) para a emissão do balcão.
 *
 * <p>{@link #gerarDocumentoConsolidado} monta o PDF consolidado fiel aos modelos
 * da NORMAM-212/DPC: Anexos <b>1-C</b> (residência, condicional), <b>5-C</b>
 * (saúde), <b>5-B-1</b> (atestado EAMA) e <b>5-B-2</b> (declaração do locatário,
 * regras a–j) + <b>Termo de Responsabilidade</b> da loja. Para via CHA, apenas o
 * Termo. A assinatura capturada (PNG) é embutida onde o locatário assina.
 *
 * <p>Espelho visual: {@code frontend/portal-cliente/app/staff/documento}.
 * Retorna bytes + SHA-256 (integridade/arquivamento).
 */
@Service
@Slf4j
public class DocumentoPdfService {

    /** Dados mínimos para o Termo de Responsabilidade (compat spike F0.1). */
    public record DadosTermo(
            String nomeCliente,
            String cpfCliente,
            String razaoSocialLoja,
            String cnpjLoja,
            String local,
            String data
    ) {}

    /** Dados completos para o PDF consolidado (anexos + termo). */
    public record DadosDocumento(
            // Cliente / locatário
            String nomeCliente, String cpfCliente, String identidade, String orgaoEmissor,
            String nacionalidade, String naturalidade, String telefone, String celular, String email,
            String endereco, String cidadeUf, String cep,
            // Loja (tenant)
            String razaoSocialLoja, String cnpjLoja,
            String local, String data, String dataCurta,
            // Habilitação
            String via,                 // CHA | EMA
            boolean incluirResidencia,  // renderiza Anexo 1-C
            boolean usaLentes, boolean usaAparelho,   // 5-C
            boolean semExperiencia,     // 5-B-2
            // Instrutor (5-B-1)
            String instrutorNome, String instrutorId, String instrutorOrgao,
            String instrutorCpf, String instrutorCha,
            // GRU
            String gruNumero, String gruValor
    ) {}

    /** Resultado: bytes do PDF + hash SHA-256 (hex). */
    public record DocumentoPdf(byte[] conteudo, String sha256) {}

    private static final String[] CLAUSULAS = {
            "A moto aquática me é entregue em perfeitas condições de funcionamento e conservação.",
            "Durante o período de utilização, sou responsável pela guarda, conservação e correta operação do equipamento.",
            "Qualquer dano causado por negligência, imprudência, imperícia, desrespeito às orientações recebidas ou descumprimento das normas de navegação será de minha inteira responsabilidade.",
            "Em caso de colisão, abalroamento, encalhe, choque contra embarcações, píeres, boias, pedras, estruturas flutuantes ou qualquer outro objeto, comprometo-me a arcar integralmente com os custos de reparo.",
            "Estou ciente de que o tombamento (virada) da moto aquática pode ocasionar entrada de água no motor e em seus componentes internos, gerando custos de manutenção e reparação.",
            "Em caso de virada da moto aquática por erro operacional ou desrespeito às orientações fornecidas, autorizo a cobrança dos custos de inspeção, drenagem, manutenção e reparação, os quais poderão variar entre R$ 400,00 e R$ 2.000,00.",
            "Caso os danos ultrapassem os valores acima mencionados, comprometo-me a ressarcir integralmente os prejuízos efetivamente apurados.",
            "Declaro que possuo condições físicas e psicológicas adequadas para operar a moto aquática e que não estou sob efeito de álcool, drogas ou qualquer substância que possa comprometer minha capacidade de condução.",
            "Comprometo-me a respeitar todas as orientações transmitidas pelo instrutor, as normas da Autoridade Marítima Brasileira e as regras de segurança aplicáveis à atividade."
    };

    private static final String[] REGRAS_5B = {
            "conduzirei a MA somente no interior da área delimitada à condução por locatários com CHA-MTA-E;",
            "conduzirei a MA somente no período entre o nascer e o pôr do sol;",
            "não utilizarei a MA para fim outro que não a recreação ou prática de esportes;",
            "não transferirei a MA a terceiros, sob qualquer pretexto;",
            "não transportarei passageiros;",
            "cumprirei as instruções sobre os procedimentos de segurança e orientações básicas fornecidas pelo EAMA;",
            "não ultrapassarei a velocidade de 37 km/h (vinte milhas náuticas/h ou vinte nós);",
            "não abastecerei a MA;",
            "jamais conduzirei a MA alugada após consumir bebidas alcoólicas ou qualquer substância entorpecente ou tóxica; e",
            "utilizarei, obrigatoriamente, lentes de correção visual e/ou aparelho de correção auditiva, na hipótese de restrição física."
    };

    private static final String ART_299 =
            "“Art. 299 - Omitir, em documento público ou particular, declaração que nele deveria constar, ou nele "
            + "inserir ou fazer inserir declaração falsa ou diversa da que deveria ser escrita, com o fim de prejudicar "
            + "direito, criar obrigação ou alterar a verdade sobre o fato juridicamente relevante.” “Pena: reclusão de "
            + "1 (um) a 5 (cinco) anos e multa, se o documento é público e reclusão de 1 (um) a 3 (três) anos, se o "
            + "documento é particular.”";

    // ------------------------------------------------------------------
    // PDF consolidado (F2.5)
    // ------------------------------------------------------------------

    public DocumentoPdf gerarDocumentoConsolidado(DadosDocumento d, byte[] assinaturaPng) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 56, 56, 54, 54);
        try {
            PdfWriter.getInstance(doc, baos);
            doc.open();
            Fonts f = new Fonts();

            boolean ema = "EMA".equalsIgnoreCase(d.via());
            boolean first = true;

            if (ema && d.incluirResidencia()) {
                first = section(doc, first);
                writeAnexo1C(doc, f, d, assinaturaPng);
            }
            if (ema) {
                first = section(doc, first);
                writeAnexo5C(doc, f, d, assinaturaPng);
                first = section(doc, first);
                writeAnexo5B1(doc, f, d);
                first = section(doc, first);
                writeAnexo5B2(doc, f, d, assinaturaPng);
            }
            first = section(doc, first);
            writeTermo(doc, f, d.razaoSocialLoja(), d.cnpjLoja(), d.nomeCliente(),
                    d.cpfCliente(), d.local(), d.dataCurta(), assinaturaPng);

            doc.close();
            byte[] bytes = baos.toByteArray();
            log.info("PDF consolidado gerado: {} bytes (via={})", bytes.length, d.via());
            return new DocumentoPdf(bytes, sha256Hex(bytes));
        } catch (DocumentException | IOException e) {
            throw new IllegalStateException("Falha ao gerar o PDF consolidado", e);
        }
    }

    /** Termo isolado (compat com o spike F0.1). */
    public DocumentoPdf gerarTermoResponsabilidade(DadosTermo d, byte[] assinaturaPng) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 56, 56, 54, 54);
        try {
            PdfWriter.getInstance(doc, baos);
            doc.open();
            writeTermo(doc, new Fonts(), d.razaoSocialLoja(), d.cnpjLoja(), d.nomeCliente(),
                    d.cpfCliente(), d.local(), d.data(), assinaturaPng);
            doc.close();
            byte[] bytes = baos.toByteArray();
            return new DocumentoPdf(bytes, sha256Hex(bytes));
        } catch (DocumentException | IOException e) {
            throw new IllegalStateException("Falha ao gerar o Termo de Responsabilidade em PDF", e);
        }
    }

    // ------------------------------------------------------------------
    // Seções
    // ------------------------------------------------------------------

    /**
     * Anexo 1-C — DECLARAÇÃO DE RESIDÊNCIA, em estilo formulário (campos
     * rotulados com linha), aproximando o layout oficial NORMAM-212/DPC.
     */
    private void writeAnexo1C(Document doc, Fonts f, DadosDocumento d, byte[] sig) throws DocumentException, IOException {
        // Cabeçalho oficial
        Paragraph norma = new Paragraph("NORMAM-212/DPC", f.sansHeader);
        norma.setAlignment(Element.ALIGN_RIGHT);
        doc.add(norma);
        Paragraph anexo = new Paragraph("ANEXO 1-C", f.sansTitle);
        anexo.setAlignment(Element.ALIGN_CENTER);
        anexo.setSpacingBefore(8f);
        doc.add(anexo);
        Paragraph tit = new Paragraph("DECLARAÇÃO DE RESIDÊNCIA", f.sansTitle);
        tit.setAlignment(Element.ALIGN_CENTER);
        tit.setSpacingAfter(18f);
        doc.add(tit);

        // Destinatário
        Paragraph dest = new Paragraph(
                "Sr. Capitão dos Portos/Delegado/Agente .............................................", f.sans);
        dest.setSpacingAfter(16f);
        doc.add(dest);

        // Campos (label + valor sobre linha)
        campos(doc, f, new String[]{"Eu"}, new String[]{nz(d.nomeCliente())}, new float[]{1f});
        campos(doc, f, new String[]{"CPF", "nacionalidade", "naturalidade"},
                new String[]{nz(d.cpfCliente()), nz(d.nacionalidade()), nz(d.naturalidade())},
                new float[]{1.1f, 1.2f, 1.4f});
        campos(doc, f, new String[]{"Telefone (DDD e nº)", "celular"},
                new String[]{nz(d.telefone()), nz(d.celular())}, new float[]{1.5f, 1f});
        campos(doc, f, new String[]{"e-mail"}, new String[]{nz(d.email())}, new float[]{1f});

        // Declaração
        Paragraph decl = new Paragraph("Na falta de documentos para comprovação de residência, em conformidade "
                + "com o disposto na Lei nº 7.115, de 29 de agosto de 1983, DECLARO para os devidos fins, sob as "
                + "penas da Lei, ser residente e domiciliado no endereço", f.sans);
        decl.setAlignment(Element.ALIGN_JUSTIFIED);
        decl.setSpacingBefore(12f);
        decl.setSpacingAfter(2f);
        doc.add(decl);
        campos(doc, f, new String[]{""}, new String[]{montaEndereco(d)}, new float[]{1f});

        Paragraph decl2 = new Paragraph("Declaro ainda, estar ciente de que a falsidade da presente declaração "
                + "pode implicar na sanção penal prevista no art. 299 do Código Penal, conforme transcrição abaixo:",
                f.sans);
        decl2.setAlignment(Element.ALIGN_JUSTIFIED);
        decl2.setSpacingBefore(12f);
        decl2.setSpacingAfter(10f);
        doc.add(decl2);

        Paragraph art = new Paragraph(ART_299, f.sansItalic);
        art.setAlignment(Element.ALIGN_JUSTIFIED);
        art.setSpacingAfter(24f);
        doc.add(art);

        // Data: (Cidade), dd/mm/aaaa — alinhada à direita (como o oficial)
        Paragraph data = new Paragraph(nz(d.local()) + ", " + nz(d.dataCurta()), f.sans);
        data.setAlignment(Element.ALIGN_RIGHT);
        data.setSpacingAfter(34f);
        doc.add(data);

        signatureLineRight(doc, f, "Assinatura do Requerente");

        Paragraph fp = new Paragraph("- 1-C-1 -", f.sansSmall);
        fp.setAlignment(Element.ALIGN_CENTER);
        fp.setSpacingBefore(18f);
        doc.add(fp);
    }

    /** Linha de campos rotulados, cada um sobre uma linha (borda inferior). */
    private void campos(Document doc, Fonts f, String[] labels, String[] valores, float[] widths)
            throws DocumentException {
        PdfPTable t = new PdfPTable(widths);
        t.setWidthPercentage(100);
        t.setSpacingAfter(5f);
        for (int i = 0; i < labels.length; i++) {
            Phrase ph = new Phrase();
            if (!labels[i].isEmpty()) {
                ph.add(new Chunk(labels[i] + "  ", f.sans));
            }
            ph.add(new Chunk(nz(valores[i]), f.sansBold));
            PdfPCell c = new PdfPCell(ph);
            c.setBorder(Rectangle.BOTTOM);
            c.setPaddingTop(8f);
            c.setPaddingBottom(2f);
            t.addCell(c);
        }
        doc.add(t);
    }

    private String montaEndereco(DadosDocumento d) {
        StringBuilder sb = new StringBuilder();
        if (d.endereco() != null) sb.append(d.endereco());
        if (d.cidadeUf() != null) sb.append(sb.length() > 0 ? ", " : "").append(d.cidadeUf());
        if (d.cep() != null) sb.append(sb.length() > 0 ? ", CEP " : "CEP ").append(d.cep());
        return sb.toString();
    }

    /** Linha de assinatura alinhada à direita (como nos formulários oficiais). */
    private void signatureLineRight(Document doc, Fonts f, String legenda) throws DocumentException {
        PdfPTable t = new PdfPTable(new float[]{1f, 1.3f});
        t.setWidthPercentage(100);
        PdfPCell vazio = new PdfPCell(new Phrase(" "));
        vazio.setBorder(Rectangle.NO_BORDER);
        t.addCell(vazio);
        PdfPCell c = new PdfPCell();
        c.setBorder(Rectangle.NO_BORDER);
        Paragraph line = new Paragraph("_______________________________", f.sans);
        line.setAlignment(Element.ALIGN_CENTER);
        Paragraph leg = new Paragraph(legenda, f.sansSmall);
        leg.setAlignment(Element.ALIGN_CENTER);
        c.addElement(line);
        c.addElement(leg);
        t.addCell(c);
        doc.add(t);
    }

    private void writeAnexo5C(Document doc, Fonts f, DadosDocumento d, byte[] sig) throws DocumentException, IOException {
        header(doc, f, "Anexo 5-C");
        title(doc, f, "AUTODECLARAÇÃO DE ATESTADO DE SAÚDE PARA EMISSÃO DE CHA-MTA-E");
        justified(doc, f, "Eu, " + b(d.nomeCliente()) + ", Identidade nº " + b(d.identidade())
                + ", CPF nº " + b(d.cpfCliente()) + ", declaro para fins específicos de emissão de Carteira de "
                + "Habilitação de Motonauta Especial (CHA-MTA-E) e condução de Moto Aquática alugada, que gozo de "
                + "boas condições de saúde física e mental, estando ciente de que eventual informação falsa poderá "
                + "ensejar responsabilidade nas esferas civil, administrativa e criminal, inclusive a caracterização "
                + "do crime de falsidade ideológica, nos termos do art. 299 do Decreto-Lei nº 2.848, de 7 de "
                + "dezembro de 1940 (Código Penal).");
        space(doc, 6);
        body(doc, f, "Faço uso de lentes de correção visual:  ( " + mark(d.usaLentes()) + " ) SIM   ( "
                + mark(!d.usaLentes()) + " ) NÃO");
        body(doc, f, "Faço uso de aparelho de correção auditiva:  ( " + mark(d.usaAparelho()) + " ) SIM   ( "
                + mark(!d.usaAparelho()) + " ) NÃO");
        space(doc, 8);
        body(doc, f, "Local e Data: " + nz(d.local()) + ", " + nz(d.dataCurta()));
        signature(doc, f, sig, "Nome e assinatura do declarante", d.nomeCliente());
        footer(doc, f, "- 5-C-1 -");
    }

    private void writeAnexo5B1(Document doc, Fonts f, DadosDocumento d) throws DocumentException, IOException {
        header(doc, f, "Anexo 5-B");
        title(doc, f, "ATESTADO DE DEMONSTRAÇÃO PARA CONDUÇÃO DE MOTO AQUÁTICA ALUGADA");
        justified(doc, f, "Atesto, para os devidos fins, que o(a) Sr.(a.) " + b(d.nomeCliente())
                + ", CPF nº " + b(d.cpfCliente()) + " assistiu à videoaula e recebeu a demonstração prática para "
                + "condução de moto aquática alugada junto ao " + b(d.razaoSocialLoja())
                + " (nome do EAMA), tendo o(a) Sr.(a.) " + b(d.instrutorNome()) + " como instrutor(a).");
        body(doc, f, "Identidade nº " + b(d.instrutorId()) + "  Órgão emissor " + b(d.instrutorOrgao())
                + "  CPF " + b(d.instrutorCpf()) + "  Nº da CHA " + b(d.instrutorCha()) + ".");
        signatureLine(doc, f, "Assinatura do Instrutor", d.instrutorNome());
        footer(doc, f, "- 5-B-1 -");
    }

    private void writeAnexo5B2(Document doc, Fonts f, DadosDocumento d, byte[] sig) throws DocumentException, IOException {
        header(doc, f, "Anexo 5-B");
        justified(doc, f, "Declaro, para os devidos fins, que compreendi os principais procedimentos de segurança e "
                + "orientações básicas, fornecidas pelo EAMA, por meio da videoaula produzida pela Marinha do Brasil "
                + "e a demonstração prática para condução de moto aquática alugada. Irei cumprir as regras "
                + "relacionadas abaixo:");
        com.lowagie.text.List regras = new com.lowagie.text.List(false, false);
        regras.setListSymbol(new com.lowagie.text.Chunk("", f.clause));
        // letras a)..j)
        char letra = 'a';
        for (String r : REGRAS_5B) {
            ListItem li = new ListItem(letra + ") " + r, f.clause);
            li.setSpacingAfter(2f);
            regras.add(li);
            letra++;
        }
        regras.setIndentationLeft(16f);
        doc.add(regras);
        space(doc, 4);
        body(doc, f, "( " + mark(d.semExperiencia()) + " ) Declaro que NÃO tenho experiência na condução de MA ou "
                + "embarcação miúda (demonstração com o locatário na garupa do instrutor).");
        body(doc, f, "( " + mark(!d.semExperiencia()) + " ) Declaro que TENHO experiência (apresentação da CHA "
                + "ARA/MSA/CPA/MTA-E).");
        justified(doc, f, "Estou ciente das imputações administrativas e penais decorrentes de acidentes em que esteja "
                + "envolvido e das sanções previstas na LESTA e RLESTA, bem como da sanção penal do art. 299 do "
                + "Código Penal.");
        space(doc, 6);
        body(doc, f, "Nome: " + b(d.nomeCliente()) + " (locatário)");
        body(doc, f, "Identidade nº " + b(d.identidade()) + "  Órgão Emissor " + b(d.orgaoEmissor())
                + "  CPF " + b(d.cpfCliente()));
        signature(doc, f, sig, "Assinatura do Locatário", d.nomeCliente());
        body(doc, f, "Observações: 1) não é válido para emissão de nova CHA-MTA-E; 2) validade de 30 dias a partir de "
                + nz(d.dataCurta()) + ".");
        footer(doc, f, "- 5-B-2 -");
    }

    private void writeTermo(Document doc, Fonts f, String razaoSocial, String cnpj, String nomeCliente,
                            String cpf, String local, String data, byte[] sig) throws DocumentException, IOException {
        Paragraph t = new Paragraph("TERMO DE RESPONSABILIDADE PELO USO DE MOTO AQUÁTICA (JET SKI)", f.title);
        t.setAlignment(Element.ALIGN_CENTER);
        doc.add(t);
        Paragraph sub = new Paragraph(nz(razaoSocial).toUpperCase(), f.subtitle);
        sub.setAlignment(Element.ALIGN_CENTER);
        doc.add(sub);
        Paragraph c = new Paragraph("CNPJ: " + nz(cnpj), f.small);
        c.setAlignment(Element.ALIGN_CENTER);
        c.setSpacingAfter(14f);
        doc.add(c);

        justified(doc, f, "Eu, " + b(nomeCliente) + ", portador(a) do CPF nº " + b(cpf)
                + ", declaro que recebi orientações de segurança e instruções de utilização da moto aquática "
                + "disponibilizada pela " + nz(razaoSocial)
                + ", assumindo total responsabilidade pelo equipamento durante o período de utilização. Declaro "
                + "estar ciente de que:");

        com.lowagie.text.List clausulas = new com.lowagie.text.List(com.lowagie.text.List.ORDERED);
        clausulas.setIndentationLeft(16f);
        for (String cl : CLAUSULAS) {
            ListItem item = new ListItem(cl, f.clause);
            item.setSpacingAfter(3f);
            clausulas.add(item);
        }
        doc.add(clausulas);

        body(doc, f, "Local: " + nz(local) + "        Data: " + nz(data));
        signature(doc, f, sig, "Nome do Cliente: " + nz(nomeCliente) + "  ·  CPF: " + nz(cpf), nomeCliente);
        Paragraph resp = new Paragraph("Responsável pela " + nz(razaoSocial)
                + ": ______________________________", f.small);
        resp.setSpacingBefore(18f);
        doc.add(resp);
    }

    // ------------------------------------------------------------------
    // Helpers de layout
    // ------------------------------------------------------------------

    private boolean section(Document doc, boolean first) {
        if (!first) {
            doc.newPage();
        }
        return false;
    }

    private void header(Document doc, Fonts f, String anexo) throws DocumentException {
        Paragraph p = new Paragraph(anexo + "                                            NORMAM-212/DPC", f.small);
        p.setAlignment(Element.ALIGN_RIGHT);
        p.setSpacingAfter(10f);
        doc.add(p);
    }

    private void title(Document doc, Fonts f, String text) throws DocumentException {
        Paragraph p = new Paragraph(text, f.title);
        p.setAlignment(Element.ALIGN_CENTER);
        p.setSpacingAfter(10f);
        doc.add(p);
    }

    private void justified(Document doc, Fonts f, String text) throws DocumentException {
        Paragraph p = new Paragraph(text, f.body);
        p.setAlignment(Element.ALIGN_JUSTIFIED);
        p.setSpacingAfter(7f);
        doc.add(p);
    }

    private void body(Document doc, Fonts f, String text) throws DocumentException {
        Paragraph p = new Paragraph(text, f.body);
        p.setSpacingAfter(5f);
        doc.add(p);
    }

    private void italic(Document doc, Fonts f, String text) throws DocumentException {
        Paragraph p = new Paragraph(text, f.italic);
        p.setAlignment(Element.ALIGN_JUSTIFIED);
        doc.add(p);
    }

    private void space(Document doc, float h) throws DocumentException {
        Paragraph p = new Paragraph(" ");
        p.setSpacingBefore(h);
        doc.add(p);
    }

    private void signature(Document doc, Fonts f, byte[] assinaturaPng, String legenda, String nome)
            throws DocumentException, IOException {
        if (assinaturaPng != null && assinaturaPng.length > 0) {
            Image img = Image.getInstance(assinaturaPng);
            img.scaleToFit(200f, 70f);
            img.setAlignment(Element.ALIGN_CENTER);
            img.setSpacingBefore(20f);
            doc.add(img);
        } else {
            space(doc, 36);
        }
        Paragraph p = new Paragraph(legenda, f.small);
        p.setAlignment(Element.ALIGN_CENTER);
        doc.add(p);
    }

    private void signatureLine(Document doc, Fonts f, String legenda, String nome) throws DocumentException {
        Paragraph p = new Paragraph("\n" + nz(nome) + "\n______________________________\n" + legenda, f.small);
        p.setAlignment(Element.ALIGN_CENTER);
        p.setSpacingBefore(20f);
        doc.add(p);
    }

    private void footer(Document doc, Fonts f, String code) throws DocumentException {
        Paragraph p = new Paragraph(code, f.small);
        p.setAlignment(Element.ALIGN_CENTER);
        p.setSpacingBefore(16f);
        doc.add(p);
    }

    private static String b(String v) {
        return (v == null || v.isBlank()) ? "____________" : v;
    }

    private static String nz(String v) {
        return v == null ? "" : v;
    }

    private static String mark(boolean v) {
        return v ? "X" : " ";
    }

    /** Conjunto de fontes (Times, WinAnsi para acentos PT). */
    private static final class Fonts {
        final Font title = font(FontFactory.TIMES_BOLD, 12);
        final Font subtitle = font(FontFactory.TIMES_BOLD, 10);
        final Font small = font(FontFactory.TIMES_ROMAN, 9);
        final Font body = font(FontFactory.TIMES_ROMAN, 11);
        final Font clause = font(FontFactory.TIMES_ROMAN, 10);
        final Font italic = FontFactory.getFont(FontFactory.TIMES_ITALIC, BaseFont.CP1252, false, 9);
        // Sans-serif (Helvetica) — aproxima o visual dos formulários oficiais
        final Font sansHeader = font(FontFactory.HELVETICA_BOLD, 10);
        final Font sansTitle = font(FontFactory.HELVETICA_BOLD, 11);
        final Font sans = font(FontFactory.HELVETICA, 10);
        final Font sansBold = font(FontFactory.HELVETICA_BOLD, 10);
        final Font sansItalic = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, BaseFont.CP1252, false, 9.5f);
        final Font sansSmall = font(FontFactory.HELVETICA, 9);
    }

    private static Font font(String name, float size) {
        return FontFactory.getFont(name, BaseFont.CP1252, false, size);
    }

    private static String sha256Hex(byte[] data) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte x : hash) {
                sb.append(Character.forDigit((x >> 4) & 0xF, 16));
                sb.append(Character.forDigit(x & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }
}
