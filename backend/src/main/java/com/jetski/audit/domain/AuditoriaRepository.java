package com.jetski.shared.audit.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Repository for Auditoria entity.
 *
 * <p>Provides standard CRUD operations plus specialized queries
 * for audit trail analysis and compliance reporting.
 *
 * <p><strong>RLS Note:</strong> All queries are automatically filtered
 * by tenant_id via PostgreSQL Row Level Security.
 *
 * @author Jetski Team
 * @since 0.10.0
 */
@Repository
public interface AuditoriaRepository extends JpaRepository<Auditoria, UUID>, JpaSpecificationExecutor<Auditoria> {

    /**
     * Find all audit entries for a specific entity.
     * Useful for viewing history of a specific locacao, jetski, etc.
     */
    List<Auditoria> findByEntidadeAndEntidadeIdOrderByCreatedAtDesc(
            String entidade, UUID entidadeId);

    /**
     * Find all audit entries by entity type and tenant.
     */
    Page<Auditoria> findByTenantIdAndEntidadeOrderByCreatedAtDesc(
            UUID tenantId, String entidade, Pageable pageable);

    /**
     * Find all audit entries by user.
     * Useful for user activity reports.
     */
    Page<Auditoria> findByTenantIdAndUsuarioIdOrderByCreatedAtDesc(
            UUID tenantId, UUID usuarioId, Pageable pageable);

    /**
     * Find audit entries within a time range.
     * Useful for compliance reporting.
     */
    @Query("""
        SELECT a FROM Auditoria a
        WHERE a.tenantId = :tenantId
        AND a.createdAt BETWEEN :inicio AND :fim
        ORDER BY a.createdAt DESC
        """)
    Page<Auditoria> findByTenantIdAndPeriodo(
            @Param("tenantId") UUID tenantId,
            @Param("inicio") Instant inicio,
            @Param("fim") Instant fim,
            Pageable pageable);

    /**
     * Find audit entries by action type.
     * Useful for filtering check-ins, check-outs, etc.
     */
    Page<Auditoria> findByTenantIdAndAcaoOrderByCreatedAtDesc(
            UUID tenantId, String acao, Pageable pageable);

    /**
     * Find audit entries by trace ID.
     * Useful for correlating with distributed logs.
     */
    List<Auditoria> findByTraceId(String traceId);

    /**
     * Count audit entries by action type for a tenant.
     * Useful for dashboard metrics.
     */
    @Query("""
        SELECT a.acao, COUNT(a) FROM Auditoria a
        WHERE a.tenantId = :tenantId
        AND a.createdAt BETWEEN :inicio AND :fim
        GROUP BY a.acao
        """)
    List<Object[]> countByAcaoAndPeriodo(
            @Param("tenantId") UUID tenantId,
            @Param("inicio") Instant inicio,
            @Param("fim") Instant fim);

    /**
     * Find recent audit entries for a tenant.
     * Useful for activity feed.
     */
    List<Auditoria> findTop50ByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
