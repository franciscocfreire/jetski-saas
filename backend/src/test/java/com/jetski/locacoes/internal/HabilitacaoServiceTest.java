package com.jetski.locacoes.internal;

import com.jetski.locacoes.domain.Reserva;
import com.jetski.locacoes.domain.ReservaHabilitacao;
import com.jetski.locacoes.domain.ReservaHabilitacao.Via;
import com.jetski.locacoes.internal.repository.ReservaHabilitacaoRepository;
import com.jetski.locacoes.internal.repository.ReservaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * F2.3 — lógica de resolução da habilitação (CHA resolve direto; EMA só com GRU paga).
 */
@DisplayName("HabilitacaoService (F2.3)")
class HabilitacaoServiceTest {

    private final ReservaHabilitacaoRepository repo = mock(ReservaHabilitacaoRepository.class);
    private final ReservaRepository reservaRepo = mock(ReservaRepository.class);
    private final HabilitacaoService service = new HabilitacaoService(repo,
        mock(CustomerHabilitacaoSyncService.class), reservaRepo,
        mock(com.jetski.shared.storage.StorageService.class),
        mock(DocumentoPdfService.class),
        mock(ClienteNotificacaoService.class),
        mock(org.springframework.context.ApplicationEventPublisher.class));

    private final UUID tenant = UUID.randomUUID();
    private final UUID reservaId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(reservaRepo.findById(reservaId))
            .thenReturn(Optional.of(Reserva.builder().id(reservaId).tenantId(tenant).build()));
        when(repo.findByReservaId(reservaId)).thenReturn(Optional.empty());
        when(repo.save(any(ReservaHabilitacao.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    @DisplayName("Via CHA com número → resolvida=true")
    void chaResolve() {
        ReservaHabilitacao dados = ReservaHabilitacao.builder()
            .via(Via.CHA).chaCategoria("Arrais Amador").chaNumero("1234567").build();

        ReservaHabilitacao h = service.registrar(reservaId, dados);

        assertThat(h.getVia()).isEqualTo(Via.CHA);
        assertThat(h.getResolvida()).isTrue();
        assertThat(h.getTenantId()).isEqualTo(tenant);
        assertThat(h.getReservaId()).isEqualTo(reservaId);
    }

    @Test
    @DisplayName("Via EMA sem GRU paga → resolvida=false")
    void emaSemGruNaoResolve() {
        ReservaHabilitacao dados = ReservaHabilitacao.builder()
            .via(Via.EMA).anexoSaude(true).anexoRegras(true).gruNumero("GRU-1").build();

        ReservaHabilitacao h = service.registrar(reservaId, dados);

        assertThat(h.getResolvida()).isFalse();
        assertThat(h.getGruPagoEm()).isNull();
    }

    @Test
    @DisplayName("Videoaula (V063): PLAYER grava carimbo+modo+idioma; DECLARACAO depois não rebaixa")
    void videoaulaPlayerNaoRebaixa() {
        java.time.Instant t1 = java.time.Instant.parse("2026-09-06T14:00:00Z");
        ReservaHabilitacao h = service.registrar(reservaId, ReservaHabilitacao.builder()
            .via(Via.EMA).videoaulaEm(t1)
            .videoaulaModo(ReservaHabilitacao.VideoaulaModo.PLAYER).videoaulaIdioma("en").build());
        assertThat(h.getVideoaulaEm()).isEqualTo(t1);
        assertThat(h.getVideoaulaModo()).isEqualTo(ReservaHabilitacao.VideoaulaModo.PLAYER);
        assertThat(h.getVideoaulaIdioma()).isEqualTo("en");

        // Termos legado reenvia videoaulaAssistida=true (sem modo) → nada muda.
        when(repo.findByReservaId(reservaId)).thenReturn(Optional.of(h));
        ReservaHabilitacao h2 = service.registrar(reservaId, ReservaHabilitacao.builder()
            .via(Via.EMA).videoaulaEm(java.time.Instant.parse("2026-09-06T15:00:00Z")).build());
        assertThat(h2.getVideoaulaEm()).isEqualTo(t1);
        assertThat(h2.getVideoaulaModo()).isEqualTo(ReservaHabilitacao.VideoaulaModo.PLAYER);
        assertThat(h2.getVideoaulaIdioma()).isEqualTo("en");

        // Sem videoaulaEm no request → também preserva.
        ReservaHabilitacao h3 = service.registrar(reservaId, ReservaHabilitacao.builder()
            .via(Via.EMA).anexoRegras(true).build());
        assertThat(h3.getVideoaulaEm()).isEqualTo(t1);
    }

    @Test
    @DisplayName("Videoaula (V063): declaração manual vira PLAYER quando o término é detectado (upgrade)")
    void videoaulaDeclaracaoUpgradeParaPlayer() {
        java.time.Instant t1 = java.time.Instant.parse("2026-09-06T14:00:00Z");
        ReservaHabilitacao h = service.registrar(reservaId, ReservaHabilitacao.builder()
            .via(Via.EMA).videoaulaEm(t1).build()); // modo ausente → DECLARACAO
        assertThat(h.getVideoaulaModo()).isEqualTo(ReservaHabilitacao.VideoaulaModo.DECLARACAO);

        when(repo.findByReservaId(reservaId)).thenReturn(Optional.of(h));
        java.time.Instant t2 = java.time.Instant.parse("2026-09-06T15:00:00Z");
        ReservaHabilitacao h2 = service.registrar(reservaId, ReservaHabilitacao.builder()
            .via(Via.EMA).videoaulaEm(t2)
            .videoaulaModo(ReservaHabilitacao.VideoaulaModo.PLAYER).videoaulaIdioma("pt").build());
        assertThat(h2.getVideoaulaEm()).isEqualTo(t2);
        assertThat(h2.getVideoaulaModo()).isEqualTo(ReservaHabilitacao.VideoaulaModo.PLAYER);
        assertThat(h2.getVideoaulaIdioma()).isEqualTo("pt");
    }

    @Test
    @DisplayName("Via EMA com GRU paga → resolvida=true + gruPagoEm")
    void emaComGruResolve() {
        ReservaHabilitacao dados = ReservaHabilitacao.builder()
            .via(Via.EMA).gruNumero("GRU-1").gruValor(new BigDecimal("23.13")).gruPago(true).build();

        ReservaHabilitacao h = service.registrar(reservaId, dados);

        assertThat(h.getResolvida()).isTrue();
        assertThat(h.getGruPago()).isTrue();
        assertThat(h.getGruPagoEm()).isNotNull();
    }
}
