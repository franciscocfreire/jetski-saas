package com.jetski.creditos.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CreditoLancamentoRepository extends JpaRepository<CreditoLancamento, UUID> {

    @Query("SELECT COALESCE(SUM(c.quantidade), 0) FROM CreditoLancamento c WHERE c.tenantId = :tenantId")
    int saldo(@Param("tenantId") UUID tenantId);

    boolean existsByTenantIdAndTipo(UUID tenantId, TipoLancamento tipo);

    List<CreditoLancamento> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, org.springframework.data.domain.Pageable pageable);
}
