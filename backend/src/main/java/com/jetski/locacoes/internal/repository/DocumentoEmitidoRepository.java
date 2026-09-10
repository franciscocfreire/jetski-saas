package com.jetski.locacoes.internal.repository;

import com.jetski.locacoes.domain.DocumentoEmitido;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentoEmitidoRepository extends JpaRepository<DocumentoEmitido, UUID> {

    List<DocumentoEmitido> findByReservaIdOrderByEmitidoEmDesc(UUID reservaId);

    /**
     * Lookup explicitamente tenant-scoped (regra 1 do projeto: não confiar só na RLS)
     * — usado pela idempotência da emissão, que decide entre reaproveitar e reemitir.
     */
    List<DocumentoEmitido> findByTenantIdAndReservaIdOrderByEmitidoEmDesc(UUID tenantId, UUID reservaId);

    List<DocumentoEmitido> findByReservaIdInOrderByEmitidoEmDesc(List<UUID> reservaIds);

    List<DocumentoEmitido> findTop200ByOrderByEmitidoEmDesc();
}
