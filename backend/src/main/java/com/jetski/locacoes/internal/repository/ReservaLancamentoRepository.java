package com.jetski.locacoes.internal.repository;

import com.jetski.locacoes.domain.ReservaLancamento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Repository: lançamentos financeiros da reserva (folio).
 * RLS filtra por tenant; leitura ordenada = extrato da reserva.
 */
public interface ReservaLancamentoRepository extends JpaRepository<ReservaLancamento, UUID> {

    List<ReservaLancamento> findByReservaIdOrderByCreatedAtAsc(UUID reservaId);
}
