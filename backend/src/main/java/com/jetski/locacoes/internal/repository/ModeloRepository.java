package com.jetski.locacoes.internal.repository;

import com.jetski.locacoes.domain.Modelo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository: ModeloRepository
 *
 * Handles database operations for jetski models (Modelo).
 * RLS (Row Level Security) automatically filters queries by tenant_id.
 *
 * Core queries:
 * - List all active models for tenant
 * - Find model by ID within tenant
 * - Check model availability
 *
 * @author Jetski Team
 * @since 0.2.0
 */
@Repository
public interface ModeloRepository extends JpaRepository<Modelo, UUID> {

    /**
     * Find all active models for current tenant.
     * RLS policy automatically filters by tenant_id.
     *
     * @return List of active Modelo records
     */
    @Query("""
        SELECT m FROM Modelo m
        WHERE m.ativo = true
        ORDER BY m.nome ASC
    """)
    List<Modelo> findAllActive();

    /**
     * Find all models for current tenant (including inactive).
     * RLS policy automatically filters by tenant_id.
     *
     * @return List of all Modelo records
     */
    @Query("""
        SELECT m FROM Modelo m
        ORDER BY m.nome ASC
    """)
    List<Modelo> findAllByTenant();

    /**
     * Find model by ID within current tenant.
     * RLS ensures tenant isolation.
     *
     * @param id Modelo UUID
     * @return Optional containing Modelo if found
     */
    Optional<Modelo> findById(UUID id);

    /**
     * Find model by name within current tenant.
     * Business rule: Model names are unique per tenant.
     *
     * @param nome Model name
     * @return Optional containing Modelo if found
     */
    Optional<Modelo> findByNome(String nome);

    /**
     * Check if model name already exists in current tenant.
     *
     * @param nome Model name
     * @return true if exists
     */
    boolean existsByNome(String nome);

    /**
     * Count active models in current tenant.
     *
     * @return Count of active models
     */
    @Query("""
        SELECT COUNT(m) FROM Modelo m
        WHERE m.ativo = true
    """)
    long countActive();
}
