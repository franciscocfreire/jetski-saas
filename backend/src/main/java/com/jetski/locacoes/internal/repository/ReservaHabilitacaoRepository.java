package com.jetski.locacoes.internal.repository;

import com.jetski.locacoes.domain.ReservaHabilitacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReservaHabilitacaoRepository extends JpaRepository<ReservaHabilitacao, UUID> {

    Optional<ReservaHabilitacao> findByReservaId(UUID reservaId);
}
