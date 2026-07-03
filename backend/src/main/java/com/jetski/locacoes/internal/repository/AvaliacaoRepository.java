package com.jetski.locacoes.internal.repository;

import com.jetski.locacoes.domain.Avaliacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AvaliacaoRepository extends JpaRepository<Avaliacao, UUID> {

    Optional<Avaliacao> findByLocacaoId(UUID locacaoId);

    boolean existsByLocacaoId(UUID locacaoId);

    List<Avaliacao> findByClienteIdOrderByCreatedAtDesc(UUID clienteId);
}
