package com.jetski.locacoes.internal.repository;

import com.jetski.locacoes.domain.ReservaComprovante;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ReservaComprovanteRepository extends JpaRepository<ReservaComprovante, UUID> {

    List<ReservaComprovante> findByReservaIdAndAtivoTrueOrderByEnviadoEmDesc(UUID reservaId);
}
