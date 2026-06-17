package com.jetski.locacoes.internal.repository;

import com.jetski.locacoes.domain.ClienteClaimToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClienteClaimTokenRepository extends JpaRepository<ClienteClaimToken, UUID> {

    /** Lookup pelo token (segredo único global) — usado na validação pública. */
    Optional<ClienteClaimToken> findByToken(String token);

    /** Tokens ativos de um cliente (para invalidar no reenvio). */
    List<ClienteClaimToken> findByClienteIdAndAtivoTrue(UUID clienteId);
}
