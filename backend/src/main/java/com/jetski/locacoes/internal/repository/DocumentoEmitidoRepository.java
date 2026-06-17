package com.jetski.locacoes.internal.repository;

import com.jetski.locacoes.domain.DocumentoEmitido;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentoEmitidoRepository extends JpaRepository<DocumentoEmitido, UUID> {

    List<DocumentoEmitido> findByReservaIdOrderByEmitidoEmDesc(UUID reservaId);
}
