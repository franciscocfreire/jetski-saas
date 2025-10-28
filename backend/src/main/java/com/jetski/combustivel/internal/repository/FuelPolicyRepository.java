package com.jetski.combustivel.internal.repository;

import com.jetski.combustivel.domain.FuelPolicy;
import com.jetski.combustivel.domain.FuelPolicyType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FuelPolicyRepository extends JpaRepository<FuelPolicy, Long> {

    Optional<FuelPolicy> findByTenantIdAndAplicavelAAndReferenciaIdAndAtivoTrue(
        UUID tenantId,
        FuelPolicyType aplicavelA,
        UUID referenciaId
    );

    Optional<FuelPolicy> findByTenantIdAndAplicavelAAndAtivoTrue(UUID tenantId, FuelPolicyType aplicavelA);

    List<FuelPolicy> findByTenantIdAndAtivoTrueOrderByPrioridadeDescCreatedAtDesc(UUID tenantId);

    List<FuelPolicy> findByTenantIdOrderByAtivoDescPrioridadeDescCreatedAtDesc(UUID tenantId);

    @Query("SELECT p FROM FuelPolicy p WHERE p.tenantId = :tenantId " +
           "AND p.aplicavelA = :aplicavelA " +
           "AND (:referenciaId IS NULL OR p.referenciaId = :referenciaId) " +
           "AND p.ativo = true " +
           "ORDER BY p.prioridade DESC, p.createdAt DESC")
    Optional<FuelPolicy> findFirstActivePolicy(
        @Param("tenantId") UUID tenantId,
        @Param("aplicavelA") FuelPolicyType aplicavelA,
        @Param("referenciaId") UUID referenciaId
    );
}
