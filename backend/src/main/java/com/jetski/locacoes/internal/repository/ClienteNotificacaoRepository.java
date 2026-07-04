package com.jetski.locacoes.internal.repository;

import com.jetski.locacoes.domain.ClienteNotificacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClienteNotificacaoRepository extends JpaRepository<ClienteNotificacao, UUID> {

    List<ClienteNotificacao> findTop50ByTenantIdAndClienteIdOrderByCreatedAtDesc(UUID tenantId, UUID clienteId);

    long countByTenantIdAndClienteIdAndLidaFalse(UUID tenantId, UUID clienteId);

    Optional<ClienteNotificacao> findByIdAndTenantIdAndClienteId(UUID id, UUID tenantId, UUID clienteId);

    List<ClienteNotificacao> findByTenantIdAndClienteIdAndLidaFalse(UUID tenantId, UUID clienteId);
}
