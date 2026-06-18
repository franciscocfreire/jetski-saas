package com.jetski.locacoes.internal;

import com.jetski.locacoes.domain.Reserva;
import com.jetski.locacoes.domain.ReservaHabilitacao;
import com.jetski.locacoes.domain.ReservaHabilitacao.Via;
import com.jetski.locacoes.internal.repository.ReservaHabilitacaoRepository;
import com.jetski.locacoes.internal.repository.ReservaRepository;
import com.jetski.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Serviço de habilitação do condutor (CHA existente ou emissão CHA-MTA-E + GRU).
 * 1:1 com reserva (upsert). {@code resolvida} = CHA coletada OU GRU paga.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class HabilitacaoService {

    private final ReservaHabilitacaoRepository repository;
    private final ReservaRepository reservaRepository;

    @Transactional(readOnly = true)
    public Optional<ReservaHabilitacao> getByReserva(UUID reservaId) {
        return repository.findByReservaId(reservaId);
    }

    @Transactional
    public ReservaHabilitacao registrar(UUID reservaId, ReservaHabilitacao dados) {
        Reserva reserva = reservaRepository.findById(reservaId)
            .orElseThrow(() -> new NotFoundException("Reserva não encontrada: " + reservaId));

        ReservaHabilitacao h = repository.findByReservaId(reservaId)
            .orElseGet(ReservaHabilitacao::new);

        h.setTenantId(reserva.getTenantId());
        h.setReservaId(reservaId);
        h.setVia(dados.getVia());
        h.setChaCategoria(dados.getChaCategoria());
        h.setChaNumero(dados.getChaNumero());
        h.setChaValidade(dados.getChaValidade());
        if (dados.getVideoaulaEm() != null) {
            h.setVideoaulaEm(dados.getVideoaulaEm());
        }
        h.setAnexoSaude(Boolean.TRUE.equals(dados.getAnexoSaude()));
        h.setAnexoRegras(Boolean.TRUE.equals(dados.getAnexoRegras()));
        h.setAnexoResidencia(Boolean.TRUE.equals(dados.getAnexoResidencia()));
        h.setUsaLentes(Boolean.TRUE.equals(dados.getUsaLentes()));
        h.setUsaAparelho(Boolean.TRUE.equals(dados.getUsaAparelho()));
        h.setGruNumero(dados.getGruNumero());
        h.setGruValor(dados.getGruValor());

        boolean gruPago = Boolean.TRUE.equals(dados.getGruPago());
        h.setGruPago(gruPago);
        if (gruPago && h.getGruPagoEm() == null) {
            h.setGruPagoEm(Instant.now());
        } else if (!gruPago) {
            h.setGruPagoEm(null);
        }

        h.setResolvida(computeResolvida(h));

        ReservaHabilitacao saved = repository.save(h);
        log.info("Habilitação registrada: reservaId={}, via={}, resolvida={}",
                 reservaId, saved.getVia(), saved.getResolvida());
        return saved;
    }

    private boolean computeResolvida(ReservaHabilitacao h) {
        if (h.getVia() == Via.CHA) {
            return h.getChaNumero() != null && !h.getChaNumero().isBlank();
        }
        // EMA resolve quando a GRU está paga
        return Boolean.TRUE.equals(h.getGruPago());
    }
}
