package com.jetski.creditos.internal;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Locale;

/**
 * Gera o payload PIX "copia e cola" (BR Code EMV-MPM, chave estática) com valor
 * exato — o mesmo texto é o conteúdo do QR Code. Sem gateway e sem rede: é só
 * montagem TLV + CRC16-CCITT, conforme o Manual de Padrões para Iniciação do
 * PIX (Bacen) / EMV QRCPS-MPM.
 */
public final class BrCodePix {

    private BrCodePix() {
    }

    /**
     * @param chave  chave PIX estática do recebedor
     * @param valor  valor exato da cobrança (2 casas)
     * @param nome   nome do recebedor (será normalizado; máx. 25 chars)
     * @param cidade cidade do recebedor (será normalizada; máx. 15 chars)
     */
    public static String gerar(String chave, BigDecimal valor, String nome, String cidade) {
        String merchantAccount = tlv("00", "BR.GOV.BCB.PIX") + tlv("01", chave);
        String payload =
              tlv("00", "01")                                   // payload format
            + tlv("26", merchantAccount)                        // merchant account info (PIX)
            + tlv("52", "0000")                                 // MCC
            + tlv("53", "986")                                  // moeda BRL
            + tlv("54", valor.setScale(2).toPlainString())      // valor exato
            + tlv("58", "BR")
            + tlv("59", ascii(nome, 25))
            + tlv("60", ascii(cidade, 15))
            + tlv("62", tlv("05", "***"))                       // txid livre (chave estática)
            + "6304";                                           // CRC vem ao final
        return payload + crc16(payload);
    }

    private static String tlv(String id, String value) {
        if (value.length() > 99) {
            throw new IllegalArgumentException("Campo EMV " + id + " excede 99 caracteres");
        }
        return id + String.format("%02d", value.length()) + value;
    }

    /** Remove acentos e caracteres fora do conjunto EMV; caixa alta; trunca. */
    private static String ascii(String s, int max) {
        String norm = Normalizer.normalize(s, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .replaceAll("[^A-Za-z0-9 .-]", "")
            .trim()
            .toUpperCase(Locale.ROOT);
        return norm.length() > max ? norm.substring(0, max).trim() : norm;
    }

    /** CRC16-CCITT-FALSE (poly 0x1021, init 0xFFFF), em hex maiúsculo. */
    private static String crc16(String data) {
        int crc = 0xFFFF;
        for (byte b : data.getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
            crc ^= (b & 0xFF) << 8;
            for (int i = 0; i < 8; i++) {
                crc = (crc & 0x8000) != 0 ? (crc << 1) ^ 0x1021 : crc << 1;
                crc &= 0xFFFF;
            }
        }
        return String.format("%04X", crc);
    }
}
