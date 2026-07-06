package com.jetski.locacoes.api;

import com.jetski.locacoes.domain.ReservaLancamento;
import com.jetski.locacoes.internal.repository.ReservaLancamentoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Public Query Service do folio financeiro (reserva/locação).
 *
 * <p>Exposto a outros módulos (ex.: fechamento) sem vazar repositórios
 * internos — mesmo padrão de {@link LocacaoQueryService}.
 *
 * @since fase 3 do folio
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FolioQueryService {

    private final ReservaLancamentoRepository reservaLancamentoRepository;

    /** Recebido líquido (PAGAMENTO − ESTORNO) de uma forma no período. */
    public record TotalPorForma(ReservaLancamento.Forma forma, BigDecimal valor) {}

    /**
     * Totais recebidos por forma de pagamento no período — regime de CAIXA
     * (data do lançamento), não de competência. A janela deve ser construída
     * no fuso do tenant pelo chamador.
     */
    public List<TotalPorForma> totalRecebidoPorFormaNoDia(UUID tenantId, Instant inicio, Instant fim) {
        return reservaLancamentoRepository.sumPorFormaNoPeriodo(tenantId, inicio, fim).stream()
            .map(row -> new TotalPorForma(
                ReservaLancamento.Forma.valueOf((String) row[0]),
                (BigDecimal) row[1]))
            .toList();
    }
}
