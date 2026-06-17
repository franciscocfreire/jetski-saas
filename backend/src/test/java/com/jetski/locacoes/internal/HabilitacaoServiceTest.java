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
    private final HabilitacaoService service = new HabilitacaoService(repo, reservaRepo);

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
