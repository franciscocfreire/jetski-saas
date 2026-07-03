package com.jetski.creditos.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BrCodePix (PIX copia-e-cola)")
class BrCodePixTest {

    @Test
    @DisplayName("Payload EMV com campos obrigatórios, valor exato e CRC válido")
    void payloadCompleto() {
        String p = BrCodePix.gerar("pix@meujet.com.br", new BigDecimal("250.00"), "Meu Jet", "Florianópolis");

        assertThat(p).startsWith("000201");                       // payload format
        assertThat(p).contains("0014BR.GOV.BCB.PIX");             // GUI do PIX
        assertThat(p).contains("0117pix@meujet.com.br");          // chave (17 chars)
        assertThat(p).contains("52040000");                       // MCC
        assertThat(p).contains("5303986");                        // BRL
        assertThat(p).contains("5406250.00");                     // valor exato
        assertThat(p).contains("5802BR");
        assertThat(p).contains("5907MEU JET");                    // nome normalizado
        assertThat(p).contains("6013FLORIANOPOLIS");              // cidade sem acento
        assertThat(p).contains("62070503***");                    // txid livre
        assertThat(p).matches(".*6304[0-9A-F]{4}$");              // CRC no fim

        // CRC recomputado bate com o emitido
        String semCrc = p.substring(0, p.length() - 4);
        int crc = 0xFFFF;
        for (byte b : semCrc.getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
            crc ^= (b & 0xFF) << 8;
            for (int i = 0; i < 8; i++) {
                crc = (crc & 0x8000) != 0 ? (crc << 1) ^ 0x1021 : crc << 1;
                crc &= 0xFFFF;
            }
        }
        assertThat(p).endsWith(String.format("%04X", crc));
    }

    @Test
    @DisplayName("Nome/cidade longos são truncados nos limites EMV (25/15)")
    void truncaLimites() {
        String p = BrCodePix.gerar("chave", new BigDecimal("1.00"),
            "Nome Extremamente Comprido Da Empresa Ltda", "Cidade Com Nome Muito Longo");
        assertThat(p).contains("5925NOME EXTREMAMENTE COMPRI");
        assertThat(p).contains("6015CIDADE COM NOME");
    }
}
