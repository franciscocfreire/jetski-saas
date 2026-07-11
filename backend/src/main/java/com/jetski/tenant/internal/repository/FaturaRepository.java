package com.jetski.tenant.internal.repository;

import com.jetski.tenant.domain.Fatura;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Repositório de faturas (RLS por tenant; lookups sempre tenant-scoped). */
@Repository
public interface FaturaRepository extends JpaRepository<Fatura, UUID> {

    List<Fatura> findByTenantIdOrderByCompetenciaDesc(UUID tenantId);

    Optional<Fatura> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByTenantIdAndCompetencia(UUID tenantId, LocalDate competencia);

    List<Fatura> findByTenantIdAndStatus(UUID tenantId, Fatura.Status status);
}
