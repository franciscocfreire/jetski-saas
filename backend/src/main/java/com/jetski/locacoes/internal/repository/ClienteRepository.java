package com.jetski.locacoes.internal.repository;

import com.jetski.locacoes.domain.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository: ClienteRepository
 *
 * Handles database operations for rental customers (Cliente).
 * RLS (Row Level Security) automatically filters queries by tenant_id.
 *
 * Core queries:
 * - List active customers
 * - Find customers by documento (CPF/CNPJ)
 * - Check if customer can rent (Business rule RF03.4)
 *
 * @author Jetski Team
 * @since 0.2.0
 */
@Repository
public interface ClienteRepository extends JpaRepository<Cliente, UUID> {

    /**
     * Find all active customers for current tenant.
     * RLS policy automatically filters by tenant_id.
     *
     * @return List of active Cliente records
     */
    @Query("""
        SELECT c FROM Cliente c
        WHERE c.ativo = true
        ORDER BY c.nome ASC
    """)
    List<Cliente> findAllActive();

    /**
     * Find customers who can rent (RF03.4).
     * Business rule: Must be active AND have accepted liability terms.
     *
     * @return List of customers eligible for rental
     */
    @Query("""
        SELECT c FROM Cliente c
        WHERE c.ativo = true
          AND c.termoAceite = true
        ORDER BY c.nome ASC
    """)
    List<Cliente> findAllEligibleForRental();

    /**
     * Find customer by documento (CPF/CNPJ) within current tenant.
     * Optional field, may not be unique.
     *
     * @param documento CPF or CNPJ
     * @return Optional containing Cliente if found
     */
    Optional<Cliente> findByDocumento(String documento);

    /**
     * Find customer by name within current tenant.
     *
     * @param nome Customer name
     * @return Optional containing Cliente if found
     */
    Optional<Cliente> findByNome(String nome);

    /**
     * Check if documento already exists in current tenant.
     *
     * @param documento CPF or CNPJ
     * @return true if exists
     */
    boolean existsByDocumento(String documento);

    /**
     * Count active customers in current tenant.
     * Used for dashboard metrics.
     *
     * @return Count of active customers
     */
    @Query("""
        SELECT COUNT(c) FROM Cliente c
        WHERE c.ativo = true
    """)
    long countActive();

    /**
     * Count customers who have accepted terms (RF03.4).
     * Used for compliance and reporting.
     *
     * @return Count of customers with accepted terms
     */
    @Query("""
        SELECT COUNT(c) FROM Cliente c
        WHERE c.ativo = true
          AND c.termoAceite = true
    """)
    long countWithAcceptedTerms();

    /**
     * Find customers by partial name match (case-insensitive).
     * Used for search functionality.
     *
     * @param nome Partial name to search
     * @return List of matching customers
     */
    @Query("""
        SELECT c FROM Cliente c
        WHERE c.ativo = true
          AND LOWER(c.nome) LIKE LOWER(CONCAT('%', :nome, '%'))
        ORDER BY c.nome ASC
    """)
    List<Cliente> searchByNome(@Param("nome") String nome);
}
