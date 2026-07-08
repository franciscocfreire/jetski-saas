package com.jetski.locacoes.internal.repository;

import com.jetski.locacoes.domain.ReservaHabilitacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReservaHabilitacaoRepository extends JpaRepository<ReservaHabilitacao, UUID> {

    Optional<ReservaHabilitacao> findByReservaId(UUID reservaId);

    /** Batch p/ a agenda (evita N+1 na prontidão). */
    java.util.List<ReservaHabilitacao> findByReservaIdIn(java.util.Collection<UUID> reservaIds);

    /** GRUs do tenant (via EMA com número), mais recente primeiro — módulo GRUs. */
    @org.springframework.data.jpa.repository.Query("""
        select h from ReservaHabilitacao h
         where h.via = com.jetski.locacoes.domain.ReservaHabilitacao.Via.EMA
           and h.gruNumero is not null
         order by coalesce(h.gruGeradaEm, h.createdAt) desc
        """)
    java.util.List<ReservaHabilitacao> listarGrus(org.springframework.data.domain.Pageable page);
}
