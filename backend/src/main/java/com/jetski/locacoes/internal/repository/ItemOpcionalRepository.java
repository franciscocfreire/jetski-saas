package com.jetski.locacoes.internal.repository;

import com.jetski.locacoes.domain.ItemOpcional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository: ItemOpcionalRepository
 *
 * Handles database operations for optional add-on items (ItemOpcional).
 * RLS (Row Level Security) automatically filters queries by tenant_id.
 *
 * @author Jetski Team
 * @since 0.8.0
 */
@Repository
public interface ItemOpcionalRepository extends JpaRepository<ItemOpcional, UUID> {

    /**
     * Find all active optional items for current tenant.
     * RLS policy automatically filters by tenant_id.
     *
     * @return List of active ItemOpcional records
     */
    @Query("""
        SELECT i FROM ItemOpcional i
        WHERE i.ativo = true
        ORDER BY i.nome ASC
    """)
    List<ItemOpcional> findAllActive();

    /**
     * Find all optional items for current tenant (including inactive).
     * RLS policy automatically filters by tenant_id.
     *
     * @return List of all ItemOpcional records
     */
    @Query("""
        SELECT i FROM ItemOpcional i
        ORDER BY i.nome ASC
    """)
    List<ItemOpcional> findAllByTenant();

    /**
     * Find optional item by ID within current tenant.
     * RLS ensures tenant isolation.
     *
     * @param id ItemOpcional UUID
     * @return Optional containing ItemOpcional if found
     */
    Optional<ItemOpcional> findById(UUID id);

    /**
     * Find optional item by name within current tenant.
     *
     * @param nome Item name
     * @return Optional containing ItemOpcional if found
     */
    Optional<ItemOpcional> findByNome(String nome);

    /**
     * Check if optional item name already exists in current tenant.
     *
     * @param nome Item name
     * @return true if exists
     */
    boolean existsByNome(String nome);

    /**
     * Find optional item by tenant and ID (explicit tenant check).
     *
     * @param tenantId Tenant UUID
     * @param id Item UUID
     * @return Optional containing ItemOpcional if found
     */
    Optional<ItemOpcional> findByTenantIdAndId(UUID tenantId, UUID id);

    /**
     * Find all active items by tenant (explicit tenant check).
     *
     * @param tenantId Tenant UUID
     * @return List of active ItemOpcional records
     */
    List<ItemOpcional> findByTenantIdAndAtivoTrueOrderByNomeAsc(UUID tenantId);

    /**
     * Find all items by tenant (explicit tenant check).
     *
     * @param tenantId Tenant UUID
     * @return List of all ItemOpcional records
     */
    List<ItemOpcional> findByTenantIdOrderByNomeAsc(UUID tenantId);
}
