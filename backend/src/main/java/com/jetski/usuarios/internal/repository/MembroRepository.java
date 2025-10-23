package com.jetski.usuarios.internal.repository;

import com.jetski.usuarios.domain.Membro;
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

    /**
     * Check if email already exists as member in tenant.
     * Used for invitation validation.
     *
     * @param tenantId Tenant UUID
     * @param email User email
     * @return true if email is already member
     */
    @Query("""
        SELECT CASE WHEN COUNT(m) > 0 THEN true ELSE false END
        FROM Membro m
        JOIN Usuario u ON m.usuarioId = u.id
        WHERE m.tenantId = :tenantId AND u.email = :email
    """)
    boolean existsByTenantIdAndEmail(
        @Param("tenantId") UUID tenantId,
        @Param("email") String email
    );

    /**
     * Count active members in a tenant.
     * Used to validate plan limits.
     *
     * @param tenantId Tenant UUID
     * @param ativo Active status
     * @return Count of active members
     */
    long countByTenantIdAndAtivo(UUID tenantId, Boolean ativo);

    /**
     * Find member by tenant and user ID (regardless of active status).
     * Used for member management.
     *
     * @param tenantId Tenant UUID
     * @param usuarioId User UUID
     * @return Optional<Membro>
     */
    Optional<Membro> findByTenantIdAndUsuarioId(UUID tenantId, UUID usuarioId);

    /**
     * Count members with specific role in tenant.
     * Used to validate if we can deactivate the last admin.
     *
     * @param tenantId Tenant UUID
     * @param role Role to search in papeis array (e.g., "ADMIN_TENANT")
     * @param ativo Active status
     * @return Count of members with this role
     */
    @Query(value = """
        SELECT COUNT(*) FROM membro m
        WHERE m.tenant_id = :tenantId
          AND :role = ANY(m.papeis)
          AND m.ativo = :ativo
    """, nativeQuery = true)
    long countByTenantIdAndPapeisContainingAndAtivo(
        @Param("tenantId") UUID tenantId,
        @Param("role") String role,
        @Param("ativo") Boolean ativo
    );
}
