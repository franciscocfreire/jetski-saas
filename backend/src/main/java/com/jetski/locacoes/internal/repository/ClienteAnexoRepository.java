package com.jetski.locacoes.internal.repository;

import com.jetski.locacoes.domain.ClienteAnexo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Anexos do cliente. Toda consulta é tenant-scoped EXPLÍCITA — não confiar só na
 * RLS (testes rodam como superuser e o escopo de cliente tem self-read cross-tenant).
 */
@Repository
public interface ClienteAnexoRepository extends JpaRepository<ClienteAnexo, UUID> {

    List<ClienteAnexo> findByTenantIdAndClienteId(UUID tenantId, UUID clienteId);

    Optional<ClienteAnexo> findByTenantIdAndClienteIdAndTipo(UUID tenantId, UUID clienteId, ClienteAnexo.Tipo tipo);
}
