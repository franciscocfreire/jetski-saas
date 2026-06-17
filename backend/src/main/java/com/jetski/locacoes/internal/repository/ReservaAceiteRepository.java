package com.jetski.locacoes.internal.repository;

import com.jetski.locacoes.domain.ReservaAceite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReservaAceiteRepository extends JpaRepository<ReservaAceite, UUID> {

    /** Aceite mais recente da reserva (o "atual"). */
    Optional<ReservaAceite> findFirstByReservaIdOrderByAceitoEmDesc(UUID reservaId);
}
