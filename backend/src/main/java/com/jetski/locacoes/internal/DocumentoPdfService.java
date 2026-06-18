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
            String instrutorDataEmissao, byte[] instrutorAssinatura,
            // GRU
            String gruNumero, String gruValor,
            // Locatário estrangeiro → inclui as versões em inglês do 5-B
            boolean incluirIngles
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
                // Versões em inglês (5-B-3 e 5-B-4) — apenas para locatário estrangeiro
                if (d.incluirIngles()) {
                    first = section(doc, first);
                    writeAnexo5B1En(doc, f, d);
                    first = section(doc, first);
                    writeAnexo5B2En(doc, f, d, assinaturaPng);
                }
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

        signatureLineRight(doc, f, sig, "Assinatura do Requerente");

        Paragraph fp = new Paragraph("- 1-C-1 -", f.sansSmall);
        fp.setAlignment(Element.ALIGN_CENTER);
        fp.setSpacingBefore(18f);
        doc.add(fp);
    }

    /** Cabeçalho oficial sans-serif: NORMAM (direita) + ANEXO + título (centro). */
    private void cabecalhoSans(Document doc, Fonts f, String anexo, String titulo) throws DocumentException {
        Paragraph norma = new Paragraph("NORMAM-212/DPC", f.sansHeader);
        norma.setAlignment(Element.ALIGN_RIGHT);
        doc.add(norma);
        Paragraph a = new Paragraph(anexo, f.sansTitle);
        a.setAlignment(Element.ALIGN_CENTER);
        a.setSpacingBefore(8f);
        doc.add(a);
        Paragraph t = new Paragraph(titulo, f.sansTitle);
        t.setAlignment(Element.ALIGN_CENTER);
        t.setSpacingAfter(16f);
        doc.add(t);
    }

    private void justifiedSans(Document doc, Fonts f, String text) throws DocumentException {
        Paragraph p = new Paragraph(text, f.sans);
        p.setAlignment(Element.ALIGN_JUSTIFIED);
        p.setSpacingAfter(8f);
        doc.add(p);
    }

    private void bodySans(Document doc, Fonts f, String text) throws DocumentException {
        Paragraph p = new Paragraph(text, f.sans);
        p.setSpacingAfter(5f);
        doc.add(p);
    }

    /** Assinatura (imagem se houver, senão espaço) + legenda sans, centralizada. */
    private void signatureSans(Document doc, Fonts f, byte[] assinaturaPng, String legenda)
            throws DocumentException, IOException {
        if (assinaturaPng != null && assinaturaPng.length > 0) {
            Image img = Image.getInstance(assinaturaPng);
            img.scaleToFit(200f, 70f);
            img.setAlignment(Element.ALIGN_CENTER);
            img.setSpacingBefore(18f);
            doc.add(img);
        } else {
            space(doc, 34);
        }
        Paragraph p = new Paragraph(legenda, f.sansSmall);
        p.setAlignment(Element.ALIGN_CENTER);
        doc.add(p);
    }

    private void footerSans(Document doc, Fonts f, String code) throws DocumentException {
        Paragraph p = new Paragraph(code, f.sansSmall);
        p.setAlignment(Element.ALIGN_CENTER);
        p.setSpacingBefore(18f);
        doc.add(p);
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

    /**
     * Bloco de assinatura alinhado à direita (como nos formulários oficiais).
     * Se {@code sig} for fornecido, embute a imagem da assinatura sobre a linha.
     */
    private void signatureLineRight(Document doc, Fonts f, byte[] sig, String legenda)
            throws DocumentException, IOException {
        PdfPTable t = new PdfPTable(new float[]{1f, 1.3f});
        t.setWidthPercentage(100);
        PdfPCell vazio = new PdfPCell(new Phrase(" "));
        vazio.setBorder(Rectangle.NO_BORDER);
        t.addCell(vazio);
        PdfPCell c = new PdfPCell();
        c.setBorder(Rectangle.NO_BORDER);
        c.setHorizontalAlignment(Element.ALIGN_CENTER);
        if (sig != null && sig.length > 0) {
            Image img = Image.getInstance(sig);
            img.scaleToFit(170f, 56f);
            img.setAlignment(Element.ALIGN_CENTER);
            c.addElement(img);
        }
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
        cabecalhoSans(doc, f, "ANEXO 5-C", "AUTODECLARAÇÃO DE ATESTADO DE SAÚDE PARA EMISSÃO DE CHA-MTA-E");
        campos(doc, f, new String[]{"Eu", "Identidade nº", "CPF nº"},
                new String[]{nz(d.nomeCliente()), nz(d.identidade()), nz(d.cpfCliente())},
                new float[]{1.6f, 1.1f, 1.1f});
        justifiedSans(doc, f, "declaro para fins específicos de emissão de Carteira de Habilitação de Motonauta "
                + "Especial (CHA-MTA-E) e condução de Moto Aquática alugada, que gozo de boas condições de saúde "
                + "física e mental, estando ciente de que eventual informação falsa poderá ensejar responsabilidade "
                + "nas esferas civil, administrativa e criminal, inclusive a caracterização do crime de falsidade "
                + "ideológica, nos termos do art. 299 do Decreto-Lei nº 2.848, de 7 de dezembro de 1940 (Código Penal).");
        space(doc, 8);
        // Tabela SIM / NÃO (formato oficial)
        PdfPTable sn = new PdfPTable(new float[]{4.2f, 1f, 1f});
        sn.setWidthPercentage(100);
        sn.addCell(simNaoCelula(f, "", true));
        sn.addCell(simNaoCelula(f, "SIM", true));
        sn.addCell(simNaoCelula(f, "NÃO", true));
        sn.addCell(simNaoCelula(f, "Faço uso de lentes de correção visual", false));
        sn.addCell(simNaoCelula(f, mark(d.usaLentes()), false));
        sn.addCell(simNaoCelula(f, mark(!d.usaLentes()), false));
        sn.addCell(simNaoCelula(f, "Faço uso de aparelho de correção auditiva", false));
        sn.addCell(simNaoCelula(f, mark(d.usaAparelho()), false));
        sn.addCell(simNaoCelula(f, mark(!d.usaAparelho()), false));
        sn.setSpacingAfter(26f);
        doc.add(sn);

        // Local e Data (esq.) + Nome e assinatura do declarante (dir.) lado a lado
        PdfPTable rod = new PdfPTable(new float[]{1f, 1f});
        rod.setWidthPercentage(100);
        rod.addCell(blocoAssinatura(f, null, "Local e Data", nz(d.local()) + ", " + nz(d.dataCurta())));
        rod.addCell(blocoAssinatura(f, sig, "Nome e assinatura do declarante", null));
        doc.add(rod);
        footerSans(doc, f, "- 5-C-1 -");
    }

    private PdfPCell simNaoCelula(Fonts f, String texto, boolean header) {
        PdfPCell c = new PdfPCell(new Phrase(texto, header ? f.sansBold : f.sans));
        c.setPadding(4f);
        if (!header && !texto.equals("X") && texto.isBlank()) c.setMinimumHeight(16f);
        c.setHorizontalAlignment(header || texto.length() <= 1
                ? Element.ALIGN_CENTER : Element.ALIGN_LEFT);
        return c;
    }

    /** Bloco "valor / linha / legenda" (assinatura opcional como imagem). */
    private PdfPCell blocoAssinatura(Fonts f, byte[] sig, String legenda, String valor) throws DocumentException, IOException {
        PdfPCell c = new PdfPCell();
        c.setBorder(Rectangle.NO_BORDER);
        c.setHorizontalAlignment(Element.ALIGN_CENTER);
        if (sig != null && sig.length > 0) {
            Image img = Image.getInstance(sig);
            img.scaleToFit(150f, 50f);
            img.setAlignment(Element.ALIGN_CENTER);
            c.addElement(img);
        } else if (valor != null) {
            Paragraph v = new Paragraph(valor, f.sans);
            v.setAlignment(Element.ALIGN_CENTER);
            c.addElement(v);
        } else {
            c.addElement(new Paragraph(" ", f.sans));
        }
        Paragraph line = new Paragraph("_____________________________", f.sans);
        line.setAlignment(Element.ALIGN_CENTER);
        Paragraph leg = new Paragraph(legenda, f.sansSmall);
        leg.setAlignment(Element.ALIGN_CENTER);
        c.addElement(line);
        c.addElement(leg);
        return c;
    }

    private void writeAnexo5B1(Document doc, Fonts f, DadosDocumento d) throws DocumentException, IOException {
        cabecalhoSans(doc, f, "ANEXO 5-B", "ATESTADO DE DEMONSTRAÇÃO PARA CONDUÇÃO DE MOTO AQUÁTICA ALUGADA");
        justifiedSans(doc, f, "O Atestado de Demonstração para Condução de Moto Aquática Alugada visa a atestar "
                + "que foi ministrada ao locatário uma familiarização mínima necessária a esse tipo de embarcação, "
                + "possibilitando a emissão de uma habilitação temporária (CHA-MTA-E), a qual permitirá a sua "
                + "condução dentro de uma área restrita.");

        Paragraph campoLabel = new Paragraph("Campo de preenchimento do EAMA:", f.sansBold);
        campoLabel.setSpacingBefore(4f);
        campoLabel.setSpacingAfter(8f);
        doc.add(campoLabel);

        justifiedSans(doc, f, "Atesto, para os devidos fins, que o(a) Sr.(a.) " + b(d.nomeCliente())
                + ", CPF nº " + b(d.cpfCliente()) + " assistiu a videoaula* e recebeu a demonstração prática** "
                + "para condução de moto aquática alugada junto ao " + b(d.razaoSocialLoja())
                + " (nome do EAMA), tendo o(a) Sr.(a.) " + b(d.instrutorNome()) + " como instrutor(a).");

        Paragraph dados = new Paragraph("(Dados do Instrutor)", f.sansSmall);
        dados.setAlignment(Element.ALIGN_CENTER);
        dados.setSpacingAfter(4f);
        doc.add(dados);

        campos(doc, f, new String[]{"Identidade nº", "Órgão emissor", "Data de emissão"},
                new String[]{nz(d.instrutorId()), nz(d.instrutorOrgao()), nz(d.instrutorDataEmissao())},
                new float[]{1.2f, 1.1f, 1.2f});
        campos(doc, f, new String[]{"CPF", "Nº da CHA"},
                new String[]{nz(d.instrutorCpf()), nz(d.instrutorCha())}, new float[]{1f, 1f});

        space(doc, 6);
        signatureLineRight(doc, f, d.instrutorAssinatura(), "Assinatura do Instrutor");

        space(doc, 8);
        notaSans(doc, f, "OBS: A apresentação de informações inverídicas poderá acarretar no cancelamento do EAMA, "
                + "sujeitando, ainda, o responsável do Estabelecimento de Aluguel de MA às sanções administrativas, "
                + "cíveis ou penais previstas em Lei.");
        notaSans(doc, f, "* A videoaula produzida pela Marinha do Brasil abordou os assuntos mais relevantes contidos "
                + "no RIPEAM, LESTA, RLESTA, NORMAM, NPCP/NPCF e procedimentos para saída/aproximação de praias e "
                + "margens, aplicados à condução da MA, levando em conta as especificidades locais da área.");
        notaSans(doc, f, "** A demonstração prática abordou as principais características e peculiaridades da MA, como "
                + "controle de propulsão e governo, a sua operação propriamente dita, bem como destacou os "
                + "procedimentos de segurança e orientações básicas, como:");
        notaSans(doc, f, "- área permitida à navegação (reconhecimento dos limites das áreas sinalizadas por boias);");
        notaSans(doc, f, "- cuidados na navegação;");
        notaSans(doc, f, "- precauções com banhistas e outras embarcações; e");
        notaSans(doc, f, "- uso apropriado do colete salva-vidas e do mecanismo de segurança da chave de ignição da "
                + "moto aquática.");
        footerSans(doc, f, "- 5-B-1 -");
    }

    private void notaSans(Document doc, Fonts f, String text) throws DocumentException {
        Paragraph p = new Paragraph(text, f.sansSmall);
        p.setAlignment(Element.ALIGN_JUSTIFIED);
        p.setSpacingAfter(3f);
        doc.add(p);
    }

    private static final String[] RULES_5B_EN = {
            "I will operate the PWC only within the area limited to CHA-MTA-E renters;",
            "I will operate the PWC only in the period between sunrise and sunset;",
            "I will not use the PWC for purposes other than recreation or sports;",
            "I will not transfer the PWC to third parties, under any pretext;",
            "I will not transport passengers;",
            "I will follow the instructions on safety procedures and basic guidelines provided by the EAMA "
                + "through the video lesson produced by the Brazilian Navy and the demonstration for operating a rented PWC;",
            "I will not exceed the speed of 37 km/h (20 nautical miles/h or 20 knots);",
            "I will not refuel the PWC;",
            "I will never operate the rented PWC after consuming alcoholic beverages, or any narcotic or toxic substances; and",
            "I will use, obligatorily, visual correction lenses and/or hearing aids, in the case of physical restriction."
    };

    private static final String ART_299_EN =
            "“Art. 299 - To omit, in a public or private document, a statement that should be included therein, or "
            + "to insert or cause to be inserted therein a statement that is false or different from the one that should "
            + "be written, in order to harm a right, create an obligation or alter the truth about the legally relevant "
            + "fact.” “Penalty: imprisonment from 1 (one) to 5 (five) years and fine, if the document is public; and "
            + "imprisonment from 1 (one) to 3 (three) years, if the document is private.”";

    /** Anexo 5-B-3 — English version of the demonstration certificate (5-B-1). */
    private void writeAnexo5B1En(Document doc, Fonts f, DadosDocumento d) throws DocumentException, IOException {
        cabecalhoSans(doc, f, "ANNEX 5-B", "CERTIFICATE OF DEMONSTRATION FOR OPERATING A RENTED PERSONAL WATERCRAFT");
        justifiedSans(doc, f, "The Certificate of Demonstration for Operating a Rented Personal Watercraft (PWC) is "
                + "intended to certify that the renter was given the minimum necessary familiarization with this type of "
                + "watercraft, allowing for the issuance of a temporary license (CHA-MTA-E), which will permit the PWC "
                + "operation within a restricted area.");
        Paragraph lbl = new Paragraph("To be completed by the rental company (EAMA):", f.sansBold);
        lbl.setSpacingAfter(8f);
        doc.add(lbl);
        justifiedSans(doc, f, "I certify, for all intents and purposes, that Mr(s). " + b(d.nomeCliente())
                + ", Passport/ID no " + b(d.cpfCliente()) + " attended the video lesson* and received the practical "
                + "demonstration** for operating a rented PWC by " + b(d.razaoSocialLoja()) + " (name of the EAMA), "
                + "having Mr.(s) " + b(d.instrutorNome()) + " as an instructor.");
        Paragraph info = new Paragraph("(Instructor's information)", f.sansSmall);
        info.setAlignment(Element.ALIGN_CENTER);
        info.setSpacingAfter(4f);
        doc.add(info);
        campos(doc, f, new String[]{"ID no", "Issued by", "Date of issue"},
                new String[]{nz(d.instrutorId()), nz(d.instrutorOrgao()), nz(d.instrutorDataEmissao())},
                new float[]{1.2f, 1.1f, 1.2f});
        campos(doc, f, new String[]{"CPF", "CHA no"},
                new String[]{nz(d.instrutorCpf()), nz(d.instrutorCha())}, new float[]{1f, 1f});
        space(doc, 6);
        signatureLineRight(doc, f, d.instrutorAssinatura(), "Instructor's signature");
        space(doc, 8);
        notaSans(doc, f, "NOTE: The submission of untrue information may result in the cancellation of the EAMA, also "
                + "subjecting the person responsible for the EAMA to administrative, civil, or criminal sanctions "
                + "provided for by law.");
        notaSans(doc, f, "* The video lesson produced by the Brazilian Navy addressed the most relevant issues contained "
                + "in COLREG, LESTA, RLESTA, NORMAM, NPCP/NPCF, and rules for leaving/approaching beaches and shores, "
                + "applied to PWC operation, taking into account the local area specifics.");
        notaSans(doc, f, "** The practical demonstration covered the main features and peculiarities of the PWC, such as "
                + "propulsion and steering control, and its operation itself. The demonstration highlighted safety "
                + "procedures and basic guidelines, such as:");
        notaSans(doc, f, "- area allowed for navigation (recognition of the limits of areas signaled by buoys);");
        notaSans(doc, f, "- navigating with caution;");
        notaSans(doc, f, "- precautions with bathers and other boats; and");
        notaSans(doc, f, "- proper use of the life jacket and PWC ignition key safety mechanism.");
        footerSans(doc, f, "- 5-B-3 -");
    }

    /** Anexo 5-B-4 — English version of the renter declaration (5-B-2). */
    private void writeAnexo5B2En(Document doc, Fonts f, DadosDocumento d, byte[] sig) throws DocumentException, IOException {
        cabecalhoSans(doc, f, "ANNEX 5-B", "RENTER'S DECLARATION");
        Paragraph lbl = new Paragraph("To be completed by the renter to be qualified as MTA-E:", f.sansBold);
        lbl.setSpacingAfter(8f);
        doc.add(lbl);
        justifiedSans(doc, f, "I declare, for all intents and purposes, that I have understood the main safety procedures "
                + "and basic guidelines, provided by the rental company (EAMA), through the video lesson produced by the "
                + "Brazilian Navy and the practical demonstration for operating a rented PWC. Furthermore, I am aware of "
                + "the administrative and criminal charges resulting from accidents in which I am involved, should I be "
                + "held liable.");
        bodySans(doc, f, "I will comply with the rules listed below:");
        com.lowagie.text.List rules = new com.lowagie.text.List(false, false);
        rules.setListSymbol(new com.lowagie.text.Chunk("", f.sans));
        char l = 'a';
        for (String r : RULES_5B_EN) {
            ListItem li = new ListItem(l + ") " + r, f.sans);
            li.setSpacingAfter(2f);
            rules.add(li);
            l++;
        }
        rules.setIndentationLeft(16f);
        doc.add(rules);
        space(doc, 4);
        bodySans(doc, f, "( " + mark(d.semExperiencia()) + " ) I declare that I have NO EXPERIENCE in operating a PWC or "
                + "small boat. (It is mandatory that the Instructor demonstrates the operation of the PWC while in motion "
                + "and the renter in the rear seat).");
        bodySans(doc, f, "( " + mark(!d.semExperiencia()) + " ) I declare that I have EXPERIENCE in operating a PWC or "
                + "small boat. (CHA ARA/MSA/CPA/MTA-E documentation is required).");
        space(doc, 4);
        bodySans(doc, f, "I am aware of:");
        bodySans(doc, f, "a) the administrative and criminal charges resulting from accidents in which I am involved, "
                + "should I be held liable; and");
        bodySans(doc, f, "b) the sanctions provided for in LESTA and RLESTA, when the acts prohibited in these legal "
                + "diplomas are committed.");
        justifiedSans(doc, f, "Finally, I also declare that I am aware that the falsity of this declaration by the person "
                + "responsible for the EAMA, by the Instructor, and by myself may incur in the criminal sanction provided "
                + "for in art. 299 of the Penal Code, as transcribed below:");
        Paragraph art = new Paragraph(ART_299_EN, f.sansItalic);
        art.setAlignment(Element.ALIGN_JUSTIFIED);
        art.setSpacingAfter(10f);
        doc.add(art);
        campos(doc, f, new String[]{"Renter's Name"}, new String[]{nz(d.nomeCliente())}, new float[]{1f});
        campos(doc, f, new String[]{"Passport/ID number", "Country of issue", "Date of issue"},
                new String[]{nz(d.identidade()), nz(d.nacionalidade()), ""}, new float[]{1.2f, 1.1f, 1.1f});
        signatureSans(doc, f, sig, "Renter's signature");
        space(doc, 6);
        notaSans(doc, f, "Notes: 1) This certificate is not valid for the issuance of a new CHA-MTA-E; and 2) It is valid "
                + "for 30 days from the date it was issued.");
        notaSans(doc, f, "Date of Issue (to be completed by the EAMA): " + nz(d.dataCurta()) + ".");
        footerSans(doc, f, "- 5-B-4 -");
    }

    private void writeAnexo5B2(Document doc, Fonts f, DadosDocumento d, byte[] sig) throws DocumentException, IOException {
        cabecalhoSans(doc, f, "ANEXO 5-B", "DECLARAÇÃO DO LOCATÁRIO");
        Paragraph campo = new Paragraph("Campo de preenchimento do locatário a habilitar-se como MTA-E", f.sansBold);
        campo.setSpacingAfter(8f);
        doc.add(campo);
        justifiedSans(doc, f, "Declaro, para os devidos fins, que compreendi os principais procedimentos de segurança e "
                + "orientações básicas, fornecidas pelo EAMA, por meio da videoaula produzida pela Marinha do Brasil "
                + "e a demonstração prática para condução de moto aquática alugada. Além disso, estou ciente das "
                + "imputações administrativas e penais decorrentes de acidentes em que esteja envolvido, caso seja "
                + "responsabilizado.");
        bodySans(doc, f, "Irei cumprir as regras relacionadas abaixo:");
        com.lowagie.text.List regras = new com.lowagie.text.List(false, false);
        regras.setListSymbol(new com.lowagie.text.Chunk("", f.sans));
        char letra = 'a';
        for (String r : REGRAS_5B) {
            ListItem li = new ListItem(letra + ") " + r, f.sans);
            li.setSpacingAfter(2f);
            regras.add(li);
            letra++;
        }
        regras.setIndentationLeft(16f);
        doc.add(regras);
        space(doc, 4);
        bodySans(doc, f, "( " + mark(d.semExperiencia()) + " ) Declaro que não tenho experiência na condução de MA ou "
                + "embarcação miúda. (É mandatória a demonstração com a MA alugada em deslocamento com o locatário "
                + "na garupa do Instrutor).");
        bodySans(doc, f, "( " + mark(!d.semExperiencia()) + " ) Declaro que tenho experiência na condução de MA ou "
                + "embarcação miúda. (É obrigatória a apresentação da CHA ARA/MSA/CPA/MTA-E).");
        space(doc, 4);
        bodySans(doc, f, "Estou ciente:");
        bodySans(doc, f, "a) das imputações administrativas e penais decorrentes de acidentes em que esteja envolvido, "
                + "caso seja responsabilizado; e");
        bodySans(doc, f, "b) das sanções previstas na LESTA e RLESTA, quando da prática das condutas vedadas nesses "
                + "diplomas legais.");
        justifiedSans(doc, f, "Por fim, declaro também estar ciente de que a falsidade da presente declaração por parte "
                + "do responsável pelo EAMA, pelo Instrutor e por mim pode implicar na sanção penal prevista no art. "
                + "299 do Código Penal, conforme transcrição abaixo:");
        Paragraph art = new Paragraph(ART_299, f.sansItalic);
        art.setAlignment(Element.ALIGN_JUSTIFIED);
        art.setSpacingAfter(10f);
        doc.add(art);

        campos(doc, f, new String[]{"Nome (locatário)"}, new String[]{nz(d.nomeCliente())}, new float[]{1f});
        campos(doc, f, new String[]{"Identidade nº", "Órgão Emissor"},
                new String[]{nz(d.identidade()), nz(d.orgaoEmissor())}, new float[]{1.2f, 1.2f});
        campos(doc, f, new String[]{"Data de Emissão", "CPF"},
                new String[]{"", nz(d.cpfCliente())}, new float[]{1.2f, 1.2f});
        signatureSans(doc, f, sig, "Assinatura do Locatário");
        space(doc, 6);
        notaSans(doc, f, "Observações: 1) O presente atestado não é válido para emissão de nova CHA-MTA-E; e "
                + "2) Tem validade de 30 dias, a partir da data em que foi emitido.");
        notaSans(doc, f, "Data de Emissão (a ser preenchida pelo EAMA): " + nz(d.dataCurta()) + ".");
        footerSans(doc, f, "- 5-B-2 -");
    }

    private void writeTermo(Document doc, Fonts f, String razaoSocial, String cnpj, String nomeCliente,
                            String cpf, String local, String data, byte[] sig) throws DocumentException, IOException {
        Paragraph t = new Paragraph("TERMO DE RESPONSABILIDADE PELO USO DE MOTO AQUÁTICA (JET SKI)", f.sansTitle);
        t.setAlignment(Element.ALIGN_CENTER);
        doc.add(t);
        Paragraph sub = new Paragraph(nz(razaoSocial).toUpperCase(), f.sansBold);
        sub.setAlignment(Element.ALIGN_CENTER);
        sub.setSpacingBefore(4f);
        doc.add(sub);
        Paragraph c = new Paragraph("CNPJ: " + nz(cnpj), f.sansSmall);
        c.setAlignment(Element.ALIGN_CENTER);
        c.setSpacingAfter(16f);
        doc.add(c);

        justifiedSans(doc, f, "Eu, " + b(nomeCliente) + ", portador(a) do CPF nº " + b(cpf)
                + ", declaro que recebi orientações de segurança e instruções de utilização da moto aquática "
                + "disponibilizada pela " + nz(razaoSocial)
                + ", assumindo total responsabilidade pelo equipamento durante o período de utilização. Declaro "
                + "estar ciente de que:");

        com.lowagie.text.List clausulas = new com.lowagie.text.List(com.lowagie.text.List.ORDERED);
        clausulas.setIndentationLeft(16f);
        for (String cl : CLAUSULAS) {
            ListItem item = new ListItem(cl, f.sans);
            item.setSpacingAfter(3f);
            clausulas.add(item);
        }
        doc.add(clausulas);

        Paragraph lcl = new Paragraph("Local: " + nz(local), f.sans);
        lcl.setSpacingBefore(8f);
        doc.add(lcl);
        bodySans(doc, f, "Data: " + nz(data));
        space(doc, 4);
        campos(doc, f, new String[]{"Nome do Cliente"}, new String[]{nz(nomeCliente)}, new float[]{1f});
        campos(doc, f, new String[]{"CPF"}, new String[]{nz(cpf)}, new float[]{1f});
        signatureSans(doc, f, sig, "Assinatura");
        Paragraph resp = new Paragraph("Responsável pela " + nz(razaoSocial)
                + ": ______________________________", f.sansSmall);
        resp.setSpacingBefore(20f);
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
