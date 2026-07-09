package com.jetski.locacoes.internal.repository;

import com.jetski.locacoes.domain.ReservaLancamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Repository: lançamentos do folio financeiro (reserva e/ou locação).
 * RLS filtra por tenant; leitura ordenada = extrato.
 */
public interface ReservaLancamentoRepository extends JpaRepository<ReservaLancamento, UUID> {

    List<ReservaLancamento> findByReservaIdOrderByCreatedAtAsc(UUID reservaId);

    List<ReservaLancamento> findByLocacaoIdOrderByCreatedAtAsc(UUID locacaoId);

    /** Lote (anti-N+1) — formas de pagamento por locação no Controle do Dia. */
    List<ReservaLancamento> findByLocacaoIdIn(Collection<UUID> locacaoIds);

    /** Relançamento de cobranças derivadas (edição de locação finalizada). */
    void deleteByLocacaoIdAndTipoIn(UUID locacaoId, Collection<ReservaLancamento.Tipo> tipos);

    /**
     * Recebido líquido por forma no período (PAGAMENTO − ESTORNO) — regime de
     * caixa, pela data do lançamento. Alimenta o fechamento diário.
     */
    @Query(value = """
        SELECT forma, SUM(CASE WHEN tipo = 'PAGAMENTO' THEN valor ELSE -valor END)
        FROM reserva_lancamento
        WHERE tenant_id = :tenantId
          AND tipo IN ('PAGAMENTO', 'ESTORNO')
          AND created_at >= :inicio AND created_at < :fim
        GROUP BY forma
        """, nativeQuery = true)
    List<Object[]> sumPorFormaNoPeriodo(@Param("tenantId") UUID tenantId,
                                        @Param("inicio") Instant inicio,
                                        @Param("fim") Instant fim);
}
