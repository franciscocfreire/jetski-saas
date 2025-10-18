package com.jetski.domain.repository;

import com.jetski.domain.entity.Membro;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository: MembroRepository
 *
 * Handles database operations for user-tenant memberships (Membro).
 *
 * Core queries:
 * - Validate if user has access to a specific tenant
 * - List all tenants a user belongs to
 * - Count total tenants per user
 *
 * @author Jetski Team
 * @since 0.2.0
 */
@Repository
public interface MembroRepository extends JpaRepository<Membro, Integer> {

    /**
     * Find active membership for user in tenant
     * Used by TenantAccessService to validate access
     *
     * @param usuarioId User UUID
     * @param tenantId Tenant UUID
     * @return Optional<Membro> if user is active member
     */
    @Query("""
        SELECT m FROM Membro m
        WHERE m.usuarioId = :usuarioId
          AND m.tenantId = :tenantId
          AND m.ativo = true
    """)
    Optional<Membro> findActiveByUsuarioAndTenant(
        @Param("usuarioId") UUID usuarioId,
        @Param("tenantId") UUID tenantId
    );

    /**
     * List all active tenant memberships for user
     * Returns max 100 results for UX (pagination if needed)
     *
     * @param usuarioId User UUID
     * @return List of active Membro records
     */
    @Query("""
        SELECT m FROM Membro m
        WHERE m.usuarioId = :usuarioId
          AND m.ativo = true
        ORDER BY m.createdAt DESC
    """)
    List<Membro> findAllActiveByUsuario(@Param("usuarioId") UUID usuarioId);

    /**
     * Count total active tenants for user
     * Used to determine if user has limited or massive access
     *
     * @param usuarioId User UUID
     * @return Count of active memberships
     */
    @Query("""
        SELECT COUNT(m) FROM Membro m
        WHERE m.usuarioId = :usuarioId
          AND m.ativo = true
    """)
    long countActiveByUsuario(@Param("usuarioId") UUID usuarioId);
}
