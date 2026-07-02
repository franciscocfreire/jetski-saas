package com.jetski.creditos.domain;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CreditoCompraRepository extends JpaRepository<CreditoCompra, UUID> {

    List<CreditoCompra> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

    List<CreditoCompra> findByTenantIdAndStatusOrderByCreatedAtAsc(UUID tenantId, StatusCompra status);

    Optional<CreditoCompra> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByTenantIdAndPixTxid(UUID tenantId, String pixTxid);
}
