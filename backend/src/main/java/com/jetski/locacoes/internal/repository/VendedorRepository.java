package com.jetski.locacoes.internal.repository;

import com.jetski.locacoes.domain.Vendedor;
import com.jetski.locacoes.domain.VendedorTipo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository: VendedorRepository
 *
 * Handles database operations for sellers and partners (Vendedor).
 * RLS (Row Level Security) automatically filters queries by tenant_id.
 *
 * Core queries:
 * - List active sellers/partners
 * - Find sellers by type (INTERNO vs PARCEIRO)
 * - Commission calculation support
 *
 * @author Jetski Team
 * @since 0.2.0
 */
@Repository
public interface VendedorRepository extends JpaRepository<Vendedor, UUID> {

    /**
     * Find all active sellers for current tenant.
     * RLS policy automatically filters by tenant_id.
     *
     * @return List of active Vendedor records
     */
    @Query("""
        SELECT v FROM Vendedor v
        WHERE v.ativo = true
        ORDER BY v.nome ASC
    """)
    List<Vendedor> findAllActive();

    /**
     * Find all sellers by type (INTERNO or PARCEIRO).
     * Used for commission reports and partner management.
     *
     * @param tipo VendedorTipo enum
     * @return List of Vendedor records of specified type
     */
    @Query("""
        SELECT v FROM Vendedor v
        WHERE v.tipo = :tipo
          AND v.ativo = true
        ORDER BY v.nome ASC
    """)
    List<Vendedor> findAllByTipo(@Param("tipo") VendedorTipo tipo);

    /**
     * Find seller by documento (CPF/CNPJ) within current tenant.
     * Optional field, may not be unique.
     *
     * @param documento CPF or CNPJ
     * @return Optional containing Vendedor if found
     */
    Optional<Vendedor> findByDocumento(String documento);

    /**
     * Find seller by name within current tenant.
     *
     * @param nome Seller name
     * @return Optional containing Vendedor if found
     */
    Optional<Vendedor> findByNome(String nome);

    /**
     * Check if documento already exists in current tenant.
     *
     * @param documento CPF or CNPJ
     * @return true if exists
     */
    boolean existsByDocumento(String documento);

    /**
     * Count active sellers in current tenant.
     * Used for dashboard metrics.
     *
     * @return Count of active sellers
     */
    @Query("""
        SELECT COUNT(v) FROM Vendedor v
        WHERE v.ativo = true
    """)
    long countActive();

    /**
     * Count active sellers by type.
     * Used for reports and analytics.
     *
     * @param tipo VendedorTipo enum
     * @return Count of active sellers of specified type
     */
    @Query("""
        SELECT COUNT(v) FROM Vendedor v
        WHERE v.tipo = :tipo
          AND v.ativo = true
    """)
    long countActiveByTipo(@Param("tipo") VendedorTipo tipo);
}
