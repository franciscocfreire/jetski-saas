package com.jetski.locacoes.internal;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.ListItem;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Geração de documentos em PDF (OpenPDF) para o atendimento/emissão.
 *
 * <p><b>Spike F0.1:</b> implementa apenas o Termo de Responsabilidade da loja
 * (1 página), fiel ao layout do protótipo ({@code portal-cliente/app/staff/documento}).
 * Será expandido na F2.5 para o PDF consolidado (Anexos 1-C, 5-C, 5-B-1, 5-B-2 + Termo).
 *
 * <p>Retorna os bytes do PDF + o hash SHA-256 para arquivamento/integridade (F2.6).
 * Nota: para hash reprodutível entre execuções é necessário fixar CreationDate/ModDate/ID
 * do PDF — follow-up da F2.6 (aqui o hash cobre integridade do artefato gerado).
 */
@Service
@Slf4j
public class DocumentoPdfService {

    /** Dados mínimos para o Termo de Responsabilidade. */
    public record DadosTermo(
            String nomeCliente,
            String cpfCliente,
            String razaoSocialLoja,
            String cnpjLoja,
            String local,
            String data
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

    /**
     * Gera o Termo de Responsabilidade preenchido.
     *
     * @param d             dados do cliente/loja
     * @param assinaturaPng imagem PNG da assinatura (opcional; pode ser {@code null})
     */
    public DocumentoPdf gerarTermoResponsabilidade(DadosTermo d, byte[] assinaturaPng) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 56, 56, 56, 56);
        try {
            PdfWriter.getInstance(doc, baos);
            doc.open();

            Font titleFont = font(FontFactory.TIMES_BOLD, 13);
            Font subtitleFont = font(FontFactory.TIMES_BOLD, 10);
            Font smallFont = font(FontFactory.TIMES_ROMAN, 9);
            Font bodyFont = font(FontFactory.TIMES_ROMAN, 11);
            Font clauseFont = font(FontFactory.TIMES_ROMAN, 10);

            Paragraph title = new Paragraph(
                    "TERMO DE RESPONSABILIDADE PELO USO DE MOTO AQUÁTICA (JET SKI)", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            doc.add(title);

            Paragraph subtitle = new Paragraph(d.razaoSocialLoja().toUpperCase(), subtitleFont);
            subtitle.setAlignment(Element.ALIGN_CENTER);
            doc.add(subtitle);

            Paragraph cnpj = new Paragraph("CNPJ: " + d.cnpjLoja(), smallFont);
            cnpj.setAlignment(Element.ALIGN_CENTER);
            cnpj.setSpacingAfter(14f);
            doc.add(cnpj);

            Paragraph intro = new Paragraph(
                    "Eu, " + d.nomeCliente() + ", portador(a) do CPF nº " + d.cpfCliente()
                            + ", declaro que recebi orientações de segurança e instruções de utilização da moto "
                            + "aquática disponibilizada pela " + d.razaoSocialLoja()
                            + ", assumindo total responsabilidade pelo equipamento durante o período de utilização. "
                            + "Declaro estar ciente de que:", bodyFont);
            intro.setAlignment(Element.ALIGN_JUSTIFIED);
            intro.setSpacingAfter(8f);
            doc.add(intro);

            com.lowagie.text.List clausulas = new com.lowagie.text.List(com.lowagie.text.List.ORDERED);
            clausulas.setIndentationLeft(16f);
            for (String c : CLAUSULAS) {
                ListItem item = new ListItem(c, clauseFont);
                item.setSpacingAfter(3f);
                clausulas.add(item);
            }
            doc.add(clausulas);

            Paragraph localData = new Paragraph(
                    "Local: " + d.local() + "        Data: " + d.data(), bodyFont);
            localData.setSpacingBefore(14f);
            doc.add(localData);

            if (assinaturaPng != null && assinaturaPng.length > 0) {
                Image assinatura = Image.getInstance(assinaturaPng);
                assinatura.scaleToFit(200f, 70f);
                assinatura.setAlignment(Element.ALIGN_CENTER);
                assinatura.setSpacingBefore(24f);
                doc.add(assinatura);
            } else {
                Paragraph espaco = new Paragraph(" ", bodyFont);
                espaco.setSpacingBefore(40f);
                doc.add(espaco);
            }

            Paragraph nomeCpf = new Paragraph(
                    "Nome do Cliente: " + d.nomeCliente() + "   ·   CPF: " + d.cpfCliente(), smallFont);
            nomeCpf.setAlignment(Element.ALIGN_CENTER);
            doc.add(nomeCpf);

            Paragraph responsavel = new Paragraph(
                    "Responsável pela " + d.razaoSocialLoja() + ": ______________________________", smallFont);
            responsavel.setSpacingBefore(22f);
            doc.add(responsavel);

            doc.close();
            byte[] bytes = baos.toByteArray();
            log.debug("Termo PDF gerado: {} bytes", bytes.length);
            return new DocumentoPdf(bytes, sha256Hex(bytes));
        } catch (DocumentException | IOException e) {
            throw new IllegalStateException("Falha ao gerar o Termo de Responsabilidade em PDF", e);
        }
    }

    private static Font font(String name, float size) {
        return FontFactory.getFont(name, BaseFont.CP1252, false, size);
    }

    private static String sha256Hex(byte[] data) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }
}
