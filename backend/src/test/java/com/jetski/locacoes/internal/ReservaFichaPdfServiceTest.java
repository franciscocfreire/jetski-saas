package com.jetski.locacoes.internal;

import com.jetski.locacoes.api.dto.FolioExtratoResponse;
import com.jetski.locacoes.api.dto.ReservaFichaResponse;
import com.jetski.locacoes.api.dto.ReservaResponse;
import com.jetski.locacoes.domain.Reserva;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ficha da reserva em PDF: gera bytes válidos com dados completos, com
 * seções ausentes (null-safe) e sem logo (fallback identidade padrão).
 */
@DisplayName("ReservaFichaPdfService")
class ReservaFichaPdfServiceTest {

    private final ReservaFichaPdfService service = new ReservaFichaPdfService();

    private ReservaFichaResponse fichaCompleta() {
        ReservaResponse reserva = ReservaResponse.builder()
            .id(UUID.randomUUID())
            .status(Reserva.ReservaStatus.CONFIRMADA)
            .canal("BALCAO")
            .dataInicio(LocalDateTime.now().plusDays(1))
            .dataFimPrevista(LocalDateTime.now().plusDays(1).plusHours(2))
            .valorTotal(new BigDecimal("240.00"))
            .createdAt(Instant.now())
            .build();
        return new ReservaFichaResponse(
            reserva,
            new ReservaFichaResponse.ClienteResumo(UUID.randomUUID(), "Cliente Teste",
                "***.***.333-44", "c@t.com", "48999990000", null),
            new ReservaFichaResponse.PasseioResumo(UUID.randomUUID(), "GTI 130", null, null),
            new FolioExtratoResponse(
                List.of(new FolioExtratoResponse.FolioLancamentoResponse(
                    UUID.randomUUID(), "PAGAMENTO", "PIX", new BigDecimal("240.00"),
                    "integral", null, Instant.now())),
                new BigDecimal("240.00"), new BigDecimal("240.00"), BigDecimal.ZERO, BigDecimal.ZERO),
            null,
            null,
            new ReservaFichaResponse.CicloGru("60893100243850001", new BigDecimal("60.32"),
                Instant.now(), true, Instant.now(), Instant.now(), null, null),
            List.of());
    }

    @Test
    @DisplayName("dados completos sem logo → PDF válido (fallback identidade padrão)")
    void geraPdfSemLogo() {
        byte[] pdf = service.gerar(ReservaFichaPdfService.DadosFicha.builder()
            .lojaNome("ACME Jet Ski Ltda").lojaCnpj("11.222.333/0001-44").lojaCidade("Floripa/SC")
            .ficha(fichaCompleta())
            .geradoPor("operador-x").geradoEm(Instant.now())
            .build());

        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
        assertThat(pdf.length).isGreaterThan(1000);
    }

    @Test
    @DisplayName("seções ausentes (extrato vazio, sem habilitação/aceite/ciclo) não explodem")
    void geraPdfMinimo() {
        ReservaFichaResponse minima = new ReservaFichaResponse(
            ReservaResponse.builder().id(UUID.randomUUID()).build(),
            null, null, null, null, null, null, List.of());

        byte[] pdf = service.gerar(ReservaFichaPdfService.DadosFicha.builder()
            .lojaNome(null).ficha(minima).geradoEm(Instant.now())
            .build());

        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
    }
}
