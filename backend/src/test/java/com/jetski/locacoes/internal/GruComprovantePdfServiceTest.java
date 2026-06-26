package com.jetski.locacoes.internal;

import com.jetski.locacoes.internal.gru.GruPagamentoStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class GruComprovantePdfServiceTest {

    @Test
    void geraPdfValidoComLogos() throws Exception {
        GruPagamentoStatus p = new GruPagamentoStatus(true, "CONCLUIDO",
            Instant.parse("2026-06-26T07:04:31Z"), "75sGYmTvk6i2EFqbnQGgSM",
            "80893100021762026", "10800 - INSCRIÇÃO EM CURSOS DO EPM", new BigDecimal("8.00"),
            "E18236120202606261004s0113c91494", "THALIA I G N", "23472084898", "Pix");

        byte[] pdf = new GruComprovantePdfService().gerar(p);

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 5)).startsWith("%PDF");
        // escreve p/ inspeção visual
        Files.write(Path.of("/tmp/comprovante-test.pdf"), pdf);
    }
}
