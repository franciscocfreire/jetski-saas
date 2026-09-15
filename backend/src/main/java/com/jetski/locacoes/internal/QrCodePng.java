package com.jetski.locacoes.internal;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;

/**
 * Gera o PNG de um QR Code a partir de um texto (ex.: BR Code do PIX copia-e-cola).
 * O PNG oficial do PagTesouro só vem na geração da GRU e não é persistido; o
 * copia-e-cola é o próprio payload do QR, então renderizar aqui dá o mesmo QR.
 */
final class QrCodePng {

    private QrCodePng() {
    }

    /** PNG do QR ou {@code null} se o conteúdo for vazio ou a geração falhar. */
    static byte[] gerar(String conteudo, int tamanho) {
        if (conteudo == null || conteudo.isBlank()) {
            return null;
        }
        try {
            BitMatrix m = new QRCodeWriter().encode(conteudo, BarcodeFormat.QR_CODE, tamanho, tamanho,
                Map.of(EncodeHintType.MARGIN, 1, EncodeHintType.CHARACTER_SET, "UTF-8"));
            BufferedImage img = new BufferedImage(m.getWidth(), m.getHeight(), BufferedImage.TYPE_INT_RGB);
            for (int x = 0; x < m.getWidth(); x++) {
                for (int y = 0; y < m.getHeight(); y++) {
                    img.setRGB(x, y, m.get(x, y) ? 0x000000 : 0xFFFFFF);
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (WriterException | IOException e) {
            return null;
        }
    }
}
